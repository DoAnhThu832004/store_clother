package com.example.store_clothes.controller;

import com.example.store_clothes.dto.request.CheckoutRequest;
import com.example.store_clothes.dto.response.ApiResponse;
import com.example.store_clothes.dto.response.OrderResponse;
import com.example.store_clothes.enums.OrderStatus;
import com.example.store_clothes.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.store_clothes.entity.User;
import com.example.store_clothes.idempotency.Idempotent;
import com.example.store_clothes.idempotency.IdempotentScope;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * OrderController - Controller xử lý các API liên quan đến hóa đơn bán hàng POS.
 *
 * Base URL: /api/v1/orders
 *
 * Controller layer CỰC KỲ mỏng — chỉ nhận request, delegate cho Service,
 * và trả response. Không có bất kỳ business logic nào ở đây.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "API quản lý hóa đơn bán hàng POS")
public class OrderController {

    private final OrderService orderService;

    // =========================================================================
    // CHECKOUT — POST /api/v1/orders/checkout
    // =========================================================================

    /**
     * POST /api/v1/orders/checkout
     *
     * Thanh toán hóa đơn POS — endpoint chính của luồng bán hàng.
     *
     * @Valid: Kích hoạt Bean Validation trên CheckoutRequest.
     *         Nếu fail → Spring tự ném MethodArgumentNotValidException
     *         → GlobalExceptionHandler bắt → HTTP 400 với field errors.
     *
     * HTTP 201 Created: Phù hợp ngữ nghĩa REST — hóa đơn được TẠO MỚI.
     *
     * @param request Dữ liệu giỏ hàng từ thu ngân
     * @return 201 Created + OrderResponse với mã hóa đơn và tiền thừa
     */
    @Idempotent(ttlSeconds = 30, scope = IdempotentScope.POS)
    @PostMapping("/checkout")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'CASHIER')")
    @Operation(
        summary = "Thanh toán hóa đơn POS",
        description = "Tạo hóa đơn mới, trừ tồn kho, tích điểm cho khách hàng. Hỗ trợ idempotency 30s."
    )
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @Valid @RequestBody CheckoutRequest request) {

        log.info("API checkout called: {} items, paidAmount={}",
                request.getItems().size(), request.getPaidAmount());

        OrderResponse response = orderService.checkout(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    // =========================================================================
    // ORD-01 — HỦY HÓA ĐƠN: POST /api/v1/orders/{id}/cancel
    // =========================================================================

    /**
     * POST /api/v1/orders/{id}/cancel
     *
     * Hủy hóa đơn đã COMPLETED và hoàn toàn bộ tồn kho.
     * Chỉ OWNER và MANAGER được phép hủy đơn.
     *
     * HTTP 200 OK (không phải 204 No Content): Trả về message xác nhận.
     * HTTP 404 Not Found: Hóa đơn không tồn tại.
     * HTTP 400 Bad Request: Hóa đơn đã bị hủy trước đó (REFUNDED).
     * HTTP 409 Conflict: Timeout lock DB khi có 2 request cancel cùng lúc.
     *
     * @param id      UUID hóa đơn cần hủy
     * @param currentUser User đang đăng nhập (inject từ SecurityContext)
     * @return 200 OK + message xác nhận
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @Operation(
        summary = "ORD-01: Hủy hóa đơn + hoàn kho",
        description = """
            Hủy hóa đơn đã COMPLETED. Tự động hoàn toàn bộ tồn kho.
            Trừ loyaltyPoints khách hàng nếu có. Ghi AuditLog async.
            Chỉ OWNER và MANAGER được phép thực hiện.
            Xử lý deadlock-safe bằng cách sort variantId ASC trước khi lock.
            """
    )
    public ResponseEntity<ApiResponse<Void>> cancelOrder(
            @Parameter(description = "UUID hóa đơn cần hủy", required = true)
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {

        log.info("API cancelOrder called: orderId={}, by={}", id, currentUser.getUsername());

        orderService.cancelOrder(id, currentUser.getId(), currentUser.getUsername());

        return ResponseEntity.ok(ApiResponse.success("Hóa đơn đã được hủy và hoàn kho thành công."));
    }

    // =========================================================================
    // ORD-02 — DANH SÁCH HÓA ĐƠN: GET /api/v1/orders
    // =========================================================================

    /**
     * GET /api/v1/orders?page=0&size=20&status=COMPLETED&customerId=&from=&to=
     *
     * Lấy danh sách hóa đơn với bộ lọc đa điều kiện + phân trang.
     *
     * @param status     Filter theo trạng thái (COMPLETED/REFUNDED/PENDING, null = tất cả)
     * @param customerId Filter theo ID khách hàng (null = tất cả)
     * @param from       Từ ngày tạo (ISO DateTime, null = không filter)
     * @param to         Đến ngày tạo (ISO DateTime, null = không filter)
     * @param page       Số trang (bắt đầu từ 0)
     * @param size       Số phần tử/trang (mặc định 20)
     * @return 200 OK + Page<OrderResponse>
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'CASHIER')")
    @Operation(
        summary = "ORD-02: Danh sách hóa đơn",
        description = """
            Lấy danh sách hóa đơn với filter đa điều kiện.
            Param status: COMPLETED | REFUNDED | PENDING (null = tất cả).
            Phân trang qua page, size. Sắp xếp mới nhất trước.
            """
    )
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getOrders(
            @Parameter(description = "Lọc theo trạng thái hóa đơn")
            @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "Lọc theo ID khách hàng")
            @RequestParam(required = false) Long customerId,
            @Parameter(description = "Từ ngày tạo (ISO format: yyyy-MM-ddTHH:mm:ss)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Đến ngày tạo (ISO format: yyyy-MM-ddTHH:mm:ss)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<OrderResponse> result = orderService.getOrders(status, customerId, from, to, pageable);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
