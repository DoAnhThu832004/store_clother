package com.example.store_clothes.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * Supplier Entity — Đại diện cho Nhà Cung Cấp hàng hóa.
 *
 * THIẾT KẾ QUAN TRỌNG:
 * - Trường debtAmount (công nợ) phải luôn được cập nhật theo công thức:
 *   Debt_mới = Debt_cũ + TotalAmount - PaidAmount
 * - Mặc định debtAmount = 0 khi tạo mới nhà cung cấp.
 * - KHÔNG tự ý reset debtAmount về 0 ngoài quy trình thanh toán công nợ.
 *
 * Soft Delete: @SQLDelete rename phone trước khi set is_deleted=true.
 * phone là UNIQUE field → phải rename để giải phóng constraint,
 * cho phép NCC mới dùng lại số điện thoại đó trong tương lai.
 *
 * 💡 Senior Note — Tại sao không xóa NCC khi còn công nợ, dù là soft delete?
 * (1) Nghiệp vụ: NCC bị xóa mềm không còn xuất hiện trong danh sách,
 *     nhưng kế toán vẫn cần truy vết công nợ → confusing nếu NCC "biến mất".
 * (2) Tính toàn vẹn dữ liệu: debtAmount > 0 nghĩa là hệ thống đang "nợ" tiền NCC.
 *     Xóa NCC khi chưa quyết toán có thể dẫn đến mất bút toán kế toán.
 * (3) Kiểm toán: Auditor cần NCC còn active để đối chiếu với sổ cái.
 * (4) Quy trình đúng: Phải thanh toán hết nợ (ImportReceipt payment) → debt = 0 → xóa.
 *
 * @Version (Optimistic Lock): Bảo vệ cập nhật đồng thời (nhiều Manager cùng sửa NCC).
 */
@Entity
@Table(
    name = "suppliers",
    indexes = {
        @Index(name = "idx_supplier_phone",   columnList = "phone"),
        @Index(name = "idx_supplier_deleted", columnList = "is_deleted")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = """
    UPDATE suppliers
    SET is_deleted = true,
        phone = CONCAT(phone, '_deleted_', UNIX_TIMESTAMP())
    WHERE id = ?
    """)
@SQLRestriction("is_deleted = false")
public class Supplier extends BaseEntity {

    /**
     * Tên nhà cung cấp. Ví dụ: "Công ty TNHH Dệt may ABC".
     */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * Số điện thoại liên hệ của nhà cung cấp.
     * UNIQUE: Mỗi NCC có số điện thoại riêng biệt.
     * Khi xóa mềm → @SQLDelete append "_deleted_<UNIX>" để giải phóng constraint.
     */
    @Column(name = "phone", unique = true, length = 20)
    private String phone;

    /**
     * Email liên hệ của nhà cung cấp.
     */
    @Column(name = "email", length = 150)
    private String email;

    /**
     * Địa chỉ nhà cung cấp.
     */
    @Column(name = "address", length = 500)
    private String address;

    /**
     * Mã số thuế (Tax Code) của nhà cung cấp.
     * Dùng cho xuất hóa đơn VAT và báo cáo thuế.
     */
    @Column(name = "tax_code", length = 20)
    private String taxCode;

    /**
     * Tổng công nợ hiện tại của nhà cung cấp.
     *
     * CÔNG THỨC CẬP NHẬT (BẮT BUỘC TUÂN THỦ):
     *   Debt_mới = Debt_cũ + TotalAmount - PaidAmount
     *
     * - precision=15: Tổng tối đa 15 chữ số (hỗ trợ đến 999 tỷ VND).
     * - scale=2: 2 chữ số thập phân.
     * - @Builder.Default: Cần thiết để Lombok Builder khởi tạo = 0 thay vì null.
     */
    @Column(name = "debt_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal debtAmount = BigDecimal.ZERO;

    /**
     * @Version — Optimistic Locking.
     * Phát hiện xung đột khi 2 Manager cùng cập nhật thông tin NCC.
     * → HTTP 409 Conflict, yêu cầu client load lại và retry.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
