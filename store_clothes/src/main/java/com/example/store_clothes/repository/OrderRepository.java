package com.example.store_clothes.repository;

import com.example.store_clothes.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     *
     * @param orderId ID của Order cần load
     * @return Optional<Order> với items đã được load sẵn
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") UUID orderId);
}
