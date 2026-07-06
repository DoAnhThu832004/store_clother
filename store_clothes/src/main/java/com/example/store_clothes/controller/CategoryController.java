package com.example.store_clothes.controller;

import com.example.store_clothes.dto.request.CreateCategoryRequest;
import com.example.store_clothes.dto.request.UpdateCategoryRequest;
import com.example.store_clothes.dto.response.ApiResponse;
import com.example.store_clothes.dto.response.CategoryResponse;
import com.example.store_clothes.dto.response.CategoryTreeResponse;
import com.example.store_clothes.service.CategoryService;
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

import java.util.List;

/**
 * CategoryController — REST API endpoints cho module Danh mục sản phẩm.
 *
 * Base URL: /api/v1/categories
 *
 * Phân quyền:
 * - Đọc (CAT-02, CAT-03, CAT-04): PUBLIC (không cần auth, hoặc mọi role)
 * - Tạo/Sửa (CAT-01, CAT-05): OWNER hoặc MANAGER
 * - Xóa (CAT-06): chỉ OWNER
 *
 * NGUYÊN TẮC:
 * - Controller chỉ nhận request → gọi Service → trả response.
 * - KHÔNG chứa business logic.
 * - @Valid kích hoạt Bean Validation, GlobalExceptionHandler bắt lỗi.
 * - @Transactional đặt ở Service, không ở đây.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Quản lý Danh mục", description = "API quản lý danh mục sản phẩm (tối đa 2 cấp)")
public class CategoryController {

    private final CategoryService categoryService;

    // =========================================================================
    // CAT-01 — POST /api/v1/categories — Tạo danh mục mới
    // =========================================================================

    /**
     * Tạo mới danh mục sản phẩm.
     *
     * Slug được tự động sinh từ name (VietnameseUtil.toSlug), xử lý trùng bằng suffix (-2, -3...).
     * Depth tối đa = 2 (Root → Child). Không cho tạo cháu (Grandchild).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @Operation(
        summary = "Tạo mới danh mục",
        description = """
            Tạo danh mục sản phẩm mới. Slug tự động sinh từ tên (VietnameseUtil.toSlug).
            - parentId null → danh mục gốc (Root, depth=1).
            - parentId có giá trị → danh mục con (depth=2). KHÔNG hỗ trợ depth=3 (cháu).
            - Nếu slug bị trùng → tự động append suffix: ao-thun → ao-thun-2 → ao-thun-3.
            """
    )
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {

        log.info("API CAT-01: Tạo danh mục name='{}'", request.name());
        CategoryResponse response = categoryService.createCategory(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    // =========================================================================
    // CAT-02 — GET /api/v1/categories — Danh sách phẳng phân trang
    // =========================================================================

    /**
     * Lấy danh sách danh mục dạng phẳng, phân trang, tìm kiếm theo tên.
     *
     * Response kèm parentName để Frontend hiển thị breadcrumb.
     * PUBLIC — không yêu cầu xác thực (phục vụ trang web khách hàng).
     */
    @GetMapping
    @Operation(
        summary = "Danh sách danh mục (phân trang)",
        description = "Lấy danh sách danh mục dạng phẳng. Response kèm parentName. Hỗ trợ tìm kiếm theo từ khóa."
    )
    public ResponseEntity<ApiResponse<Page<CategoryResponse>>> getCategories(
            @Parameter(description = "Từ khóa tìm kiếm theo tên (tùy chọn)")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "Số trang (bắt đầu từ 0)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số phần tử mỗi trang")
            @RequestParam(defaultValue = "20") int size) {

        log.debug("API CAT-02: Danh sách danh mục keyword='{}', page={}", keyword, page);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<CategoryResponse> response = categoryService.getCategories(keyword, pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // CAT-03 — GET /api/v1/categories/tree — Cây danh mục
    // =========================================================================

    /**
     * Lấy toàn bộ danh mục dạng cây phân cấp.
     *
     * Response: List<CategoryTreeResponse> gồm các Root kèm children.
     * Build cây trong Java memory (không dùng MySQL CTE recursive để tránh phụ thuộc DB version).
     * PUBLIC — phục vụ navigation menu trang web.
     */
    @GetMapping("/tree")
    @Operation(
        summary = "Cây danh mục phân cấp",
        description = """
            Trả về cấu trúc cây danh mục (Root → Children).
            Build in-memory (không dùng MySQL CTE recursive) để tương thích MySQL 5.7 và 8.0.
            Trade-off: phù hợp khi số lượng danh mục < 10,000. Recommend cache Redis cho production.
            """
    )
    public ResponseEntity<ApiResponse<List<CategoryTreeResponse>>> getCategoryTree() {

        log.debug("API CAT-03: Lấy cây danh mục");
        List<CategoryTreeResponse> response = categoryService.getCategoryTree();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // CAT-04 — GET /api/v1/categories/{id} — Chi tiết danh mục
    // =========================================================================

    /**
     * Lấy chi tiết một danh mục kèm số lượng sản phẩm ACTIVE.
     *
     * activeProductCount: dùng để hiển thị badge số lượng trên trang danh mục.
     * PUBLIC — không yêu cầu xác thực.
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Chi tiết danh mục",
        description = "Trả về thông tin chi tiết danh mục kèm số lượng sản phẩm đang ACTIVE. " +
                      "activeProductCount được tính bằng COUNT query nhẹ (không load toàn bộ Product)."
    )
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(
            @Parameter(description = "ID danh mục") @PathVariable Long id) {

        log.debug("API CAT-04: Chi tiết danh mục id={}", id);
        CategoryResponse response = categoryService.getCategoryById(id);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // CAT-05 — PUT /api/v1/categories/{id} — Cập nhật danh mục
    // =========================================================================

    /**
     * Cập nhật thông tin danh mục.
     *
     * Sử dụng Optimistic Lock (@Version): nếu 2 Manager cùng cập nhật → HTTP 409.
     * Kiểm tra circular reference khi thay đổi parentId:
     *   - Không gán danh mục thành con của chính nó.
     *   - Không gán thành con của danh mục con nó.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @Operation(
        summary = "Cập nhật danh mục",
        description = """
            Cập nhật tên, parentId, status của danh mục.
            - Optimistic Lock (@Version): xung đột đồng thời → HTTP 409 Conflict.
            - Kiểm tra circular reference khi đổi parentId.
            - Slug tự động cập nhật nếu name thay đổi.
            """
    )
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @Parameter(description = "ID danh mục cần cập nhật") @PathVariable Long id,
            @Valid @RequestBody UpdateCategoryRequest request) {

        log.info("API CAT-05: Cập nhật danh mục id={}", id);
        CategoryResponse response = categoryService.updateCategory(id, request);

        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật danh mục thành công"));
    }

    // =========================================================================
    // CAT-06 — DELETE /api/v1/categories/{id} — Xóa mềm danh mục
    // =========================================================================

    /**
     * Xóa mềm danh mục sản phẩm (chỉ OWNER).
     *
     * Điều kiện:
     * - Không có sản phẩm ACTIVE trong danh mục (trả về activeProductsCount nếu có).
     * - Không có danh mục con active.
     *
     * @SQLDelete tự động: UPDATE categories SET is_deleted=true, slug=CONCAT(slug,'_deleted_',UNIX_TIMESTAMP()).
     * Ghi AuditLog bất đồng bộ (@Async).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(
        summary = "Xóa mềm danh mục",
        description = """
            Xóa mềm danh mục (chỉ OWNER). Điều kiện:
            1. Không có sản phẩm ACTIVE (error code: CATEGORY_HAS_ACTIVE_PRODUCTS + activeProductsCount).
            2. Không có danh mục con đang active (error code: CATEGORY_HAS_CHILDREN).
            @SQLDelete: UPDATE categories SET is_deleted=true, slug=CONCAT(slug,'_deleted_',UNIX_TIMESTAMP())
            — giải phóng UNIQUE slug constraint để slug có thể được tái sử dụng.
            AuditLog được ghi bất đồng bộ (@Async + Propagation.REQUIRES_NEW).
            """
    )
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @Parameter(description = "ID danh mục cần xóa") @PathVariable Long id) {

        log.info("API CAT-06: Xóa mềm danh mục id={}", id);
        categoryService.deleteCategory(id);

        return ResponseEntity.ok(ApiResponse.success("Đã xóa danh mục thành công"));
    }
}
