package com.example.store_clothes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * ImportReceiptDetail Entity - Chi tiết từng mặt hàng trong Phiếu Nhập.
 *
 * Đây là bảng con (many-to-one) của ImportReceipt.
 * Mỗi dòng detail ghi nhận:
 *   - Biến thể sản phẩm nào được nhập (ProductVariant).
 *   - Số lượng nhập.
 *   - Giá nhập tại thời điểm nhập (snapshot giá - quan trọng cho kiểm toán).
 *
 * THIẾT KẾ QUAN TRỌNG - Snapshot giá nhập:
 * importPrice ở đây là giá nhập TẠI THỜI ĐIỂM LẬP PHIẾU, không phải giá hiện tại.
 * Cần phân biệt với ProductVariant.importPrice (giá nhập mới nhất - sẽ được cập nhật).
 * Snapshot này đảm bảo báo cáo lịch sử luôn chính xác dù giá nhập thay đổi sau này.
 *
 * Không dùng Soft Delete: Detail là bằng chứng kế toán, không được xóa.
 */
@Entity
@Table(name = "import_receipt_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportReceiptDetail extends BaseEntity {

    /**
     * Phiếu nhập mà dòng này thuộc về.
     * LAZY: Chỉ load ImportReceipt khi thực sự cần.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private ImportReceipt receipt;

    /**
     * Biến thể hàng hóa được nhập.
     * LAZY: Chỉ load ProductVariant khi cần - khi completeReceipt sẽ dùng
     * findByIdForUpdate() thay vì lazy load để có Pessimistic Lock.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    /**
     * Số lượng nhập. Phải > 0 (validate ở tầng DTO).
     */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /**
     * Giá nhập tại thời điểm lập phiếu (SNAPSHOT - không thay đổi sau khi tạo).
     * precision=12, scale=2: Hỗ trợ giá tối đa 9,999,999,999.99 VND.
     */
    @Column(name = "import_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal importPrice;
}
