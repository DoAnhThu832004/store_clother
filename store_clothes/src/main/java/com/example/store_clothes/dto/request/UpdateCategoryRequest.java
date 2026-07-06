package com.example.store_clothes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO nhận dữ liệu khi cập nhật Danh mục sản phẩm (CAT-05).
 *
 * Tương tự CreateCategoryRequest nhưng có thêm logic kiểm tra
 * circular reference khi thay đổi parentId.
 */
public record UpdateCategoryRequest(

        @NotBlank(message = "Tên danh mục không được để trống")
        @Size(max = 150, message = "Tên danh mục không quá 150 ký tự")
        String name,

        /**
         * ID danh mục cha mới. null = chuyển thành danh mục gốc.
         * Service sẽ kiểm tra:
         * - Không gán danh mục này làm con của chính nó.
         * - Không gán danh mục này làm con của con nó (circular).
         */
        Long parentId,

        @NotNull(message = "Trạng thái danh mục không được để trống")
        String status

) {}
