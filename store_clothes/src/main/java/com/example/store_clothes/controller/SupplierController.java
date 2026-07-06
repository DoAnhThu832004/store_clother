package com.example.store_clothes.controller;

import com.example.store_clothes.dto.request.CreateSupplierRequest;
import com.example.store_clothes.dto.request.UpdateSupplierRequest;
import com.example.store_clothes.dto.response.ApiResponse;
import com.example.store_clothes.dto.response.SupplierResponse;
import com.example.store_clothes.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * SupplierController — REST API endpoints cho module Nhà Cung Cấp.
 *
 * Base URL: /api/v1/suppliers
 *
 * Phân quyền:
 * - Tạo/Đọc (SUP-01, SUP-02, SUP-03): OWNER, MANAGER, WAREHOUSE_STAFF
 * - Cập nhật (SUP-04): OWNER, MANAGER (không cho WAREHOUSE_STAFF sửa)
 * - Xóa (SUP-05): chỉ OWNER
 *
 * NGUYÊN TẮC: Controller chỉ nhận → gọi Service → trả response.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
@Tag(name = "Quản lý Nhà Cung Cấp", description = "API quản lý nhà cung cấp hàng hóa và theo dõi công nợ")
public class SupplierController {

    private final SupplierService supplierService;

    // =========================================================================
    // SUP-01 — POST /api/v1/suppliers — Tạo nhà cung cấp mới
    // =========================================================================

    /**
     * Tạo mới nhà cung cấp.
     *
     * Validate số điện thoại không trùng với NCC khác đang active.
     * debtAmount mặc định = 0 khi tạo mới.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(
        summary = "Tạo mới nhà cung cấp",
        description = """
            Tạo nhà cung cấp mới. Số điện thoại phải unique trong hệ thống.
            debtAmount mặc định = 0 khi tạo, chỉ hệ thống cập nhật theo quy trình nhập hàng.
            phone: Validate định dạng số điện thoại Việt Nam + unique check.
            """
    )
    public ResponseEntity<ApiResponse<SupplierResponse>> createSupplier(
            @Valid @RequestBody CreateSupplierRequest request) {

        log.info("API SUP-01: Tạo NCC name='{}', phone='{}'", request.name(), request.phone());
        SupplierResponse response = supplierService.createSupplier(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    // =========================================================================
    // SUP-02 — GET /api/v1/suppliers — Danh sách NCC phân trang
    // =========================================================================

    /**
     * Lấy danh sách nhà cung cấp có phân trang và filter.
     *
     * hasDebt=true → chỉ lấy NCC đang có công nợ (debt > 0).
     * Dùng cho báo cáo công nợ cuối kỳ của kế toán.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(
        summary = "Danh sách nhà cung cấp",
        description = """
            Danh sách NCC phân trang. Hỗ trợ filter:
            - keyword: Tìm kiếm theo tên (case-insensitive, partial match).
            - hasDebt=true: Chỉ lấy NCC đang có công nợ (debtAmount > 0).
              Dùng cho màn hình theo dõi công nợ của kế toán.
            """
    )
    public ResponseEntity<ApiResponse<Page<SupplierResponse>>> getSuppliers(
            @Parameter(description = "Từ khóa tìm kiếm theo tên NCC")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "true = chỉ lấy NCC đang có công nợ")
            @RequestParam(required = false) Boolean hasDebt,

            @Parameter(description = "Số trang (bắt đầu từ 0)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số phần tử mỗi trang")
            @RequestParam(defaultValue = "20") int size) {

        log.debug("API SUP-02: Danh sách NCC keyword='{}', hasDebt={}, page={}", keyword, hasDebt, page);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<SupplierResponse> response = supplierService.getSuppliers(keyword, hasDebt, pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // SUP-03 — GET /api/v1/suppliers/{id} — Chi tiết NCC
    // =========================================================================

    /**
     * Lấy chi tiết nhà cung cấp kèm 5 phiếu nhập gần nhất.
     *
     * recentImportReceipts: Hiển thị lịch sử nhập hàng gần đây.
     * Không load toàn bộ ImportReceipt (tránh over-fetching).
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(
        summary = "Chi tiết nhà cung cấp",
        description = """
            Trả về thông tin chi tiết NCC kèm 5 phiếu nhập gần nhất (recentImportReceipts).
            Top-5 với LIMIT query — không phân trang để tránh overhead.
            Để xem toàn bộ lịch sử nhập → dùng API ImportReceipt với filter supplierId.
            """
    )
    public ResponseEntity<ApiResponse<SupplierResponse>> getSupplierById(
            @Parameter(description = "ID nhà cung cấp") @PathVariable Long id) {

        log.debug("API SUP-03: Chi tiết NCC id={}", id);
        SupplierResponse response = supplierService.getSupplierById(id);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // SUP-04 — PUT /api/v1/suppliers/{id} — Cập nhật NCC
    // =========================================================================

    /**
     * Cập nhật thông tin nhà cung cấp.
     *
     * KHÔNG cho phép sửa debtAmount qua đây — chỉ hệ thống tự cập nhật.
     * Optimistic Lock (@Version): xung đột đồng thời → HTTP 409.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @Operation(
        summary = "Cập nhật nhà cung cấp",
        description = """
            Cập nhật thông tin NCC. KHÔNG cho phép sửa debtAmount.
            debtAmount chỉ được hệ thống cập nhật theo quy trình ImportReceipt.
            Optimistic Lock (@Version): nếu 2 người cùng cập nhật → HTTP 409.
            """
    )
    public ResponseEntity<ApiResponse<SupplierResponse>> updateSupplier(
            @Parameter(description = "ID nhà cung cấp cần cập nhật") @PathVariable Long id,
            @Valid @RequestBody UpdateSupplierRequest request) {

        log.info("API SUP-04: Cập nhật NCC id={}", id);
        SupplierResponse response = supplierService.updateSupplier(id, request);

        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật nhà cung cấp thành công"));
    }

    // =========================================================================
    // SUP-05 — DELETE /api/v1/suppliers/{id} — Xóa mềm NCC
    // =========================================================================

    /**
     * Xóa mềm nhà cung cấp (chỉ OWNER).
     *
     * Điều kiện bắt buộc:
     * 1. debtAmount = 0 (error: SUPPLIER_OUTSTANDING_DEBT + debtAmount).
     * 2. Không có phiếu nhập DRAFT (error: SUPPLIER_HAS_DRAFT_RECEIPTS).
     *
     * @SQLDelete: UPDATE suppliers SET is_deleted=true, phone=CONCAT(phone,'_deleted_',UNIX_TIMESTAMP()).
     * Ghi AuditLog bất đồng bộ.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(
        summary = "Xóa mềm nhà cung cấp",
        description = """
            Xóa mềm NCC (chỉ OWNER). Điều kiện:
            1. debtAmount = 0 (error code: SUPPLIER_OUTSTANDING_DEBT + debtAmount trong details).
            2. Không có phiếu nhập DRAFT (error code: SUPPLIER_HAS_DRAFT_RECEIPTS).
            @SQLDelete: UPDATE suppliers SET is_deleted=true, phone=CONCAT(phone,'_deleted_',UNIX_TIMESTAMP())
            — giải phóng UNIQUE phone constraint.
            AuditLog @Async + Propagation.REQUIRES_NEW cho action nhạy cảm.
            """
    )
    public ResponseEntity<ApiResponse<Void>> deleteSupplier(
            @Parameter(description = "ID nhà cung cấp cần xóa") @PathVariable Long id) {

        log.info("API SUP-05: Xóa mềm NCC id={}", id);
        supplierService.deleteSupplier(id);

        return ResponseEntity.ok(ApiResponse.success("Đã xóa nhà cung cấp thành công"));
    }
}
