package com.example.store_clothes.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CustomerDetailResponse — DTO trả về thông tin chi tiết Khách Hàng kèm lịch sử mua (CUS-03).
 *
 * =====================================================================
 * THIẾT KẾ 2-QUERY RIÊNG BIỆT (QUAN TRỌNG — ĐỌC KỸ):
 * =====================================================================
 *
 * Lý do KHÔNG dùng 1 JOIN FETCH lớn để lấy toàn bộ orders của KH:
 *
 * (1) PERFORMANCE — Cartesian Product:
 *     Nếu KH có 500 đơn hàng, mỗi đơn 10 items →
 *     JOIN FETCH orders + items = 5,000 rows từ DB.
 *     Network bandwidth tăng 5000x so với thực tế cần (chỉ 10 đơn gần nhất).
 *
 * (2) PAGINATION KHÔNG ĐÚNG:
 *     Spring Data không thể OFFSET/LIMIT đúng khi JOIN FETCH collection.
 *     → HibernateJpaDialect ném warning "HHH90003004: firstResult/maxResults
 *       specified with collection fetch; applying in memory!" → toàn bộ 500 đơn
 *       được load vào memory rồi mới paginate → memory leak.
 *
 * (3) SEPARATION OF CONCERNS:
 *     Query 1: SELECT COUNT + SUM từ orders WHERE customerId → lightweight.
 *     Query 2: SELECT TOP 10 orders WHERE customerId ORDER BY createdAt DESC → bounded.
 *     Mỗi query làm 1 việc rõ ràng, cache riêng biệt, dễ optimize độc lập.
 *
 * 💡 Senior Note: Đây là pattern "N+1 phòng ngừa" ngược chiều — tách 1 query "béo"
 *    thành N query nhỏ có kiểm soát. Rule of thumb: Nếu JOIN FETCH kéo >100 rows
 *    không cần thiết → tách query.
 */
@Getter
@Builder
public class CustomerDetailResponse {

    // =========================================================================
    // Thông tin khách hàng
    // =========================================================================

    /** ID khách hàng */
    private Long id;

    /** Tên khách hàng */
    private String name;

    /** Số điện thoại */
    private String phone;

    /** Email */
    private String email;

    /** Ghi chú */
    private String note;

    /** Điểm tích lũy hiện tại */
    private Integer loyaltyPoints;

    /** Tổng tiền đã chi tiêu */
    private BigDecimal totalSpent;

    /** Thời điểm tạo */
    private LocalDateTime createdAt;

    /** Thời điểm cập nhật */
    private LocalDateTime updatedAt;

    // =========================================================================
    // Thống kê lịch sử mua hàng
    // =========================================================================

    /**
     * Tổng số đơn hàng đã mua (bao gồm cả đơn cũ — từ Query 1: COUNT).
     */
    private Long totalOrders;

    /**
     * Tổng tiền thực tế đã thanh toán (cross-validate với totalSpent — từ Query 1: SUM).
     * Có thể khác totalSpent nếu có đơn hủy sau khi đã cộng totalSpent.
     */
    private BigDecimal totalAmountFromOrders;

    /**
     * 10 đơn hàng gần nhất (từ Query 2: Pageable — ORDER BY createdAt DESC LIMIT 10).
     * Không load toàn bộ orders → tránh N+1 và memory leak.
     */
    private List<OrderSummary> recentOrders;

    // =========================================================================
    // INNER DTO
    // =========================================================================

    /**
     * Tóm tắt thông tin 1 đơn hàng — chỉ những trường cần hiển thị trong lịch sử.
     */
    @Getter
    @Builder
    public static class OrderSummary {

        /** Mã hóa đơn */
        private String orderCode;

        /** Tổng tiền thực trả của đơn (finalAmount = totalAmount - discount + tax...) */
        private BigDecimal finalAmount;

        /** Thời điểm tạo đơn */
        private LocalDateTime createdAt;
    }
}
