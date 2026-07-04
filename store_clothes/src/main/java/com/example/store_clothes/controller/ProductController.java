package com.example.store_clothes.controller;

import com.example.store_clothes.dto.request.AddVariantRequest;
import com.example.store_clothes.dto.request.CreateProductRequest;
import com.example.store_clothes.dto.request.CreateProductMatrixRequest;
import com.example.store_clothes.dto.request.StockAdjustmentRequest;
import com.example.store_clothes.dto.request.UpdateProductRequest;
import com.example.store_clothes.dto.request.UpdateVariantRequest;
import com.example.store_clothes.dto.response.ApiResponse;
import com.example.store_clothes.dto.response.ProductMatrixResponse;
import com.example.store_clothes.dto.response.ProductResponse;
import com.example.store_clothes.dto.response.VariantResponse;
import com.example.store_clothes.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * ProductController - REST API endpoints cho module Quản lý Hàng hóa.
 *
 * Base URL: /api/v1/products
 *
 * NGUYÊN TẮC:
 * 1. Controller chỉ làm 3 việc: nhận request → gọi Service → trả response.
 *    KHÔNG chứa business logic, không truy cập Repository trực tiếp.
 * 2. MỌI response đều bọc trong ApiResponse<T>.
 * 3. @Valid trên @RequestBody để kích hoạt Bean Validation.
 *    Nếu validation fail, MethodArgumentNotValidException được ném và
 *    GlobalExceptionHandler sẽ bắt, trả về HTTP 400 với chi tiết lỗi.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Quản lý Hàng hóa", description = "API quản lý sản phẩm và biến thể hàng hóa")
public class ProductController {

    private final ProductService productService;

    // =========================================================================
    // POST /api/v1/products - Tạo mới sản phẩm
    // =========================================================================

    /**
     * Tạo mới một sản phẩm cùng toàn bộ biến thể.
     *
     * Request body phải chứa ít nhất 1 variant.
     * Trả về HTTP 201 Created với thông tin sản phẩm đã tạo.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @Operation(
            summary = "Tạo mới sản phẩm",
            description = "Tạo một sản phẩm gốc kèm theo danh sách biến thể. Bắt buộc ít nhất 1 biến thể."
    )
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {

        log.info("API: Tạo mới sản phẩm - code={}", request.code());
        ProductResponse response = productService.createProduct(request);

        // HTTP 201 Created: Quy ước REST cho thao tác tạo mới thành công.
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    // =========================================================================
    // POST /api/v1/products/matrix - Sinh ma trận biến thể tự động
    // =========================================================================

    /**
     * Sinh ma trận biến thể sản phẩm tự động theo tích Descartes (Colors × Sizes).
     *
     * Client chỉ cần cung cấp:
     * - Tên sản phẩm, mô tả.
     * - Danh sách màu sắc (colors[]).
     * - Danh sách kích cỡ (sizes[]).
     * - Giá nhập và giá bán cơ sở (dùng chung cho tất cả biến thể).
     *
     * Service tự động:
     * - Sinh mã sản phẩm (productCode) từ tên.
     * - Tạo colors.size() × sizes.size() biến thể.
     * - Sinh SKU tự động và xử lý xung đột trong bộ nhớ.
     *
     * Trả về HTTP 201 Created với ProductMatrixResponse bao gồm:
     * totalVariants, danh sách màu/size gốc, và toàn bộ variant đã tạo.
     */
    @PostMapping("/matrix")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @Operation(
            summary = "Sinh ma trận biến thể tự động",
            description = "Tạo sản phẩm và tự động sinh toàn bộ biến thể theo tích Descartes (Colors × Sizes). "
                    + "SKU được sinh tự động từ viết tắt tên sản phẩm + màu + size. "
                    + "Xung đột SKU được giải quyết In-Memory (1 query DB duy nhất)."
    )
    public ResponseEntity<ApiResponse<ProductMatrixResponse>> createProductMatrix(
            @Valid @RequestBody CreateProductMatrixRequest request) {

        log.info("API: Sinh ma trận biến thể - name='{}', {}x{} matrix",
                request.name(), request.colors().size(), request.sizes().size());

        ProductMatrixResponse response = productService.createProductMatrix(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    // =========================================================================
    // GET /api/v1/products/{id} - Lấy chi tiết sản phẩm
    // =========================================================================

    /**
     * Lấy thông tin chi tiết một sản phẩm kèm toàn bộ biến thể.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'CASHIER', 'WAREHOUSE_STAFF')")
    @Operation(
            summary = "Lấy chi tiết sản phẩm",
            description = "Trả về thông tin sản phẩm và danh sách biến thể (chỉ các variant chưa bị xóa)"
    )
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @Parameter(description = "ID sản phẩm") @PathVariable Long id) {

        log.debug("API: Lấy chi tiết sản phẩm id={}", id);
        ProductResponse response = productService.getProductById(id);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // PUT /api/v1/products/{id} - Cập nhật thông tin sản phẩm (TICKET P-01)
    // =========================================================================

    /**
     * Cập nhật thông tin cơ bản của sản phẩm gốc.
     *
     * ⚠️ Không cho phép thay đổi mã sản phẩm (code) — khóa nghiệp vụ bất biến.
     * Chỉ cập nhật: name, description, categoryId.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @Operation(
            summary = "Cập nhật thông tin sản phẩm",
            description = "Cập nhật tên, mô tả sản phẩm. " +
                    "KHÔNG thay đổi được mã sản phẩm (code) — đây là khóa nghiệp vụ bất biến sau khi tạo. " +
                    "Ghi AuditLog bất đồng bộ với snapshot trước/sau."
    )
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @Parameter(description = "ID sản phẩm cần cập nhật") @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {

        log.info("API: Cập nhật sản phẩm id={}, name={}", id, request.name());
        ProductResponse response = productService.updateProduct(id, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // GET /api/v1/products/variants/sku/{sku} - Tìm theo SKU
    // =========================================================================

    /**
     * Tìm kiếm biến thể sản phẩm theo SKU.
     *
     * SKU path variable được đưa vào sub-resource /variants/sku/{sku}
     * để tách biệt rõ ràng giữa tìm Product và tìm Variant.
     *
     * Sử dụng DB Index idx_variant_sku → O(log n) query.
     */
    @GetMapping("/variants/sku/{sku}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'CASHIER', 'WAREHOUSE_STAFF')")
    @Operation(
            summary = "Tìm biến thể theo SKU",
            description = "Sử dụng index để tìm kiếm nhanh theo mã SKU. Trả về thông tin biến thể kèm sản phẩm gốc."
    )
    public ResponseEntity<ApiResponse<VariantResponse>> findBySku(
            @Parameter(description = "Mã SKU của biến thể") @PathVariable String sku) {

        log.debug("API: Tìm kiếm theo SKU: {}", sku);
        VariantResponse response = productService.findBySku(sku);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // GET /api/v1/products/variants/barcode/{barcode} - Tìm theo Barcode
    // =========================================================================

    /**
     * Tìm kiếm biến thể sản phẩm theo Barcode.
     *
     * Thường được gọi khi nhân viên quét mã vạch tại quầy bán hàng.
     * Sử dụng DB Index idx_variant_barcode → O(log n) query.
     */
    @GetMapping("/variants/barcode/{barcode}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'CASHIER', 'WAREHOUSE_STAFF')")
    @Operation(
            summary = "Tìm biến thể theo Barcode",
            description = "Tìm kiếm nhanh theo mã barcode (quét mã vạch). Trả về thông tin biến thể kèm sản phẩm gốc."
    )
    public ResponseEntity<ApiResponse<VariantResponse>> findByBarcode(
            @Parameter(description = "Mã Barcode của biến thể") @PathVariable String barcode) {

        log.debug("API: Tìm kiếm theo Barcode: {}", barcode);
        VariantResponse response = productService.findByBarcode(barcode);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // POST /api/v1/products/{productId}/variants - Thêm biến thể đơn lẻ (TICKET PV-01)
    // =========================================================================

    /**
     * Thêm một biến thể mới vào sản phẩm đã tồn tại.
     *
     * - SKU: tùy chọn. Nếu không truyền, Service tự sinh theo quy tắc viết tắt.
     * - initialInventory > 0: tự động ghi StockHistory (ADJUSTMENT, ref=INIT).
     */
    @PostMapping("/{productId}/variants")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @Operation(
            summary = "Thêm biến thể đơn lẻ vào sản phẩm",
            description = "Thêm một biến thể mới (màu + size + giá) vào sản phẩm đã tồn tại. " +
                    "SKU tùy chọn — nếu null, hệ thống tự sinh theo viết tắt tên SP + màu + size. " +
                    "Nếu initialInventory > 0, StockHistory được ghi tự động với type=ADJUSTMENT, ref=INIT."
    )
    public ResponseEntity<ApiResponse<VariantResponse>> addVariant(
            @Parameter(description = "ID sản phẩm cha") @PathVariable Long productId,
            @Valid @RequestBody AddVariantRequest request) {

        log.info("API: Thêm biến thể cho sản phẩm id={}, color={}, size={}", productId, request.color(), request.size());
        VariantResponse response = productService.addVariant(productId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    // =========================================================================
    // PUT /api/v1/products/variants/{id} - Cập nhật giá biến thể (TICKET PV-02)
    // =========================================================================

    /**
     * Cập nhật giá nhập, giá bán và/hoặc trạng thái của biến thể.
     *
     * Patch semantics: null = không thay đổi trường đó.
     * Optimistic Lock (@Version): nếu biến thể bị sửa đồng thời → HTTP 409.
     * KHÔNG cho phép sửa inventory qua endpoint này (dùng /stock-adjustment).
     */
    @PutMapping("/variants/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @Operation(
            summary = "Cập nhật giá và trạng thái biến thể",
            description = "Cập nhật giá nhập, giá bán, hoặc trạng thái biến thể (patch semantics: null = không đổi). " +
                    "Sử dụng Optimistic Lock (@Version): nếu 2 người cùng cập nhật → người sau nhận HTTP 409. " +
                    "KHÔNG cho phép sửa inventory trực tiếp — dùng POST /variants/{id}/stock-adjustment."
    )
    public ResponseEntity<ApiResponse<VariantResponse>> updateVariant(
            @Parameter(description = "ID biến thể cần cập nhật") @PathVariable Long id,
            @Valid @RequestBody UpdateVariantRequest request) {

        log.info("API: Cập nhật biến thể id={}", id);
        VariantResponse response = productService.updateVariant(id, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // POST /api/v1/products/variants/{id}/stock-adjustment - Điều chỉnh kho (TICKET PV-02b)
    // =========================================================================

    /**
     * Điều chỉnh tồn kho thủ công về số lượng mới (chỉ OWNER).
     *
     * Dùng Pessimistic Lock để đảm bảo tính nhất quán khi điều chỉnh đồng thời.
     * Bắt buộc cung cấp lý do điều chỉnh (reason) để audit trail.
     * Tự động ghi StockHistory và AuditLog bất đồng bộ.
     */
    @PostMapping("/variants/{id}/stock-adjustment")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(
            summary = "Điều chỉnh tồn kho thủ công",
            description = "Đặt tồn kho về số lượng mới chỉ định. CHỈ OWNER được phép thực hiện. " +
                    "Lý do điều chỉnh bắt buộc để phục vụ kiểm toán. " +
                    "Pessimistic Lock đảm bảo không có race condition. " +
                    "Tự động ghi StockHistory (ADJUSTMENT) + AuditLog bất đồng bộ."
    )
    public ResponseEntity<ApiResponse<Void>> adjustStock(
            @Parameter(description = "ID biến thể cần điều chỉnh tồn kho") @PathVariable Long id,
            @Valid @RequestBody StockAdjustmentRequest request) {

        log.info("API: Điều chỉnh tồn kho - variantId={}, newQty={}", id, request.newQuantity());
        productService.adjustStock(id, request);

        return ResponseEntity.ok(ApiResponse.success("Điều chỉnh tồn kho thành công"));
    }

    // =========================================================================
    // DELETE /api/v1/products/{id} - Xóa mềm sản phẩm
    // =========================================================================

    /**
     * Xóa mềm (Soft Delete) một sản phẩm và toàn bộ biến thể con.
     *
     * Sau khi xóa:
     * - Sản phẩm và biến thể VẪN CÒN trong DB (is_deleted = true).
     * - Không thể tìm thấy qua API thông thường.
     * - Dữ liệu lịch sử trong hóa đơn vẫn được bảo toàn.
     *
     * Trả về HTTP 200 với message (không có data body).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @Operation(
            summary = "Xóa mềm sản phẩm",
            description = "Xóa mềm sản phẩm và toàn bộ biến thể con (Cascade Soft Delete). Dữ liệu vẫn còn trong DB."
    )
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @Parameter(description = "ID sản phẩm cần xóa") @PathVariable Long id) {

        log.info("API: Yêu cầu xóa mềm sản phẩm id={}", id);
        productService.deleteProduct(id);

        // Trả về 200 OK với message thay vì 204 No Content.
        // 200 cho phép trả về message xác nhận, giúp Frontend hiển thị thông báo.
        return ResponseEntity.ok(ApiResponse.success("Đã xóa sản phẩm thành công"));
    }

    // =========================================================================
    // DELETE /api/v1/products/variants/{id} - Xóa mềm biến thể (TICKET PV-03)
    // =========================================================================

    /**
     * Xóa mềm một biến thể sản phẩm.
     *
     * Điều kiện bắt buộc:
     * - Tồn kho = 0 (yêu cầu xuất hoặc điều chỉnh về 0 trước).
     * - Không đang nằm trong phiếu nhập DRAFT.
     *
     * @SQLDelete tự động rename SKU để giải phóng UNIQUE constraint,
     * cho phép tái sử dụng SKU đó trong tương lai.
     */
    @DeleteMapping("/variants/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @Operation(
            summary = "Xóa mềm biến thể",
            description = "Xóa mềm biến thể sản phẩm. Yêu cầu: tồn kho = 0 và không trong phiếu nhập DRAFT. " +
                    "@SQLDelete tự động rename SKU (CONCAT sku + '_deleted_' + UNIX_TIMESTAMP()) " +
                    "để giải phóng UNIQUE constraint và cho phép tái sử dụng SKU."
    )
    public ResponseEntity<ApiResponse<Void>> deleteVariant(
            @Parameter(description = "ID biến thể cần xóa") @PathVariable Long id) {

        log.info("API: Yêu cầu xóa mềm biến thể id={}", id);
        productService.deleteVariant(id);

        return ResponseEntity.ok(ApiResponse.success("Đã xóa biến thể thành công"));
    }
}


