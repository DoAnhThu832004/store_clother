package com.example.store_clothes.controller;

import com.example.store_clothes.dto.request.CreateImportReceiptRequest;
import com.example.store_clothes.dto.request.UpdateImportReceiptRequest;
import com.example.store_clothes.dto.response.ApiResponse;
import com.example.store_clothes.dto.response.ImportReceiptResponse;
import com.example.store_clothes.enums.ImportReceiptStatus;
import com.example.store_clothes.service.ImportReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@Tag(name = "Phếu Nhập Hàng", description = "Quản lý phiếu nhập hàng từ nhà cung cấp")
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
    @Operation(
        summary = "Tạo phiếu nhập hàng (DRAFT)",
        description = "Tạo mới phiếu nhập ở trạng thái DRAFT. Chưa ảnh hưởng tồn kho và công nợ NCC."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAREHOUSE_STAFF')")
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
    @Operation(
        summary = "Hoàn thành phiếu nhập",
        description = "Chuyển phiếu từ DRAFT → COMPLETED. Cộng tồn kho, ghi thẻ kho, cập nhật công nợ NCC."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAREHOUSE_STAFF')")
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
    @Operation(
        summary = "Hủy phiếu nhập DRAFT",
        description = "Hủy phiếu nhập đang ở trạng thái DRAFT. Không thể hủy phiếu đã COMPLETED."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAREHOUSE_STAFF')")
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
    @Operation(
        summary = "Chi tiết phiếu nhập",
        description = "Lấy thông tin chi tiết phiếu nhập kèm danh sách hàng hóa."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAREHOUSE_STAFF')")
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
    @Operation(
        summary = "Danh sách phiếu nhập theo NCC",
        description = "Lấy danh sách phiếu nhập của một nhà cung cấp, sắp xếp mới nhất trước."
    )
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAREHOUSE_STAFF')")
    @GetMapping("/supplier/{supplierId}")
    public ResponseEntity<ApiResponse<List<ImportReceiptResponse>>> getReceiptsBySupplier(
            @PathVariable Long supplierId) {

        log.debug("API: Get import receipts by supplierId={}", supplierId);
        List<ImportReceiptResponse> responses = importReceiptService.getReceiptsBySupplier(supplierId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    // =========================================================================
    // IR-01: DANH SÁCH PHIẼU NHẬP (Pagination + Filter)
    // =========================================================================

    /**
     * Danh sách phiếu nhập với bộ lọc và phân trang.
     *
     * GET /api/v1/imports?page=0&size=20&status=DRAFT&supplierId=1&from=2026-01-01&to=2026-12-31
     */
    @Operation(
        summary = "Danh sách phiếu nhập (IR-01)",
        description = """
            Lấy danh sách phiếu nhập có phân trang và bộ lọc:
            - status: Lọc theo trạng thái DRAFT/COMPLETED/CANCELLED (không truyền = tất cả).
            - supplierId: Lọc theo nhà cung cấp.
            - from/to: Lọc theo khoảng thời gian (yyyy-MM-dd).
            - KHÔNG load danh sách chi tiết hàng hóa (tránh N+1). Gọi GET /{id} để xem chi tiết.
            """
    )
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAREHOUSE_STAFF')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ImportReceiptResponse>>> listReceipts(
            @Parameter(description = "Số trang (0-indexed)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số item mỗi trang (max 100)")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Lọc theo trạng thái: DRAFT, COMPLETED, CANCELLED")
            @RequestParam(required = false) ImportReceiptStatus status,

            @Parameter(description = "Lọc theo ID nhà cung cấp")
            @RequestParam(required = false) Long supplierId,

            @Parameter(description = "Ngày bắt đầu (yyyy-MM-dd)")
            @RequestParam(required = false) String from,

            @Parameter(description = "Ngày kết thúc (yyyy-MM-dd)")
            @RequestParam(required = false) String to) {

        log.debug("API: List import receipts, page={}, status={}, supplierId={}, from={}, to={}",
            page, status, supplierId, from, to);
        Page<ImportReceiptResponse> response = importReceiptService.listReceipts(
            page, size, status, supplierId, from, to);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // IR-02: SỬa PHIẼU NHẬP DRAFT
    // =========================================================================

    /**
     * Sửa phiếu nhập đang ở trạng thái DRAFT.
     *
     * PUT /api/v1/imports/{id}
     */
    @Operation(
        summary = "Sửa phiếu nhập DRAFT (IR-02)",
        description = """
            Cập nhật phiếu nhập đang ở trạng thái DRAFT.
            - Chỉ được sửa khi status = DRAFT. COMPLETED/CANCELLED → HTTP 409.
            - Các field null = giữ nguyên giá trị hiện tại.
            - Nếu items được truyền → XÓA toàn bộ details cũ và tạo lại (delete-and-recreate pattern).
            - Tính lại totalAmount dựa trên items mới.
            """
    )
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAREHOUSE_STAFF')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ImportReceiptResponse>> updateDraftReceipt(
            @PathVariable Long id,
            @RequestBody @Valid UpdateImportReceiptRequest request) {

        log.debug("API: Update draft receipt id={}", id);
        ImportReceiptResponse response = importReceiptService.updateDraftReceipt(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật phiếu nhập thành công"));
    }
}
