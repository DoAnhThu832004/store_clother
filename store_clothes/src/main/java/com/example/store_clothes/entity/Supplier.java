package com.example.store_clothes.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * Supplier Entity - Đại diện cho Nhà Cung Cấp hàng hóa.
 *
 * THIẾT KẾ QUAN TRỌNG:
 * - Trường debtAmount (công nợ) phải luôn được cập nhật theo công thức:
 *   Debt_mới = Debt_cũ + TotalAmount - PaidAmount
 * - Mặc định debtAmount = 0 khi tạo mới nhà cung cấp.
 * - KHÔNG tự ý reset debtAmount về 0 ngoài quy trình thanh toán công nợ.
 *
 * Soft Delete: Kế thừa is_deleted từ BaseEntity.
 * Nhà cung cấp có thể bị vô hiệu hóa (xóa mềm) mà không mất lịch sử giao dịch.
 */
@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted = false")
public class Supplier extends BaseEntity {

    /**
     * Tên nhà cung cấp. Ví dụ: "Công ty TNHH Dệt may ABC".
     */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * Số điện thoại liên hệ của nhà cung cấp.
     */
    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * Địa chỉ nhà cung cấp.
     */
    @Column(name = "address", length = 500)
    private String address;

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
}
