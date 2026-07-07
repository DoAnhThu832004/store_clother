package com.example.store_clothes.repository;

import com.example.store_clothes.entity.Order;
import com.example.store_clothes.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * OrderRepository - Repository cho Order entity.
 *
 * Thread-safety note:
 * - Tất cả write operations chạy trong @Transactional của OrderService.
 * - findMaxOrderCodeByPrefix() chạy trong cùng transaction với save() để tránh
 *   race condition sinh trùng mã hóa đơn.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Lấy mã hóa đơn lớn nhất trong ngày theo prefix để sinh mã kế tiếp.
     *
     * Cùng pattern với ImportReceiptRepository.findMaxReceiptCodeByPrefix().
     * Chạy trong @Transactional → tránh 2 thread sinh mã giống nhau.
     *
     * @param prefix Tiền tố ngày (ví dụ: "HD-20260626-")
     * @return Optional mã hóa đơn lớn nhất theo lexicographic (4 chữ số cuối)
     */
    @Query("SELECT o.orderCode FROM Order o WHERE o.orderCode LIKE CONCAT(:prefix, '%') AND LENGTH(o.orderCode) = LENGTH(CONCAT(:prefix, '0000')) ORDER BY o.orderCode DESC LIMIT 1")
    Optional<String> findMaxOrderCodeByPrefix(@Param("prefix") String prefix);

    /**
     * Load Order kèm danh sách OrderItem bằng JOIN FETCH.
     * Tránh LazyInitializationException khi truy cập o.getItems() ngoài transaction.
     * Dùng cho ORD-01 (cancelOrder) và getOrderDetail.
     *
     * @param orderId ID của Order cần load
     * @return Optional<Order> với items đã được load sẵn
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") UUID orderId);

    // =========================================================================
    // ORD-02: Danh sách hóa đơn với filter đa điều kiện + phân trang
    // =========================================================================

    /**
     * Tìm kiếm hóa đơn với bộ lọc đa điều kiện.
     *
     * 💡 Senior Note — Tại sao filter status nullable thay vì query riêng?
     * (:status IS NULL OR o.status = :status) cho phép dùng cùng 1 query
     * cho cả 2 trường hợp: có filter và không filter.
     * Hibernate sẽ optimize điều kiện IS NULL → index scan hiệu quả.
     * Tránh code trùng lặp so với viết 2 query riêng biệt.
     *
     * @param status     Filter theo OrderStatus (null = không filter)
     * @param customerId Filter theo khách hàng (null = không filter)
     * @param from       Filter từ ngày tạo (null = không filter)
     * @param to         Filter đến ngày tạo (null = không filter)
     * @param pageable   Phân trang
     * @return Page<Order>
     */
    @Query("""
        SELECT o FROM Order o
        WHERE (:status IS NULL OR o.status = :status)
          AND (:customerId IS NULL OR o.customerId = :customerId)
          AND (:from IS NULL OR o.createdAt >= :from)
          AND (:to IS NULL OR o.createdAt <= :to)
        ORDER BY o.createdAt DESC
        """)
    Page<Order> findOrders(
        @Param("status") OrderStatus status,
        @Param("customerId") Long customerId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable
    );
}
