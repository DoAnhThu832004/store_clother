package com.example.store_clothes.repository.projection;

import java.math.BigDecimal;

/**
 * TopSellingProjection - JPA Interface-based Projection cho báo cáo top sản phẩm bán chạy.
 *
 * Mỗi getter ánh xạ với alias trong native query GROUP BY:
 * - variantId         → oi.variant_id AS variantId
 * - sku               → pv.sku AS sku
 * - productName       → p.name AS productName
 * - color             → pv.color AS color
 * - size              → pv.size AS size
 * - totalQuantitySold → SUM(oi.quantity) AS totalQuantitySold
 * - totalRevenue      → SUM(oi.quantity * oi.price_at_sale) AS totalRevenue
 */
public interface TopSellingProjection {
    Long getVariantId();
    String getSku();
    String getProductName();
    String getColor();
    String getSize();
    Long getTotalQuantitySold();
    BigDecimal getTotalRevenue();
}
