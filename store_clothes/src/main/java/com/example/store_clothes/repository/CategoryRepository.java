package com.example.store_clothes.repository;

import com.example.store_clothes.entity.Category;
import com.example.store_clothes.entity.CategoryStatus;
import com.example.store_clothes.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * CategoryRepository — Tầng truy cập dữ liệu cho Danh mục sản phẩm.
 *
 * LƯU Ý: @SQLRestriction("is_deleted = false") trên Category Entity
 * tự động lọc các danh mục đã xóa mềm khỏi MỌI query thông thường.
 *
 * 💡 Senior Note — In-Memory Tree Building (CAT-03):
 * Thay vì dùng MySQL CTE (WITH RECURSIVE) để build cây phân cấp,
 * ta query ALL categories một lần → build cây trong Java memory.
 * Lý do: CTE syntax khác nhau giữa MySQL 5.x và 8.0, MariaDB, PostgreSQL.
 * Trade-off:
 *   - MySQL CTE: 1 query phức tạp, hiệu năng tốt nếu index đúng,
 *     nhưng phụ thuộc DB-specific syntax → khó port.
 *   - In-Memory: 1 query đơn giản, build O(n) trong Java,
 *     chỉ phù hợp khi tổng số danh mục < ~10,000 rows.
 * Với hệ thống thời trang quy mô nhỏ-vừa (< 500 danh mục), in-memory tối ưu hơn.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Kiểm tra slug đã tồn tại chưa (dùng để sinh slug unique).
     */
    boolean existsBySlug(String slug);

    /**
     * Tìm danh mục theo slug (case-sensitive, slug đã lowercase).
     */
    Optional<Category> findBySlug(String slug);

    /**
     * Danh sách phẳng (paginated) với tìm kiếm theo tên.
     * kèm parentName (LEFT JOIN để lấy tên cha ngay trong 1 query).
     *
     * 💡 Senior Note — Tại sao LEFT JOIN thay vì FETCH JOIN?
     * FETCH JOIN sẽ load toàn bộ parent object vào memory.
     * Ở đây ta chỉ cần parent.name → dùng JPQL scalar projection
     * hoặc để Service map sau khi load (đơn giản hơn).
     */
    @Query("""
        SELECT c FROM Category c
        WHERE (:keyword IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY c.createdAt DESC
        """)
    Page<Category> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Lấy toàn bộ danh mục để build cây trong memory (CAT-03).
     * Không phân trang. Dùng khi số lượng danh mục nhỏ (< 10,000).
     * Fetch JOIN parent để tránh N+1 khi build tree.
     */
    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent ORDER BY c.parent.id ASC NULLS FIRST, c.id ASC")
    List<Category> findAllForTree();

    /**
     * Đếm số sản phẩm ACTIVE trong một danh mục (dùng cho CAT-04 và CAT-06).
     *
     * 💡 Senior Note — Count thay vì Load:
     * Chỉ cần số lượng, không cần load toàn bộ Product entity vào memory.
     * COUNT query chỉ trả về 1 số nguyên → cực kỳ nhẹ.
     */
    @Query("""
        SELECT COUNT(p) FROM Product p
        WHERE p.category.id = :categoryId
        AND p.status = com.example.store_clothes.entity.ProductStatus.ACTIVE
        AND p.isDeleted = false
        """)
    long countActiveProductsByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Đếm số danh mục con ACTIVE (is_deleted = false tự động từ @SQLRestriction).
     * Dùng cho CAT-06: không cho xóa cha khi còn con active.
     */
    long countByParentId(Long parentId);

    /**
     * Lấy danh mục cùng với danh sách con để kiểm tra circular reference (CAT-05).
     */
    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.children WHERE c.id = :id")
    Optional<Category> findByIdWithChildren(@Param("id") Long id);

    /**
     * Tìm tất cả danh mục con của một cha (dùng khi kiểm tra circular reference).
     */
    List<Category> findAllByParentId(Long parentId);

    /**
     * Kiểm tra danh mục có tồn tại theo status không.
     */
    boolean existsByIdAndStatus(Long id, CategoryStatus status);
}
