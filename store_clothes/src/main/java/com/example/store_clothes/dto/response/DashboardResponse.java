package com.example.store_clothes.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * DashboardResponse - DTO tổng hợp cho màn hình Dashboard.
 *
 * Tập hợp 4 metrics quan trọng nhất cần hiển thị ngay:
 * 1. Doanh thu hôm nay
 * 2. Số đơn hàng hôm nay
 * 3. Top 5 sản phẩm bán chạy trong tháng
 * 4. Số lượng variant sắp hết hàng (cần nhập thêm)
 *
 * 4 query này được chạy SONG SONG qua CompletableFuture trong ReportService
 * để giảm tổng thời gian response.
 */
@Getter
@Builder
public class DashboardResponse {

    /** Tổng doanh thu hôm nay (VND). */
    private BigDecimal revenueToday;

    /** Số đơn hàng hoàn thành hôm nay. */
    private Long ordersToday;

    /** Top 5 sản phẩm bán chạy nhất trong tháng hiện tại. */
    private List<TopSellingResponse> topSellingThisMonth;

    /**
     * Số biến thể có tồn kho < 5 và đang ACTIVE.
     * Dùng để hiển thị badge cảnh báo "Cần nhập hàng".
     */
    private Long lowStockCount;
}
