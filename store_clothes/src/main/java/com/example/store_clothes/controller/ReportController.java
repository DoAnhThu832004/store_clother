package com.example.store_clothes.controller;

import com.example.store_clothes.dto.response.ApiResponse;
import com.example.store_clothes.dto.response.DashboardResponse;
import com.example.store_clothes.dto.response.DeadStockResponse;
import com.example.store_clothes.dto.response.FinancialReportResponse;
import com.example.store_clothes.dto.response.TopSellingResponse;
import com.example.store_clothes.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ReportController — Controller cực kỳ mỏng cho module báo cáo.
 *
 * Base URL: /api/v1/reports
 *
 * Nguyên tắc:
 * - Không có bất kỳ business logic nào ở đây.
 * - Chỉ parse request params → gọi Service → trả response.
 * - Mọi exception sẽ được GlobalExceptionHandler bắt và format chuẩn.
 *
 * Danh sách endpoints:
 * - GET /api/v1/reports/dashboard          → Dashboard tổng quan
 * - GET /api/v1/reports/financial          → Báo cáo tài chính theo kỳ
 * - GET /api/v1/reports/top-selling        → Top sản phẩm bán chạy
 * - GET /api/v1/reports/dead-stock         → Hàng tồn chậm
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // =========================================================================
    // ENDPOINT 1: DASHBOARD
    // =========================================================================

    /**
     * GET /api/v1/reports/dashboard
     *
     * Lấy dữ liệu tổng quan cho màn hình Dashboard.
     * Bao gồm: doanh thu hôm nay, số đơn hôm nay, top 5 tháng này, cảnh báo tồn kho.
     *
     * 4 query chạy song song → thời gian response ≈ query chậm nhất (không cộng dồn).
     *
     * @return 200 OK + DashboardResponse
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        log.info("API GET /reports/dashboard called");

        DashboardResponse response = reportService.getDashboard();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // ENDPOINT 2: BÁO CÁO TÀI CHÍNH
    // =========================================================================

    /**
     * GET /api/v1/reports/financial
     *
     * Báo cáo tài chính tổng hợp theo khoảng thời gian tùy chỉnh.
     *
     * Query params:
     * - from: ISO-8601 datetime (ví dụ: 2026-01-01T00:00:00). Null = không giới hạn.
     * - to:   ISO-8601 datetime (ví dụ: 2026-06-30T23:59:59). Null = không giới hạn.
     *
     * @param from Từ thời điểm (optional)
     * @param to   Đến thời điểm (optional)
     * @return 200 OK + FinancialReportResponse
     */
    @GetMapping("/financial")
    public ResponseEntity<ApiResponse<FinancialReportResponse>> getFinancialReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        log.info("API GET /reports/financial called: from={}, to={}", from, to);

        FinancialReportResponse response = reportService.getFinancialReport(from, to);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // ENDPOINT 3: TOP SẢN PHẨM BÁN CHẠY
    // =========================================================================

    /**
     * GET /api/v1/reports/top-selling
     *
     * Danh sách top N sản phẩm bán chạy nhất trong kỳ.
     *
     * Query params:
     * - from:  Từ thời điểm (optional)
     * - to:    Đến thời điểm (optional)
     * - limit: Số lượng sản phẩm muốn lấy (default=10, max nên giới hạn ở frontend)
     *
     * @param from  Từ thời điểm (optional)
     * @param to    Đến thời điểm (optional)
     * @param limit Số sản phẩm hiển thị (default = 10)
     * @return 200 OK + List<TopSellingResponse>
     */
    @GetMapping("/top-selling")
    public ResponseEntity<ApiResponse<List<TopSellingResponse>>> getTopSelling(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("API GET /reports/top-selling called: from={}, to={}, limit={}", from, to, limit);

        List<TopSellingResponse> response = reportService.getTopSelling(from, to, limit);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // ENDPOINT 4: HÀNG TỒN CHẬM (DEAD STOCK)
    // =========================================================================

    /**
     * GET /api/v1/reports/dead-stock
     *
     * Danh sách variant có tồn kho cao nhưng không bán được trong X ngày.
     *
     * Query params:
     * - minInventory: Ngưỡng tồn kho tối thiểu (default=1, chỉ lấy variant còn hàng)
     * - days:         Số ngày không bán để tính là dead stock (default=90)
     *
     * Ví dụ: /api/v1/reports/dead-stock?minInventory=5&days=60
     * → Lấy variant còn >= 5 cái không bán được trong 60 ngày gần nhất.
     *
     * @param minInventory Ngưỡng tồn kho tối thiểu
     * @param days         Số ngày không có giao dịch
     * @return 200 OK + List<DeadStockResponse>
     */
    @GetMapping("/dead-stock")
    public ResponseEntity<ApiResponse<List<DeadStockResponse>>> getDeadStock(
            @RequestParam(defaultValue = "1") int minInventory,
            @RequestParam(defaultValue = "90") int days) {

        log.info("API GET /reports/dead-stock called: minInventory={}, days={}", minInventory, days);

        List<DeadStockResponse> response = reportService.getDeadStock(minInventory, days);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
