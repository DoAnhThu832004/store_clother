package com.example.store_clothes.service;

import com.example.store_clothes.dto.request.CreateCategoryRequest;
import com.example.store_clothes.dto.request.UpdateCategoryRequest;
import com.example.store_clothes.dto.response.CategoryResponse;
import com.example.store_clothes.dto.response.CategoryTreeResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * CategoryService — Interface định nghĩa contract cho module Danh mục.
 *
 * Nguyên tắc: Interface-based programming để dễ mock trong unit test.
 * Service trả về DTO, không bao giờ trả về Entity trực tiếp.
 */
public interface CategoryService {

    /**
     * CAT-01: Tạo danh mục mới.
     * - Auto-generate slug từ name, xử lý xung đột.
     * - Validate depth ≤ 2 (không tạo cháu).
     */
    CategoryResponse createCategory(CreateCategoryRequest request);

    /**
     * CAT-02: Danh sách phẳng phân trang với tìm kiếm theo tên.
     */
    Page<CategoryResponse> getCategories(String keyword, Pageable pageable);

    /**
     * CAT-03: Danh sách cây phân cấp (build in-memory).
     */
    List<CategoryTreeResponse> getCategoryTree();

    /**
     * CAT-04: Chi tiết danh mục kèm số lượng sản phẩm ACTIVE.
     */
    CategoryResponse getCategoryById(Long id);

    /**
     * CAT-05: Cập nhật danh mục với Optimistic Lock.
     * - Kiểm tra circular reference khi đổi parentId.
     */
    CategoryResponse updateCategory(Long id, UpdateCategoryRequest request);

    /**
     * CAT-06: Xóa mềm danh mục.
     * - Kiểm tra không có sản phẩm ACTIVE.
     * - Kiểm tra không có danh mục con active.
     * - Ghi AuditLog bất đồng bộ.
     */
    void deleteCategory(Long id);
}
