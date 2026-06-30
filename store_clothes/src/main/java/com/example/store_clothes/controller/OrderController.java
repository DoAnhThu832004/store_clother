package com.example.store_clothes.controller;

import com.example.store_clothes.dto.request.CheckoutRequest;
import com.example.store_clothes.dto.response.ApiResponse;
import com.example.store_clothes.dto.response.OrderResponse;
import com.example.store_clothes.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
public class OrderController {

    private final OrderService orderService;

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
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @Valid @RequestBody CheckoutRequest request) {

        log.info("API checkout called: {} items, paidAmount={}",
                request.getItems().size(), request.getPaidAmount());

        OrderResponse response = orderService.checkout(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }
}
