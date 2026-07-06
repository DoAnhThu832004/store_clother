package com.example.store_clothes.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

/**
 * Category Entity — Đại diện cho Danh mục sản phẩm.
 *
 * 💡 Senior Note — Tại sao giới hạn depth = 2 (Root → Child, không có Grandchild)?
 * Trong hệ thống bán lẻ thời trang, cấu trúc danh mục thường chỉ cần 2 cấp:
 *   Cấp 1 (Root): "Thời trang Nam", "Thời trang Nữ", "Phụ kiện"
 *   Cấp 2 (Child): "Áo", "Quần", "Giày dép"
 * Tránh depth > 2 vì:
 *   (1) UI/UX: Menu 3 cấp trở lên rất khó navigate trên mobile.
 *   (2) Query: Recursive CTE phức tạp, dễ gây N+1 problem.
 *   (3) Business: KiotViet, Shopee, Lazada đều dùng tối đa 2-3 cấp.
 *   (4) Data Integrity: Flat structure dễ quản lý hơn khi xóa mềm theo cascade.
 *
 * Soft Delete: @SQLDelete rename slug trước khi set is_deleted=true
 * để giải phóng UNIQUE constraint cho slug.
 *
 * @Version (Optimistic Lock): Bảo vệ khi 2 Manager cùng cập nhật danh mục.
 */
@Entity
@Table(
    name = "categories",
    indexes = {
        @Index(name = "idx_category_slug",   columnList = "slug"),
        @Index(name = "idx_category_parent",  columnList = "parent_id"),
        @Index(name = "idx_category_deleted", columnList = "is_deleted")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = """
    UPDATE categories
    SET is_deleted = true,
        slug = CONCAT(slug, '_deleted_', UNIX_TIMESTAMP())
    WHERE id = ?
    """)
@SQLRestriction("is_deleted = false")
public class Category extends BaseEntity {

    /**
     * Tên danh mục. Ví dụ: "Thời trang Nam", "Áo khoác".
     */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /**
     * Slug URL-friendly được tự động sinh từ name.
     * Ví dụ: "thoi-trang-nam", "ao-khoac".
     * UNIQUE để đảm bảo mỗi danh mục có đường dẫn riêng biệt.
     * Khi xóa mềm → @SQLDelete append "_deleted_<UNIX>" để giải phóng constraint.
     */
    @Column(name = "slug", nullable = false, unique = true, length = 200)
    private String slug;

    /**
     * Danh mục cha. null nếu là danh mục gốc (Root).
     * Self-referencing Many-to-One: nhiều danh mục con thuộc 1 danh mục cha.
     * LAZY load để tránh eager fetch gây N+1 khi query danh sách.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    /**
     * Danh sách danh mục con.
     * mappedBy = "parent": Category.parent là bên sở hữu FK.
     * LAZY: Không tự động load con khi query danh mục cha.
     * Dùng khi cần build tree (CAT-03).
     */
    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Category> children = new ArrayList<>();

    /**
     * Trạng thái danh mục: ACTIVE / INACTIVE.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CategoryStatus status;

    /**
     * @Version — Optimistic Locking.
     *
     * 💡 Senior Note — Tại sao cần Optimistic Lock cho Category?
     * Trong môi trường multi-tenant (nhiều Manager cùng làm việc):
     *   - Manager A đọc Category{id=1, name="Áo", version=3}.
     *   - Manager B cũng đọc, cập nhật name → version trở thành 4 và commit.
     *   - Manager A cập nhật → Hibernate detect version mismatch (3 ≠ 4).
     *   → Ném ObjectOptimisticLockingFailureException → HTTP 409.
     * Đây là cơ chế "last write wins" được thay bằng "detect conflict và thông báo".
     * Ưu điểm: Không lock DB row → hiệu năng cao cho đọc nhiều, ghi ít.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
