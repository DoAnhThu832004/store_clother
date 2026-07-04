package com.example.store_clothes.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * OrderResponse - DTO trả về cho Client sau khi thanh toán thành công.
 *
 * Tất cả dữ liệu giá là snapshot — không query lại từ bảng Product.
 */
@Getter
@Builder
public class OrderResponse {

    private java.util.UUID id;
    private String orderCode;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal changeAmount;
    private String note;
    private LocalDateTime createdAt;
    private List<OrderItemSummary> items;

    // =========================================================================
    // NESTED DTO — Chi tiết từng dòng hàng
    // =========================================================================

    @Getter
    @Builder
    public static class OrderItemSummary {
        private Long variantId;
        private String skuAtSale;
        private String productNameAtSale;
        private Integer quantity;
        private BigDecimal priceAtSale;
        private BigDecimal importPriceAtSale;
        private BigDecimal lineTotal;
    }
}
