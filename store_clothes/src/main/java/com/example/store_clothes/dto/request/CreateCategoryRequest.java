package com.example.store_clothes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO nhận dữ liệu khi tạo mới Danh mục sản phẩm (CAT-01).
 *
 * parentId: nullable — null = danh mục gốc (Root), có giá trị = danh mục con.
 * status: bắt buộc — "ACTIVE" hoặc "INACTIVE".
 */
public record CreateCategoryRequest(

        @NotBlank(message = "Tên danh mục không được để trống")
        @Size(max = 150, message = "Tên danh mục không quá 150 ký tự")
        String name,

        /**
         * ID danh mục cha. null nếu tạo danh mục gốc.
         * Service sẽ validate:
         * - Parent phải tồn tại và chưa bị xóa.
         * - Parent không được có cha (depth chỉ tối đa = 2).
         */
        Long parentId,

        @NotNull(message = "Trạng thái danh mục không được để trống")
        String status

) {}
