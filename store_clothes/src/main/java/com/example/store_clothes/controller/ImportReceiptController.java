package com.example.store_clothes.controller;

import com.example.store_clothes.dto.request.CreateImportReceiptRequest;
import com.example.store_clothes.dto.response.ApiResponse;
import com.example.store_clothes.dto.response.ImportReceiptResponse;
import com.example.store_clothes.service.ImportReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ImportReceiptController - Tầng kiểm soát đầu vào cho API Nhập Hàng.
 *
 * Base URL: /clothes/api/v1/imports
 *
 * Nguyên tắc Controller:
 * - Chỉ nhận request, validate đầu vào (@Valid), gọi Service, wrap response.
 * - KHÔNG chứa logic nghiệp vụ - toàn bộ logic nằm ở ImportReceiptService.
 * - KHÔNG xử lý exception - để GlobalExceptionHandler xử lý tập trung.
 * - Mọi response phải được wrap bằng ApiResponse<T>.
 *
 * API Endpoints:
 * - POST   /api/v1/imports              → Tạo phiếu nhập nháp (DRAFT)
 * - POST   /api/v1/imports/{id}/complete → Hoàn thành phiếu (DRAFT → COMPLETED)
 * - POST   /api/v1/imports/{id}/cancel   → Hủy phiếu (DRAFT → CANCELLED)
 * - GET    /api/v1/imports/{id}          → Xem chi tiết phiếu
 * - GET    /api/v1/imports/supplier/{supplierId} → Danh sách phiếu của NCC
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
public class ImportReceiptController {

    private final ImportReceiptService importReceiptService;

    /**
     * Tạo mới phiếu nhập hàng ở trạng thái DRAFT.
     *
     * @Valid: Kích hoạt validation theo annotation trên CreateImportReceiptRequest.
     *         Nếu vi phạm → MethodArgumentNotValidException → GlobalExceptionHandler → 400.
     *
     * POST /api/v1/imports
     *
     * @param request Thông tin phiếu nhập từ client
     * @return 201 Created + Response DTO phiếu vừa tạo
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ImportReceiptResponse>> createDraftReceipt(
            @RequestBody @Valid CreateImportReceiptRequest request) {

        log.debug("API: Create draft import receipt, supplierId={}", request.getSupplierId());
        ImportReceiptResponse response = importReceiptService.createDraftReceipt(request);
        return ResponseEntity
                .status(201)
                .body(ApiResponse.created(response));
    }

    /**
     * Hoàn thành phiếu nhập: cộng tồn kho, ghi thẻ kho, cập nhật công nợ NCC.
     *
     * POST /api/v1/imports/{id}/complete
     *
     * @param id ID của phiếu nhập cần hoàn thành
     * @return 200 OK + Response DTO phiếu đã hoàn thành
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<ImportReceiptResponse>> completeReceipt(
            @PathVariable Long id) {

        log.debug("API: Complete import receipt, receiptId={}", id);
        ImportReceiptResponse response = importReceiptService.completeReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Hủy phiếu nhập đang ở trạng thái DRAFT.
     *
     * POST /api/v1/imports/{id}/cancel
     *
     * @param id ID của phiếu nhập cần hủy
     * @return 200 OK + Response DTO phiếu đã hủy
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<ImportReceiptResponse>> cancelReceipt(
            @PathVariable Long id) {

        log.debug("API: Cancel import receipt, receiptId={}", id);
        ImportReceiptResponse response = importReceiptService.cancelReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Lấy chi tiết một phiếu nhập theo ID.
     *
     * GET /api/v1/imports/{id}
     *
     * @param id ID của phiếu nhập
     * @return 200 OK + Response DTO phiếu kèm danh sách chi tiết
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ImportReceiptResponse>> getReceiptById(
            @PathVariable Long id) {

        log.debug("API: Get import receipt detail, receiptId={}", id);
        ImportReceiptResponse response = importReceiptService.getReceiptById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Lấy danh sách phiếu nhập theo nhà cung cấp.
     * Sắp xếp từ mới nhất đến cũ nhất.
     *
     * GET /api/v1/imports/supplier/{supplierId}
     *
     * @param supplierId ID của nhà cung cấp
     * @return 200 OK + Danh sách phiếu nhập
     */
    @GetMapping("/supplier/{supplierId}")
    public ResponseEntity<ApiResponse<List<ImportReceiptResponse>>> getReceiptsBySupplier(
            @PathVariable Long supplierId) {

        log.debug("API: Get import receipts by supplierId={}", supplierId);
        List<ImportReceiptResponse> responses = importReceiptService.getReceiptsBySupplier(supplierId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
