package com.example.store_clothes.entity;

import com.example.store_clothes.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * StockHistory Entity - Thẻ Kho Bất Biến (Immutable Audit Trail).
 *
 * ⚠️  NGUYÊN TẮC BẤT BIẾN TUYỆT ĐỐI:
 * Bảng stock_history CHỈ ĐƯỢC PHÉP INSERT.
 * NGHIÊM CẤM viết bất kỳ hàm UPDATE hay DELETE nào cho bảng này.
 * Đây là yêu cầu kiểm toán (audit trail) - mọi sự thay đổi đều phải
 * được ghi nhận bằng một bản ghi mới, không bao giờ sửa bản ghi cũ.
 *
 * THIẾT KẾ:
 * 1. KHÔNG kế thừa BaseEntity: StockHistory không có updatedAt và isDeleted.
 *    Hai trường này không có ý nghĩa với một bản ghi bất biến.
 *    createdAt được set thủ công = LocalDateTime.now() khi tạo.
 *
 * 2. Không có @Setter: Chỉ có @Getter để đảm bảo immutability sau khi tạo.
 *    Mọi giá trị phải được truyền qua Builder hoặc Constructor.
 *
 * 3. variantId lưu dạng Long (không phải @ManyToOne ProductVariant):
 *    Tránh lazy-load không cần thiết khi đọc thẻ kho.
 *    referenceCode lưu dạng String để không phụ thuộc vào receipt cụ thể.
 *
 * 4. balanceBefore + balanceAfter: Ghi nhận trạng thái tồn kho trước và sau
 *    khi biến động, tạo ra chuỗi liên tục kiểm chứng được (audit chain).
 */
@Entity
@Table(name = "stock_history",
        indexes = {
                // Index để truy vấn lịch sử theo biến thể - dùng thường xuyên trong báo cáo
                @Index(name = "idx_stock_history_variant_id", columnList = "variant_id"),
                // Index để truy vấn theo mã phiếu tham chiếu
                @Index(name = "idx_stock_history_reference_code", columnList = "reference_code")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID của biến thể sản phẩm bị biến động tồn kho.
     * Lưu dạng Long thay vì @ManyToOne để tránh N+1 khi load thẻ kho.
     */
    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    /**
     * Số lượng biến động.
     * - Dương (+): Nhập kho (IMPORT, RETURN)
     * - Âm (-): Xuất kho (EXPORT)
     */
    @Column(name = "change_quantity", nullable = false)
    private Integer changeQuantity;

    /**
     * Loại giao dịch: IMPORT / EXPORT / ADJUSTMENT / RETURN.
     * Lưu dạng chuỗi để dễ đọc trực tiếp trong DB.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    /**
     * Mã phiếu tham chiếu (receipt_code của phiếu nhập, hoặc mã đơn hàng...).
     * Dùng để tra cứu nguồn gốc biến động tồn kho.
     */
    @Column(name = "reference_code", length = 30)
    private String referenceCode;

    /**
     * Tồn kho TRƯỚC KHI biến động. Dùng để kiểm chứng tính liên tục của chuỗi.
     */
    @Column(name = "balance_before", nullable = false)
    private Integer balanceBefore;

    /**
     * Tồn kho SAU KHI biến động. Phải = balanceBefore + changeQuantity.
     */
    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    /**
     * Thời điểm ghi nhận biến động. Set thủ công thay vì dùng @CreatedDate
     * vì StockHistory không kế thừa BaseEntity / không dùng JPA Auditing.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
