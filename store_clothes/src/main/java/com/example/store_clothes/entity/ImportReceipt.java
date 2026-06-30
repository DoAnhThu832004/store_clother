package com.example.store_clothes.entity;

import com.example.store_clothes.enums.ImportReceiptStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * ImportReceipt Entity - Đại diện cho Phiếu Nhập Hàng (đầu phiếu).
 *
 * THIẾT KẾ QUAN TRỌNG:
 * 1. Phiếu nhập có 2 trạng thái chính: DRAFT → COMPLETED.
 *    Chỉ khi COMPLETED thì tồn kho và công nợ mới được cập nhật.
 *
 * 2. totalAmount = Σ (quantity × importPrice) của tất cả ImportReceiptDetail.
 *    Phải được tính và set trước khi save.
 *
 * 3. paidAmount = số tiền đã thanh toán ngay khi nhập hàng.
 *    paidAmount <= totalAmount (nghiệp vụ cần validate).
 *
 * 4. receiptCode: Mã phiếu nhập duy nhất, được sinh tự động theo format:
 *    PN-YYYYMMDD-XXXX (ví dụ: PN-20260626-0001).
 *
 * Không dùng Soft Delete (@SQLDelete) vì phiếu nhập là bằng chứng giao dịch
 * - không bao giờ được xóa khỏi DB (kể cả xóa mềm).
 */
@Entity
@Table(name = "import_receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportReceipt extends BaseEntity {

    /**
     * Mã phiếu nhập hàng - duy nhất trên toàn hệ thống.
     * Format: PN-YYYYMMDD-XXXX. Ví dụ: "PN-20260626-0001".
     * Được sinh tự động bởi ImportReceiptService khi tạo phiếu nháp.
     */
    @Column(name = "receipt_code", nullable = false, unique = true, length = 30)
    private String receiptCode;

    /**
     * Nhà cung cấp của phiếu nhập này.
     * LAZY: Chỉ load Supplier khi thực sự cần - tránh N+1 query.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    /**
     * Tổng giá trị hàng hóa trong phiếu nhập.
     * Công thức: Σ (detail.quantity × detail.importPrice) cho mọi detail.
     */
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    /**
     * Số tiền đã thanh toán ngay khi nhập hàng.
     * Có thể = 0 nếu mua chịu hoàn toàn, hoặc = totalAmount nếu thanh toán đủ.
     * Giá trị này không được âm và không vượt quá totalAmount.
     */
    @Column(name = "paid_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal paidAmount;

    /**
     * Trạng thái phiếu nhập. Lưu dạng chuỗi "DRAFT"/"COMPLETED"/"CANCELLED".
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ImportReceiptStatus status;

    /**
     * Ghi chú thêm của người lập phiếu. Không bắt buộc.
     */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /**
     * Danh sách chi tiết hàng hóa trong phiếu.
     *
     * - CascadeType.ALL: Persist/Merge/Remove detail cùng với phiếu.
     * - orphanRemoval = true: Xóa detail không còn thuộc phiếu này.
     * - @Builder.Default: Khởi tạo danh sách rỗng thay vì null.
     */
    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ImportReceiptDetail> details = new ArrayList<>();
}
