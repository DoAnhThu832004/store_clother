package com.example.store_clothes.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * DTO trả về cấu trúc cây danh mục (CAT-03).
 *
 * Schema response:
 * [
 *   {
 *     "id": 1, "name": "Thời trang Nam", "slug": "thoi-trang-nam",
 *     "children": [
 *       { "id": 3, "name": "Áo", "slug": "ao", "children": [] },
 *       { "id": 4, "name": "Quần", "slug": "quan", "children": [] }
 *     ]
 *   }
 * ]
 *
 * 💡 Senior Note — Tại sao build tree in-memory thay vì CTE?
 * MySQL CTE:
 *   + 1 query, DB xử lý toàn bộ
 *   - Syntax CTE khác nhau giữa MySQL 5.7 (không hỗ trợ) và MySQL 8.0
 *   - Phức tạp khi kết hợp với @SQLRestriction filter
 * In-Memory (Java):
 *   + Tương thích mọi version DB, dễ maintain
 *   + Dễ áp dụng các filter phức tạp (is_deleted, status)
 *   - Tải toàn bộ danh mục vào RAM → ổn với < 10,000 rows
 * Kết luận: Với hệ thống bán lẻ vừa-nhỏ (< 500 danh mục), in-memory là best-practice.
 * Nếu scale lên hàng chục nghìn danh mục → xem xét lại với Redis cache hoặc CTE.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryTreeResponse {

    private Long id;
    private String name;
    private String slug;
    private String status;
    private Long parentId;

    /**
     * Danh sách con. Null khi là danh mục lá (no children).
     * @JsonInclude(NON_NULL) đảm bảo null children không serialize → response gọn.
     */
    private List<CategoryTreeResponse> children;
}
