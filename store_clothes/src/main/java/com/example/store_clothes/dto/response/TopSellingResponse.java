package com.example.store_clothes.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * TopSellingResponse - DTO trả về cho API top sản phẩm bán chạy.
 *
 * Thứ hạng (rank) được tính trong tầng Service dựa trên thứ tự danh sách
 * trả về từ DB (đã ORDER BY totalQuantitySold DESC).
 */
@Getter
@Builder
public class TopSellingResponse {

    /** Thứ hạng bán chạy (1 = bán nhiều nhất). */
    private Integer rank;

    /** ID biến thể sản phẩm. */
    private Long variantId;

    /** Mã SKU nội bộ. */
    private String sku;

    /** Tên sản phẩm cha. */
    private String productName;

    /** Màu sắc biến thể. */
    private String color;

    /** Kích cỡ biến thể. */
    private String size;

    /** Tổng số lượng đã bán trong kỳ. */
    private Long totalQuantitySold;

    /** Tổng doanh thu của biến thể này trong kỳ (VND). */
    private BigDecimal totalRevenue;
}
