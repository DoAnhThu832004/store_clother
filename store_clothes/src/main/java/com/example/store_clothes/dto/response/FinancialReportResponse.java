package com.example.store_clothes.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * FinancialReportResponse - DTO trả về cho API báo cáo tài chính tổng hợp.
 *
 * Bao gồm:
 * - Doanh thu (tổng tiền khách trả)
 * - Giá vốn (tổng giá nhập)
 * - Lợi nhuận gộp = Doanh thu - Giá vốn
 * - Biên lợi nhuận gộp (%)
 * - Tổng số đơn hàng
 * - Giá trị trung bình mỗi đơn
 */
@Getter
@Builder
public class FinancialReportResponse {

    /** Tổng doanh thu trong kỳ báo cáo (VND). */
    private BigDecimal totalRevenue;

    /** Tổng giá vốn hàng bán (VND). */
    private BigDecimal totalCost;

    /** Lợi nhuận gộp = totalRevenue - totalCost (VND). */
    private BigDecimal grossProfit;

    /**
     * Biên lợi nhuận gộp (%).
     * Công thức: grossProfit / totalRevenue × 100, làm tròn 2 chữ số thập phân.
     */
    private BigDecimal grossMarginPercent;

    /** Tổng số đơn hàng hoàn thành trong kỳ. */
    private Long totalOrders;

    /**
     * Giá trị trung bình mỗi đơn hàng.
     * Công thức: totalRevenue / totalOrders.
     */
    private BigDecimal avgOrderValue;

    /** Thời điểm bắt đầu kỳ báo cáo (ISO-8601 string). Null = không giới hạn. */
    private String fromDate;

    /** Thời điểm kết thúc kỳ báo cáo (ISO-8601 string). Null = không giới hạn. */
    private String toDate;
}
