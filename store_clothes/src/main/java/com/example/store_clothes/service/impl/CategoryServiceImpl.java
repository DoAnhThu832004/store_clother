package com.example.store_clothes.service.impl;

import com.example.store_clothes.dto.request.CreateCategoryRequest;
import com.example.store_clothes.dto.request.UpdateCategoryRequest;
import com.example.store_clothes.dto.response.CategoryResponse;
import com.example.store_clothes.dto.response.CategoryTreeResponse;
import com.example.store_clothes.entity.Category;
import com.example.store_clothes.entity.CategoryStatus;
import com.example.store_clothes.exception.DomainException;
import com.example.store_clothes.exception.EntityNotFoundException;
import com.example.store_clothes.exception.ErrorCode;
import com.example.store_clothes.repository.CategoryRepository;
import com.example.store_clothes.service.AuditLogService;
import com.example.store_clothes.service.CategoryService;
import com.example.store_clothes.util.VietnameseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * CategoryServiceImpl — Triển khai toàn bộ nghiệp vụ Danh mục sản phẩm.
 *
 * NGUYÊN TẮC:
 * 1. @Transactional đặt ở Service, KHÔNG đặt ở Controller.
 * 2. Trả về DTO, không bao giờ trả Entity.
 * 3. Dùng DomainException(ErrorCode) thay vì BusinessException.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final AuditLogService auditLogService;

    // =========================================================================
    // CAT-01 — Tạo danh mục mới
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * Flow:
     * 1. Parse và validate status.
     * 2. Nếu có parentId → validate parent tồn tại + kiểm tra depth ≤ 2.
     * 3. Auto-generate slug unique từ name.
     * 4. Persist và trả về DTO.
     */
    @Override
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        log.info("Tạo danh mục mới: name='{}'", request.name());

        // Parse status
        CategoryStatus status = parseCategoryStatus(request.status());

        // Validate parentId nếu có
        Category parent = null;
        if (request.parentId() != null) {
            parent = validateAndGetParent(request.parentId());
        }

        // Auto-generate slug unique
        String baseSlug = VietnameseUtil.toSlug(request.name());
        String slug = resolveSlugConflict(baseSlug);

        // Build và persist entity
        Category category = Category.builder()
                .name(request.name())
                .slug(slug)
                .parent(parent)
                .status(status)
                .build();

        category = categoryRepository.save(category);
        log.info("Đã tạo danh mục: id={}, slug='{}'", category.getId(), category.getSlug());

        return CategoryResponse.from(category);
    }

    // =========================================================================
    // CAT-02 — Danh sách phẳng phân trang
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * 💡 Senior Note — Lazy Load Parent:
     * Khi Category được load từ Page query, parent là LAZY.
     * Trong cùng một @Transactional context, truy cập category.getParent().getName()
     * sẽ trigger lazy load (1 query per category có parent).
     * Để tránh N+1 problem ở scale lớn, nên dùng @EntityGraph hoặc JOIN FETCH.
     * Với tập dữ liệu nhỏ (< 500 danh mục), chấp nhận N+1 để code đơn giản hơn.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<CategoryResponse> getCategories(String keyword, Pageable pageable) {
        log.debug("Lấy danh sách danh mục: keyword='{}', page={}", keyword, pageable.getPageNumber());

        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return categoryRepository.searchByKeyword(kw, pageable)
                .map(CategoryResponse::from);
    }

    // =========================================================================
    // CAT-03 — Cây danh mục (in-memory build)
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * 💡 Senior Note — Trade-off In-Memory vs CTE:
     * MySQL CTE (WITH RECURSIVE):
     *   + DB xử lý, 1 query phức tạp
     *   - Không tương thích MySQL 5.7, khó combine với @SQLRestriction
     *   - Phụ thuộc DB vendor syntax
     * In-Memory Build (O(n) pass):
     *   + DB-agnostic, tương thích mọi version
     *   + Dễ áp dụng bất kỳ filter nào
     *   - Tải toàn bộ vào RAM → chỉ ổn khi < ~10,000 rows
     * Với hệ thống thời trang quy mô SME (< 500 danh mục), in-memory là optimal.
     *
     * Thuật toán: 2 pass O(n):
     *   Pass 1: Convert tất cả Category → CategoryTreeResponse (Map by ID)
     *   Pass 2: Gán children + tách root ra list
     */
    @Override
    @Transactional(readOnly = true)
    public List<CategoryTreeResponse> getCategoryTree() {
        log.debug("Build cây danh mục in-memory");

        List<Category> allCategories = categoryRepository.findAllForTree();

        return buildTreeEfficient(allCategories);
    }

    /**
     * Build cây danh mục hiệu quả dùng mutable intermediate objects.
     *
     * 💡 Senior Note — Mutable Intermediate:
     * CategoryTreeResponse dùng @Builder nên immutable sau build().
     * Để build cây, ta cần gán children sau khi tạo object.
     * Giải pháp: Dùng map<ID, List<Category>> để group children,
     * sau đó build từ root xuống (Top-down DFS).
     */
    private List<CategoryTreeResponse> buildTreeEfficient(List<Category> allCategories) {
        // Group children theo parent ID
        Map<Long, List<Category>> childrenMap = allCategories.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        // Lấy danh sách root (không có parent)
        List<Category> roots = allCategories.stream()
                .filter(c -> c.getParent() == null)
                .collect(Collectors.toList());

        // Build tree từ root, DFS
        return roots.stream()
                .map(root -> buildNode(root, childrenMap))
                .collect(Collectors.toList());
    }

    /**
     * Đệ quy build một node và tất cả con của nó.
     * Depth được giới hạn tự nhiên vì DB chỉ có tối đa 2 cấp (không có cháu).
     */
    private CategoryTreeResponse buildNode(Category category, Map<Long, List<Category>> childrenMap) {
        List<CategoryTreeResponse> children = childrenMap
                .getOrDefault(category.getId(), Collections.emptyList())
                .stream()
                .map(child -> buildNode(child, childrenMap))
                .collect(Collectors.toList());

        return CategoryTreeResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .status(category.getStatus().name())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .children(children.isEmpty() ? null : children)
                .build();
    }

    // =========================================================================
    // CAT-04 — Chi tiết danh mục
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * Kết hợp 2 query:
     * 1. findById → load Category với parent (lazy load trong transaction).
     * 2. countActiveProductsByCategoryId → COUNT query nhẹ.
     */
    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        log.debug("Lấy chi tiết danh mục id={}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy danh mục với id: " + id));

        long activeProductCount = categoryRepository.countActiveProductsByCategoryId(id);

        return CategoryResponse.from(category, activeProductCount);
    }

    // =========================================================================
    // CAT-05 — Cập nhật danh mục (Optimistic Lock)
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * 💡 Senior Note — Optimistic Lock hoạt động như thế nào ở đây?
     * Hibernate tự động thêm WHERE id=? AND version=? vào câu UPDATE.
     * Nếu version trong DB khác với version đang giữ → 0 rows updated
     * → Hibernate ném ObjectOptimisticLockingFailureException
     * → GlobalExceptionHandler bắt → HTTP 409 Conflict.
     * Client phải load lại data (version mới) trước khi retry.
     *
     * Flow kiểm tra circular reference khi đổi parentId:
     * - category không thể làm con của chính nó (id == newParentId).
     * - category không thể làm con của con nó (newParent là child của category).
     */
    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        log.info("Cập nhật danh mục id={}, name='{}'", id, request.name());

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy danh mục với id: " + id));

        CategoryStatus status = parseCategoryStatus(request.status());

        // Xử lý parentId
        Category newParent = null;
        if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw new DomainException(ErrorCode.CATEGORY_CIRCULAR_REFERENCE,
                        "Danh mục không thể là con của chính nó");
            }

            newParent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Không tìm thấy danh mục cha với id: " + request.parentId()));

            // Kiểm tra parent mới không phải là con của category hiện tại
            checkNotCircularReference(id, newParent);

            // Kiểm tra parent mới không tạo depth > 2
            if (newParent.getParent() != null) {
                throw new DomainException(ErrorCode.CATEGORY_DEPTH_EXCEEDED,
                        "Danh mục cha đã là danh mục con, không thể tạo thêm 1 cấp nữa (depth > 2)");
            }
        }

        // Cập nhật slug nếu name thay đổi
        if (!category.getName().equals(request.name())) {
            String baseSlug = VietnameseUtil.toSlug(request.name());
            // Loại trừ slug hiện tại của chính danh mục này khi check trùng
            String newSlug = resolveSlugConflictExcluding(baseSlug, category.getSlug());
            category.setSlug(newSlug);
        }

        category.setName(request.name());
        category.setParent(newParent);
        category.setStatus(status);

        // @Version tự động increment sau save → Optimistic Lock hoạt động
        category = categoryRepository.save(category);

        log.info("Đã cập nhật danh mục id={}", id);
        return CategoryResponse.from(category);
    }

    // =========================================================================
    // CAT-06 — Xóa mềm danh mục
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * 💡 Senior Note — Thứ tự kiểm tra trong Soft Delete:
     * 1. Kiểm tra danh mục tồn tại (EntityNotFoundException).
     * 2. Kiểm tra không có sản phẩm ACTIVE (throw với details để Frontend hiển thị số lượng).
     * 3. Kiểm tra không có danh mục con active.
     * 4. Gọi delete() → @SQLDelete tự convert thành UPDATE + rename slug.
     * 5. Ghi AuditLog @Async (không block response chính).
     *
     * Thứ tự này quan trọng: các kiểm tra nghiệp vụ PHẢI chạy trước delete().
     * Nếu delete() chạy trước → @SQLRestriction lọc luôn → không query được nữa.
     */
    @Override
    @Transactional
    public void deleteCategory(Long id) {
        log.info("Yêu cầu xóa mềm danh mục id={}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy danh mục với id: " + id));

        // Kiểm tra sản phẩm ACTIVE
        long activeProductCount = categoryRepository.countActiveProductsByCategoryId(id);
        if (activeProductCount > 0) {
            throw new DomainException(
                    ErrorCode.CATEGORY_HAS_ACTIVE_PRODUCTS,
                    String.format("Danh mục đang chứa %d sản phẩm hoạt động, vui lòng chuyển hoặc xóa sản phẩm trước", activeProductCount),
                    Map.of("activeProductsCount", activeProductCount)
            );
        }

        // Kiểm tra danh mục con active
        long activeChildCount = categoryRepository.countByParentId(id);
        if (activeChildCount > 0) {
            throw new DomainException(
                    ErrorCode.CATEGORY_HAS_CHILDREN,
                    String.format("Danh mục đang có %d danh mục con, vui lòng xóa danh mục con trước", activeChildCount),
                    Map.of("activeChildCount", activeChildCount)
            );
        }

        // Ghi AuditLog TRƯỚC khi xóa (sau khi xóa entity không còn truy cập được)
        String categorySnapshot = String.format("{\"id\":%d,\"name\":\"%s\",\"slug\":\"%s\"}",
                category.getId(), category.getName(), category.getSlug());

        // Thực hiện soft delete — @SQLDelete tự rename slug + set is_deleted=true
        categoryRepository.delete(category);

        log.info("Đã xóa mềm danh mục id={}, slug='{}'", id, category.getSlug());

        // Ghi AuditLog bất đồng bộ — Propagation.REQUIRES_NEW trong AuditLogService
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null) ? auth.getName() : "SYSTEM";
        auditLogService.log(null, username, "DELETE_CATEGORY", "CATEGORY", id,
                "{\"action\":\"SOFT_DELETE\",\"before\":" + categorySnapshot + "}");
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Parse và validate CategoryStatus từ string.
     */
    private CategoryStatus parseCategoryStatus(String statusStr) {
        try {
            return CategoryStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Trạng thái danh mục không hợp lệ: " + statusStr + ". Chỉ chấp nhận ACTIVE hoặc INACTIVE");
        }
    }

    /**
     * Validate parent category khi tạo mới:
     * - Parent phải tồn tại.
     * - Parent không được có parent (depth = 1), tránh tạo grandchild (depth > 2).
     *
     * 💡 Senior Note — Tại sao giới hạn depth = 2?
     * (1) UI/UX: Navigation menu 3 cấp rất phức tạp trên mobile.
     * (2) Query complexity: Recursive query phức tạp, khó tối ưu.
     * (3) Business rule: Trong thời trang, 2 cấp là đủ (VD: "Nam" → "Áo khoác").
     * (4) KiotViet chuẩn cũng chỉ hỗ trợ 2 cấp danh mục.
     */
    private Category validateAndGetParent(Long parentId) {
        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy danh mục cha với id: " + parentId));

        if (parent.getParent() != null) {
            throw new DomainException(ErrorCode.CATEGORY_DEPTH_EXCEEDED,
                    "Không thể tạo danh mục cháu. Danh mục cha '" + parent.getName()
                            + "' đã là danh mục cấp 2. Hệ thống chỉ hỗ trợ tối đa 2 cấp danh mục.");
        }

        return parent;
    }

    /**
     * Sinh slug unique từ base slug.
     * Nếu "ao-thun" đã tồn tại → thử "ao-thun-2", "ao-thun-3"...
     *
     * 💡 Senior Note — Tại sao append "-2" thay vì "-1"?
     * Convention phổ biến: slug gốc = "ao-thun" (không có số),
     * duplicate đầu tiên = "ao-thun-2" (hàm ý "bản 2").
     * Đây là pattern của WordPress, Shopify, nhiều CMS lớn.
     */
    private String resolveSlugConflict(String baseSlug) {
        if (!categoryRepository.existsBySlug(baseSlug)) {
            return baseSlug;
        }
        int suffix = 2;
        while (categoryRepository.existsBySlug(baseSlug + "-" + suffix)) {
            suffix++;
        }
        return baseSlug + "-" + suffix;
    }

    /**
     * Sinh slug unique, loại trừ slug hiện tại của entity đang update.
     * Tránh conflict slug với chính nó khi giữ nguyên name.
     */
    private String resolveSlugConflictExcluding(String baseSlug, String currentSlug) {
        if (!categoryRepository.existsBySlug(baseSlug) || baseSlug.equals(currentSlug)) {
            return baseSlug;
        }
        int suffix = 2;
        while (categoryRepository.existsBySlug(baseSlug + "-" + suffix)) {
            suffix++;
        }
        return baseSlug + "-" + suffix;
    }

    /**
     * Kiểm tra xem newParent có phải là con (trực tiếp hoặc gián tiếp) của category không.
     * Nếu có → ném CATEGORY_CIRCULAR_REFERENCE.
     *
     * 💡 Senior Note — Tại sao chỉ cần kiểm tra 1 cấp?
     * Vì hệ thống chỉ cho phép depth = 2, nên children của category (depth=1)
     * không thể có con của chúng (depth sẽ = 3, bị chặn ở CAT-01/CAT-05).
     * Do đó, chỉ cần kiểm tra xem newParent có nằm trong children trực tiếp không.
     *
     * Nếu sau này mở rộng cho phép depth > 2, cần implement DFS/BFS đầy đủ.
     */
    private void checkNotCircularReference(Long categoryId, Category newParent) {
        List<Category> children = categoryRepository.findAllByParentId(categoryId);
        boolean isCircular = children.stream()
                .anyMatch(child -> child.getId().equals(newParent.getId()));

        if (isCircular) {
            throw new DomainException(ErrorCode.CATEGORY_CIRCULAR_REFERENCE,
                    "Không thể gán danh mục thành con của danh mục con của nó — tạo vòng lặp phân cấp");
        }
    }
}
