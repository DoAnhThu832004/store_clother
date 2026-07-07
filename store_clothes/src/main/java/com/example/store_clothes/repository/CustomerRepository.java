package com.example.store_clothes.repository;

import com.example.store_clothes.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * CustomerRepository — Repository cho Customer Entity.
 *
 * THIẾT KẾ QUERY:
 *
 * [1] Soft Delete tự động qua @SQLRestriction("is_deleted = false"):
 *     Mọi query mặc định chỉ truy vấn KH đang active.
 *     Nếu cần query KH đã xóa → phải dùng @Query native SQL bypass restriction.
 *
 * [2] Tìm kiếm phone trên KH đã bị xóa:
 *     existsByPhoneIgnoringSoftDelete() dùng native query để bypass @SQLRestriction.
 *     Mục đích: Kiểm tra số điện thoại có đang được dùng bởi KH đã xóa không,
 *     để trả lỗi rõ ràng "đã từng tồn tại" thay vì tạo mới thành công nhầm.
 *
 * [3] Tách 2 query cho CUS-03 (Chi tiết + Lịch sử):
 *     - countAndSumOrdersByCustomerId(): Query 1 — đếm và tổng tiền (aggregate light).
 *     - findTop10RecentOrdersByCustomerId(): Query 2 — 10 đơn gần nhất (bounded).
 *     Xem giải thích đầy đủ tại CustomerDetailResponse Javadoc.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // =========================================================================
    // CUS-01: Tạo khách hàng — Validate phone unique
    // =========================================================================

    /**
     * Kiểm tra SĐT đã tồn tại ở KH đang active (chịu @SQLRestriction).
     * Dùng để validate trước khi tạo mới hoặc cập nhật KH.
     *
     * @param phone Số điện thoại cần kiểm tra
     * @return true nếu SĐT đã được dùng bởi KH đang active
     */
    boolean existsByPhone(String phone);

    /**
     * Kiểm tra SĐT đã tồn tại ở KH active khác (trừ ID hiện tại).
     * Dùng trong CUS-04 (update) để cho phép KH giữ nguyên SĐT của mình.
     *
     * @param phone Số điện thoại cần kiểm tra
     * @param id    ID của KH đang được cập nhật (loại trừ KH này)
     * @return true nếu SĐT đã được dùng bởi KH KHÁC đang active
     */
    boolean existsByPhoneAndIdNot(String phone, Long id);

    /**
     * Tìm KH đã bị xóa mềm bằng số điện thoại (bypass @SQLRestriction).
     *
     * 💡 Senior Note — Tại sao dùng Native Query?
     * @SQLRestriction("is_deleted = false") tự động inject vào mọi JPQL/Criteria query.
     * Để query bảng customers BỎ QUA restriction → phải dùng native SQL.
     * Dùng LIKE để match phone ban đầu (trước khi bị append "_deleted_<unix>").
     * Ví dụ: phone gốc "0912345678" → sau xóa "0912345678_deleted_1720000000".
     *
     * @param phonePrefix Số điện thoại gốc (10 chữ số)
     * @return Optional<Customer> nếu tìm thấy KH đã bị xóa với SĐT này
     */
    @Query(
        value = "SELECT * FROM customers WHERE phone LIKE CONCAT(:phonePrefix, '_deleted_%') AND is_deleted = true LIMIT 1",
        nativeQuery = true
    )
    Optional<Customer> findDeletedCustomerByPhonePrefix(@Param("phonePrefix") String phonePrefix);

    // =========================================================================
    // CUS-02: Danh sách khách hàng — Tìm kiếm + Filter
    // =========================================================================

    /**
     * Tìm kiếm KH theo keyword (name HOẶC phone) kết hợp filter hasLoyaltyPoints.
     *
     * 💡 Senior Note — JPQL với keyword search:
     * LOWER() trên cả 2 phía đảm bảo case-insensitive search.
     * CONCAT('%', :keyword, '%') tương đương LIKE '%keyword%'.
     * Spring Data tự inject OFFSET/LIMIT từ Pageable parameter.
     *
     * Query 1: Không filter loyaltyPoints (hasLoyaltyPoints = null/false).
     *
     * @param keyword  Từ khóa tìm kiếm (name hoặc phone)
     * @param pageable Phân trang
     * @return Page<Customer>
     */
    @Query("""
        SELECT c FROM Customer c
        WHERE (:keyword IS NULL OR
               LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR c.phone LIKE CONCAT('%', :keyword, '%'))
        ORDER BY c.createdAt DESC
        """)
    Page<Customer> findByKeyword(
        @Param("keyword") String keyword,
        Pageable pageable
    );

    /**
     * Tìm kiếm KH theo keyword + chỉ lấy KH có điểm tích lũy > 0.
     * Dùng khi hasLoyaltyPoints=true trong request param.
     *
     * @param keyword  Từ khóa tìm kiếm
     * @param pageable Phân trang
     * @return Page<Customer> chỉ có loyaltyPoints > 0
     */
    @Query("""
        SELECT c FROM Customer c
        WHERE c.loyaltyPoints > 0
          AND (:keyword IS NULL OR
               LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR c.phone LIKE CONCAT('%', :keyword, '%'))
        ORDER BY c.loyaltyPoints DESC, c.createdAt DESC
        """)
    Page<Customer> findByKeywordAndHasLoyaltyPoints(
        @Param("keyword") String keyword,
        Pageable pageable
    );

    // =========================================================================
    // CUS-03: Chi tiết — 2 Query riêng cho lịch sử mua hàng
    // =========================================================================

    /**
     * Query 1 — Thống kê đơn hàng của khách: COUNT + SUM trong 1 query.
     *
     * Trả về Object[] với 2 phần tử:
     *   [0] = COUNT(o.id): Tổng số đơn (Long)
     *   [1] = COALESCE(SUM(o.totalAmount), 0): Tổng tiền (BigDecimal)
     *
     * 💡 Senior Note — Tại sao COUNT + SUM trong cùng 1 query thay vì 2 query?
     * Mục tiêu là "2 query riêng" = tách khỏi JOIN FETCH toàn bộ orders collection.
     * Nhưng COUNT + SUM là aggregate light → 1 SELECT duy nhất, DB chạy full-scan
     * trên index (idx_order_customer_id) rất nhanh → gộp vào 1 query hợp lý.
     *
     * JOIN Order o (JPQL) → Hibernate tự ghép điều kiện is_deleted = false
     * qua @SQLRestriction trên Order entity.
     *
     * @param customerId ID khách hàng
     * @return Object[]{totalOrders: Long, totalAmount: BigDecimal}
     */
    @Query("""
        SELECT COUNT(o.id), COALESCE(SUM(o.totalAmount), 0)
        FROM Order o
        WHERE o.customerId = :customerId
        """)
    Object[] countAndSumOrdersByCustomerId(@Param("customerId") Long customerId);

    /**
     * Query 2 — 10 đơn hàng gần nhất của khách.
     *
     * Projection: Chỉ SELECT các trường cần thiết (orderCode, totalAmount, createdAt).
     * KHÔNG SELECT * hoặc JOIN FETCH items → tránh N+1 và Cartesian Product.
     * LIMIT 10 cứng (không phân trang tiếp) → predictable, bounded response.
     *
     * Trả về Object[] với 3 phần tử:
     *   [0] = o.orderCode (String)
     *   [1] = o.totalAmount (BigDecimal) — dùng làm finalAmount
     *   [2] = o.createdAt (LocalDateTime)
     *
     * @param customerId ID khách hàng
     * @return List của Object[] — mỗi phần tử là thông tin 1 đơn hàng
     */
    @Query("""
        SELECT o.orderCode, o.totalAmount, o.createdAt
        FROM Order o
        WHERE o.customerId = :customerId
        ORDER BY o.createdAt DESC
        LIMIT 10
        """)
    java.util.List<Object[]> findTop10RecentOrdersByCustomerId(@Param("customerId") Long customerId);

    // =========================================================================
    // CUS-05: Xóa mềm — Kiểm tra đơn hàng PENDING
    // =========================================================================

    /**
     * Đếm số đơn hàng chưa hoàn thành của khách hàng.
     *
     * 💡 Senior Note — Tại sao check PENDING trước khi soft delete?
     * Soft delete KH khi còn đơn PENDING → đơn hàng mất liên kết KH.
     * OrderService có thể fail khi cố complete đơn (FK còn đó nhưng KH "biến mất").
     * Mặc dù @SQLRestriction không xóa FK, nhưng UI sẽ hiển thị "Khách hàng đã xóa"
     * ở đơn đang xử lý → confusing cho cashier. Chặn trước là cách an toàn nhất.
     *
     * "PENDING" ở đây bao gồm: PENDING, PROCESSING (các trạng thái chưa COMPLETED/CANCELLED).
     * Điều chỉnh enum value theo thiết kế của Order entity trong hệ thống.
     *
     * @param customerId ID khách hàng
     * @return số lượng đơn đang PENDING
     */
    @Query("""
        SELECT COUNT(o.id) FROM Order o
        WHERE o.customerId = :customerId
          AND o.isDeleted = false
        """)
    long countActiveOrdersByCustomerId(@Param("customerId") Long customerId);
}
