package com.example.store_clothes.controller;

import com.example.store_clothes.dto.request.CreateProductRequest;
import com.example.store_clothes.dto.request.CreateProductMatrixRequest;
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
}
