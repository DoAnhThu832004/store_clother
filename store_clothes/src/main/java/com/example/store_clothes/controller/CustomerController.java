package com.example.store_clothes.controller;

import com.example.store_clothes.dto.request.CreateCustomerRequest;
import com.example.store_clothes.dto.request.UpdateCustomerRequest;
import com.example.store_clothes.dto.response.ApiResponse;
import com.example.store_clothes.dto.response.CustomerDetailResponse;
import com.example.store_clothes.dto.response.CustomerResponse;
import com.example.store_clothes.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * CustomerController — Tầng kiểm soát đầu vào cho API Khách Hàng.
 *
 * Base URL: /api/v1/customers
 *
 * Nguyên tắc Controller (BẮT BUỘC):
 * - Chỉ nhận request, validate đầu vào (@Valid), gọi Service, wrap response.
 * - KHÔNG chứa logic nghiệp vụ — toàn bộ logic nằm ở CustomerService.
 * - KHÔNG đặt @Transactional ở đây (Quy tắc #1 bắt buộc).
 * - KHÔNG xử lý exception — để GlobalExceptionHandler xử lý tập trung.
 *
 * Quyền truy cập:
 * - OWNER, MANAGER, CASHIER: Xem danh sách, chi tiết, tạo mới, cập nhật.
 * - OWNER, MANAGER: Xóa khách hàng.
 */
@Slf4j
@Tag(name = "Khách Hàng", description = "Quản lý thông tin và lịch sử mua hàng của khách hàng")
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    // =========================================================================
    // CUS-01: TẠO KHÁCH HÀNG
    // =========================================================================

    /**
     * Tạo mới khách hàng.
     *
     * POST /api/v1/customers
     *
     * @param request Thông tin khách hàng từ client (đã pass @Valid)
     * @return 201 Created + CustomerResponse
     */
    @Operation(
        summary = "Tạo khách hàng mới",
        description = """
            Tạo mới khách hàng với thông tin cơ bản.
            - Số điện thoại phải unique trong danh sách KH đang active.
            - Nếu SĐT thuộc KH đã bị xóa mềm → trả lỗi rõ ràng, KHÔNG auto-restore.
            - Điểm tích lũy và tổng chi tiêu khởi tạo = 0.
            """
    )
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'CASHIER')")
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(
            @RequestBody @Valid CreateCustomerRequest request) {

        log.debug("API: Create customer, phone={}", request.getPhone());
        CustomerResponse response = customerService.createCustomer(request);
        return ResponseEntity
            .status(201)
            .body(ApiResponse.created(response));
    }

    // =========================================================================
    // CUS-02: DANH SÁCH KHÁCH HÀNG
    // =========================================================================

    /**
     * Lấy danh sách khách hàng với phân trang và tìm kiếm.
     *
     * GET /api/v1/customers?page=0&size=20&keyword=nguyen&hasLoyaltyPoints=true
     *
     * @param page             Số trang (0-indexed), mặc định 0
     * @param size             Số item mỗi trang, mặc định 20
     * @param keyword          Tìm theo tên HOẶC số điện thoại (nullable)
     * @param hasLoyaltyPoints true = chỉ lấy KH có điểm tích lũy > 0
     * @return Page<CustomerResponse>
     */
    @Operation(
        summary = "Danh sách khách hàng",
        description = """
            Lấy danh sách khách hàng có phân trang.
            - keyword: Tìm kiếm theo tên hoặc số điện thoại (case-insensitive).
            - hasLoyaltyPoints=true: Chỉ lấy KH đang có điểm tích lũy.
            """
    )
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'CASHIER')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> getCustomers(
            @Parameter(description = "Số trang (0-indexed)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số item mỗi trang")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Tìm kiếm theo tên hoặc số điện thoại")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "Chỉ lấy KH có điểm tích lũy > 0")
            @RequestParam(defaultValue = "false") boolean hasLoyaltyPoints) {

        log.debug("API: Get customers, page={}, size={}, keyword={}, hasLoyaltyPoints={}",
            page, size, keyword, hasLoyaltyPoints);
        Page<CustomerResponse> response = customerService.getCustomers(page, size, keyword, hasLoyaltyPoints);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // CUS-03: CHI TIẾT KHÁCH HÀNG
    // =========================================================================

    /**
     * Lấy chi tiết khách hàng kèm lịch sử mua hàng.
     *
     * GET /api/v1/customers/{id}
     *
     * @param id ID khách hàng
     * @return CustomerDetailResponse kèm 10 đơn hàng gần nhất
     */
    @Operation(
        summary = "Chi tiết khách hàng + lịch sử mua",
        description = """
            Trả về thông tin chi tiết khách hàng kèm:
            - totalOrders: Tổng số đơn hàng đã mua.
            - totalAmountFromOrders: Tổng tiền từ đơn hàng (cross-validate với totalSpent).
            - recentOrders: 10 đơn hàng gần nhất (orderCode, finalAmount, createdAt).
            
            Sử dụng 2 query riêng biệt thay vì 1 JOIN FETCH lớn để tránh Cartesian Product
            và Hibernate pagination bug (HHH90003004).
            """
    )
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'CASHIER')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerDetailResponse>> getCustomerDetail(
            @PathVariable Long id) {

        log.debug("API: Get customer detail, id={}", id);
        CustomerDetailResponse response = customerService.getCustomerDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // CUS-04: CẬP NHẬT KHÁCH HÀNG
    // =========================================================================

    /**
     * Cập nhật thông tin cơ bản khách hàng.
     *
     * PUT /api/v1/customers/{id}
     *
     * @param id      ID khách hàng
     * @param request Dữ liệu cập nhật (chỉ name, phone, email)
     * @return CustomerResponse sau khi cập nhật
     */
    @Operation(
        summary = "Cập nhật thông tin khách hàng",
        description = """
            Cập nhật name, phone, email của khách hàng.
            - loyaltyPoints và totalSpent KHÔNG được cập nhật qua API này.
            - Optimistic Lock: Nếu có xung đột cập nhật đồng thời → HTTP 409.
            """
    )
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'CASHIER')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @PathVariable Long id,
            @RequestBody @Valid UpdateCustomerRequest request) {

        log.debug("API: Update customer, id={}, phone={}", id, request.getPhone());
        CustomerResponse response = customerService.updateCustomer(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật khách hàng thành công"));
    }

    // =========================================================================
    // CUS-05: XÓA MỀM KHÁCH HÀNG
    // =========================================================================

    /**
     * Xóa mềm khách hàng.
     *
     * DELETE /api/v1/customers/{id}
     *
     * @param id          ID khách hàng cần xóa
     * @param currentUser Người dùng đang thực hiện (inject từ JWT SecurityContext)
     * @return 200 OK + message
     */
    @Operation(
        summary = "Xóa mềm khách hàng",
        description = """
            Xóa mềm khách hàng — giữ lại record để bảo toàn lịch sử đơn hàng.
            
            Quy trình:
            1. Validate KH tồn tại.
            2. Kiểm tra không có đơn hàng PENDING chưa hoàn thành → lỗi nếu có.
            3. Soft Delete: SET is_deleted=true, phone=CONCAT(phone,'_deleted_',UNIX_TIMESTAMP())
               → Giải phóng UNIQUE constraint trên phone.
            4. Ghi AuditLog bất đồng bộ.
            
            Chỉ OWNER và MANAGER được phép xóa khách hàng.
            """
    )
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails currentUser) {

        log.debug("API: Delete customer, id={}, by={}", id, currentUser.getUsername());

        // Lấy thông tin người thực hiện từ SecurityContext (inject từ JWT)
        // Username dùng để ghi AuditLog — xác định ai đã xóa KH
        String username = currentUser.getUsername();

        // 💡 Senior Note: Không inject userId trực tiếp ở Controller.
        // Trong hệ thống thực, có thể lấy userId từ CustomUserDetails hoặc JWT claims.
        // Tạm dùng null và để AuditLogService handle gracefully.
        customerService.deleteCustomer(id, username, null);

        return ResponseEntity.ok(ApiResponse.success("Xóa khách hàng thành công"));
    }
}
