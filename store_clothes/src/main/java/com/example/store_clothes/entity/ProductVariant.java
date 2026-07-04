package com.example.store_clothes.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * ProductVariant Entity - Đại diện cho một Biến thể cụ thể của sản phẩm.
 * Ví dụ: Áo thun nam oversize - Màu Đen - Size L.
 *
 * SENIOR NOTES:
 * 1. @Index trên SKU và Barcode: Các trường này được dùng thường xuyên trong
 *    tìm kiếm (WHERE sku = ? / WHERE barcode = ?). Khai báo Index tại đây giúp
 *    Hibernate tự tạo index khi ddl-auto=update/create, tối ưu tốc độ query.
 *
 * 2. FetchType.LAZY trên @ManyToOne: Mặc định @ManyToOne là EAGER (luôn load).
 *    Điều này gây lỗi N+1 Query kinh điển: khi query 100 Variants, Hibernate sẽ
 *    phát sinh thêm 100 câu query để load Product cho từng Variant.
 *    Dùng LAZY: chỉ load Product khi thực sự gọi getProduct().
 *
 * 3. BigDecimal cho giá tiền: Tuyệt đối KHÔNG dùng float/double cho tiền tệ
 *    do sai số làm tròn. BigDecimal đảm bảo chính xác tuyệt đối.
 */
@Entity
@Table(
    name = "product_variants",
    indexes = {
        // Index cho tìm kiếm theo SKU - thường dùng nhất trong nghiệp vụ bán hàng
        @Index(name = "idx_variant_sku", columnList = "sku"),
        // Index cho tìm kiếm theo Barcode - dùng khi quét mã vạch
        @Index(name = "idx_variant_barcode", columnList = "barcode")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE product_variants SET is_deleted = true, sku = CONCAT(sku, '_deleted_', UNIX_TIMESTAMP()) WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class ProductVariant extends BaseEntity {

    /**
     * Stock Keeping Unit - Mã quản lý tồn kho nội bộ, duy nhất trên toàn hệ thống.
     * Ví dụ: "AOTD-DEN-L" (Áo thun dài - Đen - L)
     */
    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    /**
     * Mã vạch sản phẩm (EAN-13, QR Code...).
     * Có thể null với hàng không có barcode.
     */
    @Column(length = 50)
    private String barcode;

    /**
     * Màu sắc của biến thể. Ví dụ: "Đen", "Trắng", "Đỏ".
     */
    @Column(length = 50)
    private String color;

    /**
     * Kích cỡ của biến thể. Ví dụ: "S", "M", "L", "XL".
     */
    @Column(length = 50)
    private String size;

    /**
     * Giá nhập hàng. precision=12: tổng 12 chữ số, scale=2: 2 chữ số thập phân.
     * Hỗ trợ giá trị tối đa: 9,999,999,999.99 VND.
     */
    @Column(name = "import_price", precision = 12, scale = 2)
    private BigDecimal importPrice;

    /**
     * Giá bán lẻ cho khách hàng.
     */
    @Column(name = "sale_price", precision = 12, scale = 2)
    private BigDecimal salePrice;

    /**
     * Số lượng tồn kho hiện tại. Mặc định = 0 khi mới tạo.
     * @Builder.Default: Cần thiết khi dùng @Builder, tránh Hibernate set null.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer inventory = 0;

    /**
     * Trạng thái biến thể. Có thể INACTIVE riêng lẻ mà không ảnh hưởng Product cha.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    /**
     * Optimistic Lock Version — Phát hiện xung đột ghi đồng thời.
     *
     * 💡 Senior Note — Tại sao dùng Optimistic Lock (không phải Pessimistic) cho update giá?
     * - Optimistic: Không lock row ở DB. Chỉ kiểm tra version khi commit.
     *   → Throughput cao, phù hợp với thao tác cập nhật giá (ít xung đột thực tế).
     *   → Nếu 2 manager cùng update giá: người sau sẽ nhận HTTP 409 → load lại → retry.
     *   → Không gây deadlock, không block thread.
     * - Pessimistic: Lock row ngay khi đọc (SELECT FOR UPDATE).
     *   → Phù hợp với inventory (xung đột thường xuyên, cần nhất quán tuyệt đối).
     *   → Có thể gây deadlock nếu lock nhiều row theo thứ tự khác nhau.
     *
     * QUY TẮC: Giá thay đổi = Optimistic (low-conflict, high-throughput).
     *          Inventory thay đổi = Pessimistic (high-conflict, must-be-exact).
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Khóa ngoại liên kết về Product cha.
     *
     * CRITICAL: FetchType.LAZY bắt buộc - tránh N+1 Query.
     * Hibernate chỉ load Product khi gọi getProduct(), không load tự động.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}
