package com.example.store_clothes.repository.projection;

import java.math.BigDecimal;

/**
 * DeadStockProjection - JPA Interface-based Projection cho báo cáo hàng tồn chậm (dead stock).
 *
 * "Dead stock" = sản phẩm còn tồn kho nhưng không có đơn hoàn thành nào
 * trong X ngày gần nhất.
 *
 * Mỗi getter ánh xạ với alias trong native query:
 * - variantId   → pv.id AS variantId
 * - sku         → pv.sku AS sku
 * - productName → p.name AS productName
 * - color       → pv.color AS color
 * - size        → pv.size AS size
 * - inventory   → pv.inventory AS inventory
 * - importPrice → pv.import_price AS importPrice
 */
public interface DeadStockProjection {
    Long getVariantId();
    String getSku();
    String getProductName();
    String getColor();
    String getSize();
    Integer getInventory();
    BigDecimal getImportPrice();
}
