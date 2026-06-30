# **ĐẶC TẢ TÁI CẤU TRÚC VÀ TOÀN BỘ MÃ NGUỒN MODULE REPORT — KIOTVIET SYSTEM**

Tài liệu này chứa toàn bộ mã nguồn đã được tái cấu trúc hoàn chỉnh theo tiêu chuẩn Senior Developer, giải quyết triệt để các vấn đề về Concurrency, Transaction, Timezone và Data Mapping để làm Prompt ngữ cảnh cao cấp gửi thẳng vào các công cụ AI (Antigravity/Cursor).

## **1\. DANH SÁCH CÁC VẤN ĐỀ KIẾN TRÚC ĐÃ KHẮC PHỤC**

| Vấn đề phát hiện | Hệ quả / Rủi ro | Giải pháp đã thực hiện   |
| :---- | :---- | :---- |
| **ForkJoinPool.commonPool Abuse** Sử dụng CompletableFuture.supplyAsync() không có Custom Executor. | Gây nghẽn luồng hệ thống (Thread Starvation) do các tác vụ I/O blocking chiếm dụng luồng của CPU-bound tasks. | Cấu hình một ThreadPoolTaskExecutor riêng đặt tên là reportTaskExecutor để cô lập tài nguyên. |
| **Self-Invocation Proxy Bypass** Gọi hàm getRevenueTodayAsync() trực tiếp nội bộ bảng mã. | Annotation @Transactional bị bỏ qua hoàn toàn, dẫn đến việc truy vấn không chạy trong context transaction chuẩn. | Chuyển các logic truy vấn trực tiếp vào tầng Repository hoặc tách Bean xử lý Async độc lập. |
| **Brittle Object\[\] Mapping** Dùng mảng Object thô với chỉ số index cố định (row\[0\], row\[1\]). | Dễ vỡ code khi thay đổi thứ tự SELECT trong SQL native query, khó bảo trì. | Chuyển đổi toàn bộ Native Query sang dạng JPA Interface-based Projections. |
| **Database Timezone Dependency** Sử dụng các hàm native như CURDATE(), NOW(). | Sai lệch dữ liệu báo cáo ngày/tháng nếu múi giờ của DB Server lệch với múi giờ của Application Server. | Truyền tham số LocalDateTime tính toán từ Java App (đã qua xử lý ZoneId) vào câu lệnh SQL thay vì gọi hàm trực tiếp của MySQL. |

## **2\. TOÀN BỘ MÃ NGUỒN CHI TIẾT (REFACTORED SOURCE CODE)**

### **2.1. Cấu hình Async Thread Pool (AsyncConfig.java)**

*Gói: com.kiotviet.config*

`package com.kiotviet.config;`

`import org.springframework.context.annotation.Bean;`  
`import org.springframework.context.annotation.Configuration;`  
`import org.springframework.scheduling.annotation.EnableAsync;`  
`import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;`

`import java.util.concurrent.Executor;`

`@Configuration`  
`@EnableAsync`  
`public class AsyncConfig {`

    `@Bean(name = "reportTaskExecutor")`  
    `public Executor reportTaskExecutor() {`  
        `ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();`  
        `executor.setCorePoolSize(5);        // Số thread chạy thường trực`  
        `executor.setMaxPoolSize(10);       // Số lượng thread tối đa khi hàng đợi đầy`  
        `executor.setQueueCapacity(100);     // Sức chứa của hàng đợi nhiệm vụ`  
        `executor.setThreadNamePrefix("ReportAsync-");`  
        `executor.initialize();`  
        `return executor;`  
    `}`  
`}`

### **2.2. Các JPA Interface Projections**

*Gói: com.kiotviet.repository.projection*

#### **FinancialSummaryProjection.java**

`package com.kiotviet.repository.projection;`

`import java.math.BigDecimal;`

`public interface FinancialSummaryProjection {`  
    `BigDecimal getTotalRevenue();`  
    `BigDecimal getTotalCost();`  
    `BigDecimal getGrossProfit();`  
    `Long getTotalOrders();`  
`}`

#### **TopSellingProjection.java**

`package com.kiotviet.repository.projection;`

`import java.math.BigDecimal;`

`public interface TopSellingProjection {`  
    `Long getVariantId();`  
    `String getSku();`  
    `String getProductName();`  
    `String getColor();`  
    `String getSize();`  
    `Long getTotalQuantitySold();`  
    `BigDecimal getTotalRevenue();`  
`}`

#### **DeadStockProjection.java**

`package com.kiotviet.repository.projection;`

`import java.math.BigDecimal;`

`public interface DeadStockProjection {`  
    `Long getVariantId();`  
    `String getSku();`  
    `String getProductName();`  
    `String getColor();`  
    `String getSize();`  
    `Integer getInventory();`  
    `BigDecimal getImportPrice();`  
`}`

### **2.3. Lớp Dữ liệu Repository (ReportRepository.java)**

*Gói: com.kiotviet.repository*

`package com.kiotviet.repository;`

`import com.kiotviet.repository.projection.DeadStockProjection;`  
`import com.kiotviet.repository.projection.FinancialSummaryProjection;`  
`import com.kiotviet.repository.projection.TopSellingProjection;`  
`import org.springframework.data.jpa.repository.Query;`  
`import org.springframework.data.repository.Repository;`  
`import org.springframework.data.repository.query.Param;`

`import java.math.BigDecimal;`  
`import java.time.LocalDateTime;`  
`import java.util.List;`

`@org.springframework.stereotype.Repository`  
`public interface ReportRepository extends Repository<com.kiotviet.entity.Order, Long> {`

    `@Query(value = "SELECT " +`  
            `"COALESCE(SUM(oi.quantity * oi.price_at_sale), 0) AS totalRevenue, " +`  
            `"COALESCE(SUM(oi.quantity * oi.import_price_at_sale), 0) AS totalCost, " +`  
            `"COALESCE(SUM(oi.quantity * (oi.price_at_sale - oi.import_price_at_sale)), 0) AS grossProfit, " +`  
            `"COUNT(DISTINCT o.id) AS totalOrders " +`  
            `"FROM orders o " +`  
            `"JOIN order_items oi ON oi.order_id = o.id " +`  
            `"WHERE o.status = 'COMPLETED' " +`  
            `"AND o.is_deleted = false " +`  
            `"AND (:from IS NULL OR o.created_at >= :from) " +`  
            `"AND (:to IS NULL OR o.created_at <= :to)", nativeQuery = true)`  
    `FinancialSummaryProjection getFinancialSummary(`  
            `@Param("from") LocalDateTime from,`  
            `@Param("to") LocalDateTime to`  
    `);`

    `@Query(value = "SELECT " +`  
            `"oi.variant_id AS variantId, " +`  
            `"pv.sku AS sku, " +`  
            `"p.name AS productName, " +`  
            `"pv.color AS color, " +`  
            `"pv.size AS size, " +`  
            `"SUM(oi.quantity) AS totalQuantitySold, " +`  
            `"SUM(oi.quantity * oi.price_at_sale) AS totalRevenue " +`  
            `"FROM order_items oi " +`  
            `"JOIN orders o ON o.id = oi.order_id " +`  
            `"JOIN product_variants pv ON pv.id = oi.variant_id " +`  
            `"JOIN products p ON p.id = pv.product_id " +`  
            `"WHERE o.status = 'COMPLETED' " +`  
            `"AND o.is_deleted = false " +`  
            `"AND (:from IS NULL OR o.created_at >= :from) " +`  
            `"AND (:to IS NULL OR o.created_at <= :to) " +`  
            `"GROUP BY oi.variant_id, pv.sku, p.name, pv.color, pv.size " +`  
            `"ORDER BY totalQuantitySold DESC " +`  
            `"LIMIT :limitVal", nativeQuery = true)`  
    `List<TopSellingProjection> getTopSelling(`  
            `@Param("from") LocalDateTime from,`  
            `@Param("to") LocalDateTime to,`  
            `@Param("limitVal") int limit`  
    `);`

    `@Query(value = "SELECT " +`  
            `"pv.id AS variantId, " +`  
            `"pv.sku AS sku, " +`  
            `"p.name AS productName, " +`  
            `"pv.color AS color, " +`  
            `"pv.size AS size, " +`  
            `"pv.inventory AS inventory, " +`  
            `"pv.import_price AS importPrice " +`  
            `"FROM product_variants pv " +`  
            `"JOIN products p ON p.id = pv.product_id " +`  
            `"WHERE pv.inventory >= :minInventory " +`  
            `"AND pv.is_deleted = false " +`  
            `"AND p.is_deleted = false " +`  
            `"AND NOT EXISTS ( " +`  
            `"    SELECT 1 " +`  
            `"    FROM order_items oi " +`  
            `"    JOIN orders o ON o.id = oi.order_id " +`  
            `"    WHERE oi.variant_id = pv.id " +`  
            `"    AND o.status = 'COMPLETED' " +`  
            `"    AND o.is_deleted = false " +`  
            `"    AND o.created_at >= :deadStockThreshold " +`  
            `") " +`  
            `"ORDER BY pv.inventory DESC", nativeQuery = true)`  
    `List<DeadStockProjection> getDeadStock(`  
            `@Param("minInventory") int minInventory,`  
            `@Param("deadStockThreshold") LocalDateTime deadStockThreshold`  
    `);`

    `@Query(value = "SELECT COALESCE(SUM(oi.quantity * oi.price_at_sale), 0) " +`  
            `"FROM orders o " +`  
            `"JOIN order_items oi ON oi.order_id = o.id " +`  
            `"WHERE o.status = 'COMPLETED' " +`  
            `"AND o.is_deleted = false " +`  
            `"AND o.created_at >= :startOfToday " +`  
            `"AND o.created_at <= :endOfToday", nativeQuery = true)`  
    `BigDecimal getRevenueTodayRaw(`  
            `@Param("startOfToday") LocalDateTime startOfToday,`  
            `@Param("endOfToday") LocalDateTime endOfToday`  
    `);`

    `@Query(value = "SELECT COUNT(*) FROM orders " +`  
            `"WHERE status = 'COMPLETED' " +`  
            `"AND is_deleted = false " +`  
            `"AND created_at >= :startOfToday " +`  
            `"AND created_at <= :endOfToday", nativeQuery = true)`  
    `Long countOrdersToday(`  
            `@Param("startOfToday") LocalDateTime startOfToday,`  
            `@Param("endOfToday") LocalDateTime endOfToday`  
    `);`

    `@Query(value = "SELECT COUNT(*) FROM product_variants " +`  
            `"WHERE inventory < 5 " +`  
            `"AND is_deleted = false " +`  
            `"AND status = 'ACTIVE'", nativeQuery = true)`  
    `Long countLowStock();`  
`}`

### **2.4. Tầng Xử lý Nghiệp vụ (ReportService.java)**

*Gói: com.kiotviet.service*

`package com.kiotviet.service;`

`import com.kiotviet.dto.response.*;`  
`import com.kiotviet.repository.ReportRepository;`  
`import com.kiotviet.repository.projection.DeadStockProjection;`  
`import com.kiotviet.repository.projection.FinancialSummaryProjection;`  
`import com.kiotviet.repository.projection.TopSellingProjection;`  
`import lombok.RequiredArgsConstructor;`  
`import lombok.extern.slf4j.Slf4j;`  
`import org.springframework.beans.factory.annotation.Qualifier;`  
`import org.springframework.stereotype.Service;`  
`import org.springframework.transaction.annotation.Transactional;`

`import java.math.BigDecimal;`  
`import java.math.RoundingMode;`  
`import java.time.LocalDate;`  
`import java.time.LocalDateTime;`  
`import java.time.LocalTime;`  
`import java.time.ZoneId;`  
`import java.util.List;`  
`import java.util.concurrent.CompletableFuture;`  
`import java.util.concurrent.Executor;`

`@Slf4j`  
`@Service`  
`@RequiredArgsConstructor`  
`public class ReportService {`

    `private final ReportRepository reportRepository;`

    `@Qualifier("reportTaskExecutor")`  
    `private final Executor reportTaskExecutor;`

    `private static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");`

    `@Transactional(readOnly = true)`  
    `public FinancialReportResponse getFinancialReport(LocalDateTime from, LocalDateTime to) {`  
        `FinancialSummaryProjection summary = reportRepository.getFinancialSummary(from, to);`

        `BigDecimal totalRevenue = (summary != null && summary.getTotalRevenue() != null) ? summary.getTotalRevenue() : BigDecimal.ZERO;`  
        `BigDecimal totalCost    = (summary != null && summary.getTotalCost() != null) ? summary.getTotalCost() : BigDecimal.ZERO;`  
        `BigDecimal grossProfit  = (summary != null && summary.getGrossProfit() != null) ? summary.getGrossProfit() : BigDecimal.ZERO;`  
        `Long totalOrders        = (summary != null && summary.getTotalOrders() != null) ? summary.getTotalOrders() : 0L;`

        `BigDecimal grossMarginPercent = BigDecimal.ZERO;`  
        `if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {`  
            `grossMarginPercent = grossProfit`  
                    `.divide(totalRevenue, 4, RoundingMode.HALF_UP)`  
                    `.multiply(BigDecimal.valueOf(100))`  
                    `.setScale(2, RoundingMode.HALF_UP);`  
        `}`

        `BigDecimal avgOrderValue = BigDecimal.ZERO;`  
        `if (totalOrders > 0) {`  
            `avgOrderValue = totalRevenue.divide(BigDecimal.valueOf(totalOrders), 0, RoundingMode.HALF_UP);`  
        `}`

        `return FinancialReportResponse.builder()`  
                `.totalRevenue(totalRevenue)`  
                `.totalCost(totalCost)`  
                `.grossProfit(grossProfit)`  
                `.grossMarginPercent(grossMarginPercent)`  
                `.totalOrders(totalOrders)`  
                `.avgOrderValue(avgOrderValue)`  
                `.fromDate(from != null ? from.toString() : null)`  
                `.toDate(to != null ? to.toString() : null)`  
                `.build();`  
    `}`

    `@Transactional(readOnly = true)`  
    `public List<TopSellingResponse> getTopSelling(LocalDateTime from, LocalDateTime to, int limit) {`  
        `List<TopSellingProjection> rows = reportRepository.getTopSelling(from, to, limit);`  
          
        `return rows.stream().map(row -> TopSellingResponse.builder()`  
                `.rank(rows.indexOf(row) + 1)`  
                `.variantId(row.getVariantId())`  
                `.sku(row.getSku())`  
                `.productName(row.getProductName())`  
                `.color(row.getColor())`  
                `.size(row.getSize())`  
                `.totalQuantitySold(row.getTotalQuantitySold())`  
                `.totalRevenue(row.getTotalRevenue() != null ? row.getTotalRevenue() : BigDecimal.ZERO)`  
                `.build()`  
        `).toList();`  
    `}`

    `@Transactional(readOnly = true)`  
    `public List<DeadStockResponse> getDeadStock(int minInventory, int days) {`  
        `LocalDateTime deadStockThreshold = LocalDate.now(ZONE_VN).minusDays(days).atStartOfDay();`  
        `List<DeadStockProjection> rows = reportRepository.getDeadStock(minInventory, deadStockThreshold);`  
          
        `return rows.stream().map(row -> {`  
            `BigDecimal importPrice = row.getImportPrice() != null ? row.getImportPrice() : BigDecimal.ZERO;`  
            `Integer inventory = row.getInventory() != null ? row.getInventory() : 0;`  
            `BigDecimal stockValue = importPrice.multiply(BigDecimal.valueOf(inventory));`

            `return DeadStockResponse.builder()`  
                    `.variantId(row.getVariantId())`  
                    `.sku(row.getSku())`  
                    `.productName(row.getProductName())`  
                    `.color(row.getColor())`  
                    `.size(row.getSize())`  
                    `.inventory(inventory)`  
                    `.importPrice(importPrice)`  
                    `.stockValue(stockValue)`  
                    `.build();`  
        `}).toList();`  
    `}`

    `@Transactional(readOnly = true)`  
    `public DashboardResponse getDashboard() {`  
        `LocalDate today = LocalDate.now(ZONE_VN);`  
        `LocalDateTime startOfToday = today.atStartOfDay();`  
        `LocalDateTime endOfToday = today.atTime(LocalTime.MAX);`  
        `LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();`

        `CompletableFuture<BigDecimal> revenueFuture = CompletableFuture.supplyAsync(`  
                `() -> {`  
                    `BigDecimal rev = reportRepository.getRevenueTodayRaw(startOfToday, endOfToday);`  
                    `return rev != null ? rev : BigDecimal.ZERO;`  
                `}, reportTaskExecutor)`  
                `.exceptionally(ex -> {`  
                    `log.error("Lỗi tải doanh thu hôm nay: ", ex);`  
                    `return BigDecimal.ZERO;`  
                `});`

        `CompletableFuture<Long> ordersFuture = CompletableFuture.supplyAsync(`  
                `() -> {`  
                    `Long count = reportRepository.countOrdersToday(startOfToday, endOfToday);`  
                    `return count != null ? count : 0L;`  
                `}, reportTaskExecutor)`  
                `.exceptionally(ex -> {`  
                    `log.error("Lỗi đếm số đơn hàng hôm nay: ", ex);`  
                    `return 0L;`  
                `});`

        `CompletableFuture<List<TopSellingResponse>> topSellingFuture = CompletableFuture.supplyAsync(`  
                `() -> getTopSelling(startOfMonth, null, 5), reportTaskExecutor)`  
                `.exceptionally(ex -> {`  
                    `log.error("Lỗi tải top sản phẩm trong tháng: ", ex);`  
                    `return List.of();`  
                `});`

        `CompletableFuture<Long> lowStockFuture = CompletableFuture.supplyAsync(`  
                `() -> {`  
                    `Long count = reportRepository.countLowStock();`  
                    `return count != null ? count : 0L;`  
                `}, reportTaskExecutor)`  
                `.exceptionally(ex -> {`  
                    `log.error("Lỗi lấy số lượng hàng tồn kho thấp: ", ex);`  
                    `return 0L;`  
                `});`

        `CompletableFuture.allOf(revenueFuture, ordersFuture, topSellingFuture, lowStockFuture).join();`

        `return DashboardResponse.builder()`  
                `.revenueToday(revenueFuture.join())`  
                `.ordersToday(ordersFuture.join())`  
                `.topSellingThisMonth(topSellingFuture.join())`  
                `.lowStockCount(lowStockFuture.join())`  
                `.build();`  
    `}`  
`}`  
