package com.example.store_clothes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * OrderItem Entity - Dòng chi tiết của một hóa đơn bán hàng.
 *
 * ============================================================
 * CRITICAL — SNAPSHOT DESIGN (ĐỌC TRƯỚC KHI SỬA):
 * ============================================================
 *
 * [1] priceAtSale (Giá bán tại thời điểm mua):
 *     - Được COPY từ ProductVariant.salePrice lúc checkout.
 *     - IMMUTABLE sau khi lưu — không bao giờ update.
 *     - Lý do: Giá sản phẩm thay đổi theo thời gian. Báo cáo doanh thu
 *       tháng 1/2025 phải reflect đúng giá khách thực trả, không phải
 *       giá hiện tại của sản phẩm năm 2026.
 *
 * [2] importPriceAtSale (Giá nhập tại thời điểm mua):
 *     - Được COPY từ ProductVariant.importPrice lúc checkout.
 *     - IMMUTABLE sau khi lưu — không bao giờ update.
 *     - Lý do: Tính lợi nhuận thực = priceAtSale - importPriceAtSale.
 *       Nếu query động từ bảng Product → giá nhập có thể đã thay đổi
 *       → báo cáo lợi nhuận sai hoàn toàn.
 *
 * [3] TUYỆT ĐỐI KHÔNG:
 *     - Không JOIN sang bảng products/product_variants để lấy giá
 *       khi tính doanh thu/lợi nhuận cũ.
 *     - Không thêm @ManyToOne tới ProductVariant với eager load tại đây.
 *     - Không UPDATE các trường snapshot sau khi record đã được tạo.
 * ============================================================
 */
@Entity
@Table(
    name = "order_items",
    indexes = {
        // Tìm kiếm items theo order — thường xuyên khi load chi tiết hóa đơn
        @Index(name = "idx_order_item_order_id", columnList = "order_id"),
        // Tìm lịch sử bán của một variant — dùng trong báo cáo top sản phẩm
        @Index(name = "idx_order_item_variant_id", columnList = "variant_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem extends BaseEntity {

    /**
     * Liên kết về hóa đơn cha.
     * LAZY: chỉ load Order khi thực sự cần, tránh N+1.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * ID của biến thể được bán — lưu thêm ID để tham chiếu khi cần.
     * Không dùng @ManyToOne tại đây để tránh query động khi tính báo cáo.
     *
     * Senior Note: Có thể xảy ra trường hợp variant bị xóa mềm sau khi đã bán.
     * Lưu variantId dạng Long thay vì FK bắt buộc giúp order_item vẫn đọc được.
     */
    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    /**
     * SKU của biến thể tại thời điểm bán — snapshot để dễ đọc báo cáo,
     * không cần JOIN sang bảng product_variants để lấy SKU.
     */
    @Column(name = "sku_at_sale", nullable = false, length = 50)
    private String skuAtSale;

    /**
     * Tên sản phẩm (màu + size) tại thời điểm bán.
     * Ví dụ: "Áo thun nam oversize - Đen - L"
     */
    @Column(name = "product_name_at_sale", nullable = false, length = 255)
    private String productNameAtSale;

    /**
     * Số lượng mua.
     */
    @Column(nullable = false)
    private Integer quantity;

    /**
     * [SNAPSHOT] Giá bán tại thời điểm giao dịch.
     *
     * Đây là trường BẮFC BUỘC PHẢI snapshot:
     * - Copy từ ProductVariant.salePrice trong OrderService.
     * - Không bao giờ query lại từ bảng product_variants sau này.
     * - Đảm bảo báo cáo doanh thu chính xác bất kể giá thay đổi sau.
     */
    @Column(name = "price_at_sale", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAtSale;

    /**
     * [SNAPSHOT] Giá nhập tại thời điểm giao dịch.
     *
     * Đây là trường BẮBC BUỘC PHẢI snapshot:
     * - Copy từ ProductVariant.importPrice trong OrderService.
     * - Không bao giờ query lại từ bảng product_variants sau này.
     * - Đảm bảo tính lợi nhuận (profit = priceAtSale - importPriceAtSale) chính xác.
     */
    @Column(name = "import_price_at_sale", nullable = false, precision = 12, scale = 2)
    private BigDecimal importPriceAtSale;

    /**
     * Thành tiền dòng = priceAtSale × quantity.
     * Snapshot luôn — không tính lại sau.
     */
    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;
}
