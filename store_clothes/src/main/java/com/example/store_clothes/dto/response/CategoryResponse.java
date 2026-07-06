package com.example.store_clothes.dto.response;

import com.example.store_clothes.entity.Category;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * DTO trả về thông tin Danh mục dạng phẳng (flat list).
 * Dùng cho CAT-02 (danh sách phân trang) và CAT-04 (chi tiết).
 *
 * parentName: tên danh mục cha, null nếu là danh mục gốc.
 * activeProductCount: chỉ có trong response chi tiết (CAT-04).
 * @JsonInclude(NON_NULL): Không serialize null field → response gọn hơn.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryResponse {

    private Long id;
    private String name;
    private String slug;
    private String status;
    private Long parentId;
    private String parentName;

    /**
     * Số lượng sản phẩm ACTIVE thuộc danh mục này.
     * Chỉ có trong CAT-04 (chi tiết), null trong CAT-02 (danh sách).
     */
    private Long activeProductCount;

    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Factory method: Map từ Category Entity sang DTO (không kèm activeProductCount).
     * Dùng cho CAT-02 (danh sách phẳng).
     */
    public static CategoryResponse from(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .status(category.getStatus().name())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .parentName(category.getParent() != null ? category.getParent().getName() : null)
                .version(category.getVersion())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    /**
     * Factory method: Map từ Category Entity kèm activeProductCount.
     * Dùng cho CAT-04 (chi tiết).
     */
    public static CategoryResponse from(Category category, long activeProductCount) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .status(category.getStatus().name())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .parentName(category.getParent() != null ? category.getParent().getName() : null)
                .activeProductCount(activeProductCount)
                .version(category.getVersion())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
