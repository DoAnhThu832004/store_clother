package com.example.store_clothes.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

/**
 * Product Entity - Đại diện cho một Sản phẩm gốc (Master Product).
 *
 * SENIOR NOTES:
 * 1. @SQLDelete: Override câu lệnh DELETE mặc định của Hibernate.
 *    Thay vì xóa vật lý (DELETE FROM products WHERE id=?), Hibernate sẽ
 *    chỉ cập nhật cờ is_deleted = true (Soft Delete - Xóa mềm).
 *
 * 2. @SQLRestriction: Thay thế @Where (deprecated trong Hibernate 6).
 *    Tự động thêm điều kiện "AND is_deleted = false" vào MỌI câu truy vấn
 *    liên quan đến entity này, đảm bảo record đã xóa mềm không bao giờ
 *    xuất hiện trong kết quả.
 *
 * 3. KHÔNG dùng @Data: Tránh StackOverflowError do toString() vòng lặp
 *    trên quan hệ song phương Product <-> ProductVariant.
 *    Chỉ dùng @Getter, @Setter, @Builder, @NoArgsConstructor, @AllArgsConstructor.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE products SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class Product extends BaseEntity {

    /**
     * Tên sản phẩm. Ví dụ: "Áo thun nam oversize".
     */
    @Column(nullable = false, length = 255)
    private String name;

    /**
     * Mã sản phẩm - duy nhất trong hệ thống. Ví dụ: "SP0001".
     * Không dùng @NaturalId ở đây vì không cần cache theo natural key phức tạp.
     */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /**
     * Mô tả chi tiết về sản phẩm (TEXT không giới hạn ký tự).
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Trạng thái sản phẩm. Dùng EnumType.STRING để DB lưu "ACTIVE"/"INACTIVE"
     * thay vì 0/1 - dễ đọc và an toàn khi refactor enum.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    /**
     * Danh sách biến thể của sản phẩm này.
     *
     * - mappedBy = "product": Chỉ định ProductVariant.product là bên sở hữu khóa ngoại.
     * - CascadeType.ALL: Mọi thao tác (persist, merge, remove...) trên Product sẽ
     *   được cascade xuống các Variant.
     * - orphanRemoval = true: Nếu một Variant bị xóa khỏi list này, nó sẽ tự động
     *   bị xóa khỏi DB.
     * - @Builder.Default: Bắt buộc khi dùng @Builder để khởi tạo giá trị mặc định.
     *   Nếu không có, @Builder sẽ set field này = null thay vì new ArrayList<>().
     */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();

    // =========================================================================
    // HELPER METHODS - Bidirectional Synchronization
    // Luôn dùng các helper method này thay vì trực tiếp thao tác với list.
    // Đảm bảo cả hai phía của quan hệ luôn được đồng bộ trong cùng một session.
    // =========================================================================

    /**
     * Thêm một Variant vào Product và gán ngược Product reference cho Variant.
     * Tránh lỗi quên gán product_id khi lưu Variant mới.
     */
    public void addVariant(ProductVariant variant) {
        variants.add(variant);
        variant.setProduct(this);
    }

    /**
     * Xóa một Variant khỏi Product và ngắt liên kết ngược.
     */
    public void removeVariant(ProductVariant variant) {
        variants.remove(variant);
        variant.setProduct(null);
    }
}
