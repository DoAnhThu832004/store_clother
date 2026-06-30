package com.example.store_clothes.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * DeadStockResponse - DTO trả về cho API báo cáo hàng tồn chậm.
 *
 * "Hàng tồn chậm" = variant còn hàng trong kho nhưng không bán được
 * trong X ngày gần nhất (ngưỡng cấu hình trong ReportService).
 *
 * stockValue = importPrice × inventory:
 * Giúp manager đánh giá mức độ "đóng băng" vốn của từng sản phẩm.
 */
@Getter
@Builder
public class DeadStockResponse {

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

    /** Số lượng tồn kho hiện tại. */
    private Integer inventory;

    /** Giá nhập gần nhất (VND/cái). */
    private BigDecimal importPrice;

    /**
     * Giá trị tồn kho bị đóng băng = importPrice × inventory (VND).
     * Tính trong Service để tránh tính toán phức tạp ở tầng SQL.
     */
    private BigDecimal stockValue;
}
