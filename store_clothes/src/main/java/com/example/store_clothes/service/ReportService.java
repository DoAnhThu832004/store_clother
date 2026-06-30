package com.example.store_clothes.service;

import com.example.store_clothes.dto.response.DashboardResponse;
import com.example.store_clothes.dto.response.DeadStockResponse;
import com.example.store_clothes.dto.response.FinancialReportResponse;
import com.example.store_clothes.dto.response.TopSellingResponse;
import com.example.store_clothes.repository.ReportRepository;
import com.example.store_clothes.repository.projection.DeadStockProjection;
import com.example.store_clothes.repository.projection.FinancialSummaryProjection;
import com.example.store_clothes.repository.projection.TopSellingProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * ReportService — Tầng xử lý nghiệp vụ báo cáo & thống kê.
 *
 * =====================================================================
 * GIẢI QUYẾT 4 VẤN ĐỀ KIẾN TRÚC THEO TÀI LIỆU ĐẶC TẢ V2:
 * =====================================================================
 *
 * [1] ForkJoinPool.commonPool Abuse → GIẢI QUYẾT:
 *     - getDashboard() dùng reportTaskExecutor (ThreadPoolTaskExecutor
 *       riêng biệt, 5-10 threads) thay vì ForkJoinPool.commonPool.
 *     - I/O blocking queries không chiếm thread của CPU-bound tasks.
 *
 * [2] Self-Invocation Proxy Bypass → GIẢI QUYẾT:
 *     - getDashboard() không tự gọi getTopSelling() qua this.xxx()
 *       mà gọi trực tiếp reportRepository để lấy data rồi map.
 *     - @Transactional không bị bỏ qua do proxy bypass.
 *
 * [3] Brittle Object[] Mapping → GIẢI QUYẾT:
 *     - Toàn bộ query dùng JPA Interface-based Projection (FinancialSummaryProjection,
 *       TopSellingProjection, DeadStockProjection).
 *     - Ánh xạ theo tên alias → không vỡ code khi thay đổi thứ tự SELECT.
 *
 * [4] Database Timezone Dependency → GIẢI QUYẾT:
 *     - Mọi mốc thời gian (startOfToday, endOfToday, deadStockThreshold)
 *       được tính từ Java với ZoneId.of("Asia/Ho_Chi_Minh").
 *     - Không dùng CURDATE(), NOW() của MySQL → không phụ thuộc timezone DB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    /**
     * Executor riêng cho tác vụ Report — tránh dùng ForkJoinPool.commonPool.
     * Bean được cấu hình trong AsyncConfig với tên "reportTaskExecutor".
     */
    @Qualifier("reportTaskExecutor")
    private final Executor reportTaskExecutor;

    /** Múi giờ Việt Nam — dùng thống nhất cho mọi tính toán ngày/giờ. */
    private static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");

    // =========================================================================
    // API 1: BÁO CÁO TÀI CHÍNH
    // =========================================================================

    /**
     * Báo cáo tài chính tổng hợp theo khoảng thời gian tùy chỉnh.
     *
     * Tính toán thêm trong Java (không đưa vào SQL):
     * - grossMarginPercent: Tránh chia cho 0 (totalRevenue = 0).
     * - avgOrderValue: Tránh chia cho 0 (totalOrders = 0).
     *
     * @param from Từ thời điểm (null = không giới hạn)
     * @param to   Đến thời điểm (null = không giới hạn)
     * @return FinancialReportResponse với đầy đủ metrics tài chính
     */
    @Transactional(readOnly = true)
    public FinancialReportResponse getFinancialReport(LocalDateTime from, LocalDateTime to) {
        FinancialSummaryProjection summary = reportRepository.getFinancialSummary(from, to);

        // Null-safe: Query có thể trả về null nếu không có đơn nào match
        BigDecimal totalRevenue = (summary != null && summary.getTotalRevenue() != null)
                ? summary.getTotalRevenue() : BigDecimal.ZERO;
        BigDecimal totalCost = (summary != null && summary.getTotalCost() != null)
                ? summary.getTotalCost() : BigDecimal.ZERO;
        BigDecimal grossProfit = (summary != null && summary.getGrossProfit() != null)
                ? summary.getGrossProfit() : BigDecimal.ZERO;
        Long totalOrders = (summary != null && summary.getTotalOrders() != null)
                ? summary.getTotalOrders() : 0L;

        // Biên lợi nhuận gộp (%) — tránh ArithmeticException khi totalRevenue = 0
        BigDecimal grossMarginPercent = BigDecimal.ZERO;
        if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            grossMarginPercent = grossProfit
                    .divide(totalRevenue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // Giá trị trung bình mỗi đơn — tránh ArithmeticException khi totalOrders = 0
        BigDecimal avgOrderValue = BigDecimal.ZERO;
        if (totalOrders > 0) {
            avgOrderValue = totalRevenue.divide(BigDecimal.valueOf(totalOrders), 0, RoundingMode.HALF_UP);
        }

        return FinancialReportResponse.builder()
                .totalRevenue(totalRevenue)
                .totalCost(totalCost)
                .grossProfit(grossProfit)
                .grossMarginPercent(grossMarginPercent)
                .totalOrders(totalOrders)
                .avgOrderValue(avgOrderValue)
                .fromDate(from != null ? from.toString() : null)
                .toDate(to != null ? to.toString() : null)
                .build();
    }

    // =========================================================================
    // API 2: TOP SẢN PHẨM BÁN CHẠY
    // =========================================================================

    /**
     * Danh sách top N sản phẩm bán chạy nhất theo khoảng thời gian.
     *
     * rank được gán tuần tự theo thứ tự list trả về từ DB (đã ORDER BY DESC).
     *
     * NOTE: Dùng indexOf() có thể O(n²) với list lớn, nhưng báo cáo top thường
     * giới hạn <= 50 records nên không ảnh hưởng performance thực tế.
     *
     * @param from  Từ thời điểm
     * @param to    Đến thời điểm
     * @param limit Số lượng sản phẩm muốn hiển thị
     * @return Danh sách TopSellingResponse với rank từ 1 đến N
     */
    @Transactional(readOnly = true)
    public List<TopSellingResponse> getTopSelling(LocalDateTime from, LocalDateTime to, int limit) {
        List<TopSellingProjection> rows = reportRepository.getTopSelling(from, to, limit);

        return rows.stream().map(row -> TopSellingResponse.builder()
                .rank(rows.indexOf(row) + 1)
                .variantId(row.getVariantId())
                .sku(row.getSku())
                .productName(row.getProductName())
                .color(row.getColor())
                .size(row.getSize())
                .totalQuantitySold(row.getTotalQuantitySold())
                .totalRevenue(row.getTotalRevenue() != null ? row.getTotalRevenue() : BigDecimal.ZERO)
                .build()
        ).toList();
    }

    // =========================================================================
    // API 3: HÀNG TỒN CHẬM (DEAD STOCK)
    // =========================================================================

    /**
     * Danh sách hàng tồn chậm — sản phẩm còn hàng nhưng không bán trong X ngày.
     *
     * deadStockThreshold tính từ Java với ZONE_VN:
     * → Kết quả nhất quán bất kể timezone của MySQL Server.
     *
     * stockValue = importPrice × inventory: Tính trong Java thay vì SQL
     * để tránh làm phức tạp câu query.
     *
     * @param minInventory Ngưỡng tồn kho tối thiểu để tính là "còn hàng"
     * @param days         Số ngày không bán để tính là "hàng tồn chậm"
     * @return Danh sách DeadStockResponse sắp xếp theo tồn kho giảm dần
     */
    @Transactional(readOnly = true)
    public List<DeadStockResponse> getDeadStock(int minInventory, int days) {
        // Timezone-safe: tính từ Java, không dùng DATE_SUB(NOW(), INTERVAL X DAY)
        LocalDateTime deadStockThreshold = LocalDate.now(ZONE_VN)
                .minusDays(days)
                .atStartOfDay();

        List<DeadStockProjection> rows = reportRepository.getDeadStock(minInventory, deadStockThreshold);

        return rows.stream().map(row -> {
            BigDecimal importPrice = row.getImportPrice() != null ? row.getImportPrice() : BigDecimal.ZERO;
            Integer inventory = row.getInventory() != null ? row.getInventory() : 0;
            BigDecimal stockValue = importPrice.multiply(BigDecimal.valueOf(inventory));

            return DeadStockResponse.builder()
                    .variantId(row.getVariantId())
                    .sku(row.getSku())
                    .productName(row.getProductName())
                    .color(row.getColor())
                    .size(row.getSize())
                    .inventory(inventory)
                    .importPrice(importPrice)
                    .stockValue(stockValue)
                    .build();
        }).toList();
    }

    // =========================================================================
    // API 4: DASHBOARD — 4 QUERY SONG SONG
    // =========================================================================

    /**
     * Tổng hợp dữ liệu Dashboard bằng cách chạy 4 query ĐỒNG THỜI.
     *
     * GIẢI THÍCH THIẾT KẾ CONCURRENCY:
     *
     * - 4 query độc lập nhau → có thể chạy song song.
     * - Dùng CompletableFuture với reportTaskExecutor (không dùng ForkJoinPool.commonPool).
     * - .exceptionally() trên từng Future: nếu 1 query lỗi, dashboard vẫn
     *   trả về data từ 3 query còn lại (Graceful Degradation).
     * - CompletableFuture.allOf(...).join() chờ tất cả hoàn tất.
     *
     * LÝ DO KHÔNG GỌI getTopSelling() NỘI BỘ (Self-Invocation Fix):
     * - Thay vì gọi this.getTopSelling() (bypass proxy → @Transactional mất hiệu lực),
     *   ta gọi trực tiếp reportRepository.getTopSelling() và map trong lambda.
     *
     * @return DashboardResponse với 4 metrics cốt lõi
     */
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        // Timezone-safe: tính từ Java với ZONE_VN
        LocalDate today = LocalDate.now(ZONE_VN);
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime endOfToday = today.atTime(LocalTime.MAX);
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();

        // --- QUERY 1: Doanh thu hôm nay ---
        CompletableFuture<BigDecimal> revenueFuture = CompletableFuture.supplyAsync(
                () -> {
                    BigDecimal rev = reportRepository.getRevenueTodayRaw(startOfToday, endOfToday);
                    return rev != null ? rev : BigDecimal.ZERO;
                }, reportTaskExecutor)
                .exceptionally(ex -> {
                    log.error("Lỗi tải doanh thu hôm nay: ", ex);
                    return BigDecimal.ZERO;
                });

        // --- QUERY 2: Số đơn hàng hôm nay ---
        CompletableFuture<Long> ordersFuture = CompletableFuture.supplyAsync(
                () -> {
                    Long count = reportRepository.countOrdersToday(startOfToday, endOfToday);
                    return count != null ? count : 0L;
                }, reportTaskExecutor)
                .exceptionally(ex -> {
                    log.error("Lỗi đếm số đơn hàng hôm nay: ", ex);
                    return 0L;
                });

        // --- QUERY 3: Top 5 sản phẩm bán chạy trong tháng ---
        // Gọi trực tiếp repository.getTopSelling() thay vì this.getTopSelling()
        // → tránh self-invocation bypass @Transactional proxy
        CompletableFuture<List<TopSellingResponse>> topSellingFuture = CompletableFuture.supplyAsync(
                () -> {
                    List<TopSellingProjection> rows = reportRepository.getTopSelling(startOfMonth, null, 5);
                    return rows.stream().map(row -> TopSellingResponse.builder()
                            .rank(rows.indexOf(row) + 1)
                            .variantId(row.getVariantId())
                            .sku(row.getSku())
                            .productName(row.getProductName())
                            .color(row.getColor())
                            .size(row.getSize())
                            .totalQuantitySold(row.getTotalQuantitySold())
                            .totalRevenue(row.getTotalRevenue() != null ? row.getTotalRevenue() : BigDecimal.ZERO)
                            .build()
                    ).toList();
                }, reportTaskExecutor)
                .exceptionally(ex -> {
                    log.error("Lỗi tải top sản phẩm trong tháng: ", ex);
                    return List.of();
                });

        // --- QUERY 4: Số variant sắp hết hàng ---
        CompletableFuture<Long> lowStockFuture = CompletableFuture.supplyAsync(
                () -> {
                    Long count = reportRepository.countLowStock();
                    return count != null ? count : 0L;
                }, reportTaskExecutor)
                .exceptionally(ex -> {
                    log.error("Lỗi lấy số lượng hàng tồn kho thấp: ", ex);
                    return 0L;
                });

        // Chờ tất cả 4 query hoàn thành
        CompletableFuture.allOf(revenueFuture, ordersFuture, topSellingFuture, lowStockFuture).join();

        return DashboardResponse.builder()
                .revenueToday(revenueFuture.join())
                .ordersToday(ordersFuture.join())
                .topSellingThisMonth(topSellingFuture.join())
                .lowStockCount(lowStockFuture.join())
                .build();
    }
}
