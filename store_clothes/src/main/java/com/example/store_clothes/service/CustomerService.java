package com.example.store_clothes.service;

import com.example.store_clothes.dto.request.CreateCustomerRequest;
import com.example.store_clothes.dto.request.UpdateCustomerRequest;
import com.example.store_clothes.dto.response.CustomerDetailResponse;
import com.example.store_clothes.dto.response.CustomerResponse;
import com.example.store_clothes.entity.Customer;
import com.example.store_clothes.exception.DomainException;
import com.example.store_clothes.exception.ErrorCode;
import com.example.store_clothes.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * CustomerService — Tầng xử lý nghiệp vụ Khách Hàng.
 *
 * ====================================================================
 * NGUYÊN TẮC THIẾT KẾ:
 * ====================================================================
 *
 * [1] @Transactional ĐẶT Ở SERVICE, KHÔNG đặt ở Controller (Quy tắc #1).
 *     - readOnly=true cho tất cả query → Hibernate skip dirty checking → tăng hiệu năng.
 *     - @Transactional (write) cho create/update/delete.
 *
 * [2] Soft Delete với phone rename (Quy tắc #3):
 *     @SQLDelete trên Customer entity tự xử lý SQL. Service chỉ cần gọi
 *     customerRepository.delete(customer) — Hibernate tự chạy custom SQL.
 *
 * [3] Optimistic Lock (Quy tắc #7):
 *     Hibernate tự xử lý @Version. Nếu xung đột → OptimisticLockException.
 *     GlobalExceptionHandler bắt và trả HTTP 409 Conflict.
 *
 * [4] AuditLog @Async + REQUIRES_NEW (Quy tắc #9):
 *     Ghi audit cho hành động xóa — chạy song song, không block nghiệp vụ chính.
 * ====================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AuditLogService auditLogService;

    // =========================================================================
    // CUS-01: TẠO KHÁCH HÀNG
    // =========================================================================

    /**
     * Tạo mới khách hàng.
     *
     * Luồng nghiệp vụ:
     * 1. Kiểm tra phone đã tồn tại ở KH đang active → throw CUSTOMER_PHONE_DUPLICATED.
     * 2. Kiểm tra phone đã từng tồn tại ở KH bị xóa mềm → throw CUSTOMER_PHONE_BELONGS_TO_DELETED
     *    (KHÔNG auto-restore — cần xác nhận từ OWNER/MANAGER).
     * 3. Tạo và lưu Customer mới.
     *
     * 💡 Senior Note — Tại sao KHÔNG auto-restore KH đã xóa khi trùng SĐT?
     * Auto-restore ẩn đi lịch sử xóa, có thể gây nhầm lẫn nghiệp vụ
     * (KH cũ có thể bị xóa vì lý do gian lận, tranh chấp). Cần xác nhận
     * có chủ đích từ OWNER/MANAGER để restore — đảm bảo kiểm soát chặt chẽ.
     *
     * @param request Dữ liệu tạo KH từ client
     * @return CustomerResponse sau khi tạo thành công
     */
    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        log.debug("Creating customer with phone={}", request.getPhone());

        // Bước 1: Kiểm tra SĐT trùng với KH đang active (chịu @SQLRestriction)
        if (customerRepository.existsByPhone(request.getPhone())) {
            throw new DomainException(ErrorCode.CUSTOMER_PHONE_DUPLICATED,
                Map.of("phone", request.getPhone()));
        }

        // Bước 2: Kiểm tra SĐT đã từng thuộc KH bị xóa mềm (bypass @SQLRestriction)
        // Native query tìm trong bảng customers kể cả is_deleted = true
        customerRepository.findDeletedCustomerByPhonePrefix(request.getPhone())
            .ifPresent(deletedCustomer -> {
                // Tìm thấy KH đã bị xóa mềm với SĐT này → thông báo rõ ràng
                throw new DomainException(ErrorCode.CUSTOMER_PHONE_BELONGS_TO_DELETED,
                    Map.of(
                        "phone", request.getPhone(),
                        "deletedCustomerName", deletedCustomer.getName()
                    ));
            });

        // Bước 3: Tạo mới KH
        Customer customer = Customer.builder()
            .name(request.getName())
            .phone(request.getPhone())
            .email(request.getEmail())
            .note(request.getNote())
            .loyaltyPoints(0)
            .totalSpent(BigDecimal.ZERO)
            .build();

        customer = customerRepository.save(customer);

        log.info("Customer created: id={}, name={}, phone={}", customer.getId(), customer.getName(), customer.getPhone());
        return mapToResponse(customer);
    }

    // =========================================================================
    // CUS-02: DANH SÁCH KHÁCH HÀNG
    // =========================================================================

    /**
     * Lấy danh sách khách hàng có phân trang, tìm kiếm và filter.
     *
     * 💡 Senior Note — readOnly = true:
     * Hibernate skip flush + dirty checking → tiết kiệm CPU và latency cho GET requests.
     * Luôn đặt readOnly=true cho tất cả @Transactional query-only methods.
     *
     * @param page             Số trang (0-indexed)
     * @param size             Số item mỗi trang
     * @param keyword          Từ khóa tìm kiếm (name hoặc phone), null = không filter
     * @param hasLoyaltyPoints true = chỉ lấy KH có điểm tích lũy > 0
     * @return Page<CustomerResponse>
     */
    @Transactional(readOnly = true)
    public Page<CustomerResponse> getCustomers(int page, int size, String keyword, boolean hasLoyaltyPoints) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Chuẩn hóa keyword: trim whitespace, null nếu rỗng
        String normalizedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        Page<Customer> customers;
        if (hasLoyaltyPoints) {
            customers = customerRepository.findByKeywordAndHasLoyaltyPoints(normalizedKeyword, pageable);
        } else {
            customers = customerRepository.findByKeyword(normalizedKeyword, pageable);
        }

        return customers.map(this::mapToResponse);
    }

    // =========================================================================
    // CUS-03: CHI TIẾT KHÁCH HÀNG + LỊCH SỬ MUA HÀNG
    // =========================================================================

    /**
     * Lấy chi tiết khách hàng kèm lịch sử mua hàng.
     *
     * Chiến lược 2 QUERY RIÊNG BIỆT:
     * 1. Query count + sum: Thống kê tổng số đơn và tổng tiền.
     * 2. Query 10 đơn gần nhất: Bounded list, không load toàn bộ.
     *
     * 💡 Senior Note — Tại sao KHÔNG dùng 1 JOIN FETCH lớn?
     * (1) Cartesian Product: KH có 500 đơn × 10 items/đơn = 5,000 rows từ DB.
     *     Network bandwidth tăng 500x so với cần thiết (chỉ 10 đơn).
     * (2) Hibernate pagination bug: HHH90003004 — khi dùng Pageable + JOIN FETCH
     *     collection, Hibernate load toàn bộ vào memory rồi mới paginate.
     *     → Memory leak với KH có hàng nghìn đơn hàng.
     * (3) Single Responsibility: Mỗi query làm 1 việc → dễ optimize/cache riêng.
     *
     * @param customerId ID khách hàng
     * @return CustomerDetailResponse kèm thống kê và lịch sử mua
     */
    @Transactional(readOnly = true)
    public CustomerDetailResponse getCustomerDetail(Long customerId) {
        // Load thông tin KH (chịu @SQLRestriction — chỉ tìm KH đang active)
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new DomainException(ErrorCode.CUSTOMER_NOT_FOUND,
                Map.of("id", customerId)));

        // =====================================================================
        // Query 1: Thống kê đơn hàng (COUNT + SUM trong 1 SELECT nhẹ)
        // Kết quả: Object[] { totalOrders: Long, totalAmount: BigDecimal }
        // =====================================================================
        Object[] stats = customerRepository.countAndSumOrdersByCustomerId(customerId);
        Long totalOrders = stats[0] != null ? ((Number) stats[0]).longValue() : 0L;
        BigDecimal totalAmountFromOrders = stats[1] != null ? (BigDecimal) stats[1] : BigDecimal.ZERO;

        // =====================================================================
        // Query 2: 10 đơn gần nhất (Projection query — chỉ 3 field cần thiết)
        // Kết quả: List<Object[]> { orderCode, totalAmount, createdAt }
        // =====================================================================
        List<Object[]> recentOrderData = customerRepository.findTop10RecentOrdersByCustomerId(customerId);
        List<CustomerDetailResponse.OrderSummary> recentOrders = recentOrderData.stream()
            .map(row -> CustomerDetailResponse.OrderSummary.builder()
                .orderCode((String) row[0])
                .finalAmount((BigDecimal) row[1])
                .createdAt((LocalDateTime) row[2])
                .build())
            .toList();

        return CustomerDetailResponse.builder()
            .id(customer.getId())
            .name(customer.getName())
            .phone(customer.getPhone())
            .email(customer.getEmail())
            .note(customer.getNote())
            .loyaltyPoints(customer.getLoyaltyPoints())
            .totalSpent(customer.getTotalSpent())
            .createdAt(customer.getCreatedAt())
            .updatedAt(customer.getUpdatedAt())
            // Từ Query 1
            .totalOrders(totalOrders)
            .totalAmountFromOrders(totalAmountFromOrders)
            // Từ Query 2
            .recentOrders(recentOrders)
            .build();
    }

    // =========================================================================
    // CUS-04: CẬP NHẬT KHÁCH HÀNG
    // =========================================================================

    /**
     * Cập nhật thông tin cơ bản của khách hàng.
     *
     * Nguyên tắc:
     * - Chỉ cho sửa name, phone, email — KHÔNG cho sửa loyaltyPoints, totalSpent.
     * - Validate phone unique với các KH KHÁC (loại trừ KH hiện tại).
     * - Optimistic Lock: Hibernate tự xử lý @Version. Nếu xung đột → OptimisticLockException → HTTP 409.
     *
     * 💡 Senior Note — Tại sao @Transactional không có readOnly?
     * Đây là write operation → cần readOnly = false (default) để Hibernate
     * cho phép flush entity changes xuống DB khi transaction commit.
     *
     * @param customerId ID khách hàng cần cập nhật
     * @param request    Dữ liệu cập nhật
     * @return CustomerResponse sau khi cập nhật
     */
    @Transactional
    public CustomerResponse updateCustomer(Long customerId, UpdateCustomerRequest request) {
        log.debug("Updating customer id={}, newPhone={}", customerId, request.getPhone());

        // Load KH (có @Version để Optimistic Lock hoạt động)
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new DomainException(ErrorCode.CUSTOMER_NOT_FOUND,
                Map.of("id", customerId)));

        // Validate phone unique với KH KHÁC (tự mình có thể giữ nguyên SĐT)
        if (customerRepository.existsByPhoneAndIdNot(request.getPhone(), customerId)) {
            throw new DomainException(ErrorCode.CUSTOMER_PHONE_DUPLICATED,
                Map.of("phone", request.getPhone()));
        }

        // Cập nhật các trường được phép
        customer.setName(request.getName());
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        // loyaltyPoints và totalSpent: KHÔNG cập nhật ở đây

        // Hibernate tự detect dirty fields và UPDATE khi transaction commit
        // @Version tự tăng 1 đơn vị — nếu version mismatch → OptimisticLockException
        customer = customerRepository.save(customer);

        log.info("Customer updated: id={}, name={}, phone={}", customer.getId(), customer.getName(), customer.getPhone());
        return mapToResponse(customer);
    }

    // =========================================================================
    // CUS-05: XÓA MỀM KHÁCH HÀNG
    // =========================================================================

    /**
     * Xóa mềm khách hàng.
     *
     * Quy trình:
     * 1. Load và validate KH tồn tại.
     * 2. Kiểm tra không có đơn hàng PENDING chưa hoàn thành.
     * 3. Gọi repository.delete() → Hibernate thực thi @SQLDelete custom SQL:
     *    UPDATE customers SET is_deleted=true, phone=CONCAT(phone,'_deleted_',UNIX_TIMESTAMP()) WHERE id=?
     * 4. Ghi AuditLog async (Quy tắc #9).
     *
     * @SQLDelete đảm bảo:
     * - Record được giữ lại → lịch sử orders tham chiếu customerId vẫn đọc được.
     * - phone được rename → giải phóng UNIQUE constraint → KH mới có thể dùng SĐT này.
     *
     * 💡 Senior Note — Tại sao không dùng @PreAuthorize ở Service?
     * @PreAuthorize đặt ở Controller để enforce security tại HTTP layer.
     * Service layer là "trusted" — không tự ý kiểm tra permission tại đây.
     * Việc gọi deleteCustomer() đã được filter bởi Spring Security trước đó.
     *
     * @param customerId ID khách hàng cần xóa
     * @param deletedByUsername Username người thực hiện (để ghi AuditLog)
     * @param deletedByUserId   UserId người thực hiện
     */
    @Transactional
    public void deleteCustomer(Long customerId, String deletedByUsername, Long deletedByUserId) {
        log.debug("Soft deleting customer id={}, by user={}", customerId, deletedByUsername);

        // Bước 1: Validate KH tồn tại
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new DomainException(ErrorCode.CUSTOMER_NOT_FOUND,
                Map.of("id", customerId)));

        // Bước 2: Kiểm tra không có đơn hàng active
        // 💡 Senior Note: Kiểm tra trước khi xóa, KHÔNG check sau — fail fast pattern.
        // Nếu có đơn PENDING → throw ngay, không bao giờ chạm vào delete().
        long activeOrders = customerRepository.countActiveOrdersByCustomerId(customerId);
        if (activeOrders > 0) {
            throw new DomainException(ErrorCode.CUSTOMER_HAS_PENDING_ORDERS,
                Map.of(
                    "customerId", customerId,
                    "activeOrderCount", activeOrders
                ));
        }

        // Bước 3: Soft Delete
        // Hibernate sẽ thực thi @SQLDelete:
        //   UPDATE customers
        //   SET is_deleted = true,
        //       phone = CONCAT(phone, '_deleted_', UNIX_TIMESTAMP())
        //   WHERE id = ?
        // Sau đó Hibernate evict entity khỏi 1st-level cache.
        String customerName = customer.getName();
        String customerPhone = customer.getPhone();
        customerRepository.delete(customer);

        log.info("Customer soft deleted: id={}, name={}, phone={}, by={}",
            customerId, customerName, customerPhone, deletedByUsername);

        // Bước 4: Ghi AuditLog bất đồng bộ (Quy tắc #9 — @Async + REQUIRES_NEW)
        // AuditLogService.log() chạy trên thread pool riêng biệt → không block response.
        // Nếu ghi log fail → chỉ log error nội bộ, không rollback nghiệp vụ xóa.
        auditLogService.log(
            deletedByUserId,
            deletedByUsername,
            "DELETE_CUSTOMER",
            "CUSTOMER",
            customerId,
            String.format("{\"name\":\"%s\",\"phone\":\"%s\"}", customerName, customerPhone)
        );
    }

    // =========================================================================
    // PRIVATE HELPER
    // =========================================================================

    /**
     * Map Customer entity sang CustomerResponse DTO.
     */
    private CustomerResponse mapToResponse(Customer customer) {
        return CustomerResponse.builder()
            .id(customer.getId())
            .name(customer.getName())
            .phone(customer.getPhone())
            .email(customer.getEmail())
            .note(customer.getNote())
            .loyaltyPoints(customer.getLoyaltyPoints())
            .totalSpent(customer.getTotalSpent())
            .createdAt(customer.getCreatedAt())
            .updatedAt(customer.getUpdatedAt())
            .build();
    }
}
