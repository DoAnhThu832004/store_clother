package com.example.store_clothes.repository.projection;

import java.math.BigDecimal;

/**
 * FinancialSummaryProjection - JPA Interface-based Projection.
 *
 * GIẢI QUYẾT VẤN ĐỀ "Brittle Object[] Mapping":
 * - Trước: row[0], row[1], row[2]... vỡ code khi đổi thứ tự SELECT.
 * - Sau: Ánh xạ theo tên cột (alias trong SQL → getter trong interface).
 *   Thứ tự SELECT không còn quan trọng.
 *
 * Các getter tương ứng với alias trong native query:
 * - totalRevenue → AS totalRevenue
 * - totalCost    → AS totalCost
 * - grossProfit  → AS grossProfit
 * - totalOrders  → AS totalOrders
 */
public interface FinancialSummaryProjection {
    BigDecimal getTotalRevenue();
    BigDecimal getTotalCost();
    BigDecimal getGrossProfit();
    Long getTotalOrders();
}
