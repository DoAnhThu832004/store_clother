package com.example.store_clothes.repository;

import com.example.store_clothes.entity.Order;
import com.example.store_clothes.repository.projection.DeadStockProjection;
import com.example.store_clothes.repository.projection.FinancialSummaryProjection;
import com.example.store_clothes.repository.projection.TopSellingProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ReportRepository - Repository chỉ đọc dành riêng cho module báo cáo.
 *
 * THIẾT KẾ:
 * 1. Extends Repository (không phải JpaRepository) để KHÔNG expose các method
 *    write (save, delete...) — nguyên tắc Interface Segregation.
 * 2. Toàn bộ query dùng JPA Interface-based Projection thay vì Object[]:
 *    - Ánh xạ theo tên alias → không vỡ code khi thay đổi thứ tự SELECT.
 *    - IDE có thể gợi ý, compile-time type safe.
 * 3. Tham số thời gian truyền từ Java (LocalDateTime với ZoneId "Asia/Ho_Chi_Minh"):
 *    - Giải quyết "Database Timezone Dependency".
 *    - Tránh phụ thuộc vào timezone của MySQL Server.
 *
 * @see com.example.store_clothes.service.ReportService
 */
@org.springframework.stereotype.Repository
public interface ReportRepository extends Repository<Order, Long> {

    /**
     * Tổng hợp tài chính theo khoảng thời gian.
     *
     * Trả về: tổng doanh thu, tổng giá vốn, lợi nhuận gộp, số đơn hàng.
     * Chỉ tính đơn có status = 'COMPLETED' và chưa bị xóa mềm.
     *
     * @param from  Thời điểm bắt đầu (null = không giới hạn từ đầu)
     * @param to    Thời điểm kết thúc (null = không giới hạn đến cuối)
     * @return FinancialSummaryProjection với các giá trị tổng hợp
     */
    @Query(value = "SELECT " +
            "COALESCE(SUM(oi.quantity * oi.price_at_sale), 0) AS totalRevenue, " +
            "COALESCE(SUM(oi.quantity * oi.import_price_at_sale), 0) AS totalCost, " +
            "COALESCE(SUM(oi.quantity * (oi.price_at_sale - oi.import_price_at_sale)), 0) AS grossProfit, " +
            "COUNT(DISTINCT o.id) AS totalOrders " +
            "FROM orders o " +
            "JOIN order_items oi ON oi.order_id = o.id " +
            "WHERE o.status = 'COMPLETED' " +
            "AND o.is_deleted = false " +
            "AND (:from IS NULL OR o.created_at >= :from) " +
            "AND (:to IS NULL OR o.created_at <= :to)", nativeQuery = true)
    FinancialSummaryProjection getFinancialSummary(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * Top N sản phẩm bán chạy nhất theo số lượng bán ra.
     *
     * GROUP BY variant để đếm chính xác từng biến thể (màu + size riêng biệt).
     * ORDER BY totalQuantitySold DESC → sản phẩm bán nhiều nhất lên đầu.
     *
     * @param from     Thời điểm bắt đầu lọc đơn hàng
     * @param to       Thời điểm kết thúc lọc đơn hàng
     * @param limit    Số lượng sản phẩm muốn lấy (top 5, top 10...)
     * @return Danh sách TopSellingProjection đã sắp xếp theo doanh số
     */
    @Query(value = "SELECT " +
            "oi.variant_id AS variantId, " +
            "pv.sku AS sku, " +
            "p.name AS productName, " +
            "pv.color AS color, " +
            "pv.size AS size, " +
            "SUM(oi.quantity) AS totalQuantitySold, " +
            "SUM(oi.quantity * oi.price_at_sale) AS totalRevenue " +
            "FROM order_items oi " +
            "JOIN orders o ON o.id = oi.order_id " +
            "JOIN product_variants pv ON pv.id = oi.variant_id " +
            "JOIN products p ON p.id = pv.product_id " +
            "WHERE o.status = 'COMPLETED' " +
            "AND o.is_deleted = false " +
            "AND (:from IS NULL OR o.created_at >= :from) " +
            "AND (:to IS NULL OR o.created_at <= :to) " +
            "GROUP BY oi.variant_id, pv.sku, p.name, pv.color, pv.size " +
            "ORDER BY totalQuantitySold DESC " +
            "LIMIT :limitVal", nativeQuery = true)
    List<TopSellingProjection> getTopSelling(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("limitVal") int limit
    );

    /**
     * Danh sách hàng tồn chậm (dead stock).
     *
     * Dead stock = variant còn hàng tồn (>= minInventory) nhưng KHÔNG có
     * đơn COMPLETED nào trong khoảng thời gian kể từ deadStockThreshold.
     *
     * Dùng NOT EXISTS thay vì LEFT JOIN + IS NULL:
     * - Hiệu năng tốt hơn khi bảng order_items lớn (MySQL optimizer).
     * - Ngữ nghĩa rõ ràng hơn.
     *
     * @param minInventory       Số lượng tồn tối thiểu để tính là "có hàng"
     * @param deadStockThreshold Ngưỡng thời gian: variant không bán kể từ ngày này
     * @return Danh sách DeadStockProjection sắp xếp theo tồn kho giảm dần
     */
    @Query(value = "SELECT " +
            "pv.id AS variantId, " +
            "pv.sku AS sku, " +
            "p.name AS productName, " +
            "pv.color AS color, " +
            "pv.size AS size, " +
            "pv.inventory AS inventory, " +
            "pv.import_price AS importPrice " +
            "FROM product_variants pv " +
            "JOIN products p ON p.id = pv.product_id " +
            "WHERE pv.inventory >= :minInventory " +
            "AND pv.is_deleted = false " +
            "AND p.is_deleted = false " +
            "AND NOT EXISTS ( " +
            "    SELECT 1 " +
            "    FROM order_items oi " +
            "    JOIN orders o ON o.id = oi.order_id " +
            "    WHERE oi.variant_id = pv.id " +
            "    AND o.status = 'COMPLETED' " +
            "    AND o.is_deleted = false " +
            "    AND o.created_at >= :deadStockThreshold " +
            ") " +
            "ORDER BY pv.inventory DESC", nativeQuery = true)
    List<DeadStockProjection> getDeadStock(
            @Param("minInventory") int minInventory,
            @Param("deadStockThreshold") LocalDateTime deadStockThreshold
    );

    /**
     * Doanh thu hôm nay — dùng cho Dashboard widget.
     *
     * startOfToday / endOfToday tính từ Java với ZoneId "Asia/Ho_Chi_Minh":
     * → Tránh phụ thuộc CURDATE()/NOW() của MySQL Server timezone.
     *
     * @param startOfToday Đầu ngày hôm nay (00:00:00)
     * @param endOfToday   Cuối ngày hôm nay (23:59:59.999999999)
     * @return Tổng doanh thu hôm nay, trả về null nếu chưa có đơn
     */
    @Query(value = "SELECT COALESCE(SUM(oi.quantity * oi.price_at_sale), 0) " +
            "FROM orders o " +
            "JOIN order_items oi ON oi.order_id = o.id " +
            "WHERE o.status = 'COMPLETED' " +
            "AND o.is_deleted = false " +
            "AND o.created_at >= :startOfToday " +
            "AND o.created_at <= :endOfToday", nativeQuery = true)
    BigDecimal getRevenueTodayRaw(
            @Param("startOfToday") LocalDateTime startOfToday,
            @Param("endOfToday") LocalDateTime endOfToday
    );

    /**
     * Đếm số đơn hàng hoàn thành hôm nay — dùng cho Dashboard widget.
     *
     * @param startOfToday Đầu ngày hôm nay
     * @param endOfToday   Cuối ngày hôm nay
     * @return Số đơn hàng hoàn thành trong ngày
     */
    @Query(value = "SELECT COUNT(*) FROM orders " +
            "WHERE status = 'COMPLETED' " +
            "AND is_deleted = false " +
            "AND created_at >= :startOfToday " +
            "AND created_at <= :endOfToday", nativeQuery = true)
    Long countOrdersToday(
            @Param("startOfToday") LocalDateTime startOfToday,
            @Param("endOfToday") LocalDateTime endOfToday
    );

    /**
     * Đếm số biến thể có tồn kho thấp (inventory < 5 và đang ACTIVE).
     *
     * Ngưỡng < 5 là mặc định, có thể cấu hình thành property sau.
     *
     * @return Số lượng variant cần nhập hàng
     */
    @Query(value = "SELECT COUNT(*) FROM product_variants " +
            "WHERE inventory < 5 " +
            "AND is_deleted = false " +
            "AND status = 'ACTIVE'", nativeQuery = true)
    Long countLowStock();
}
