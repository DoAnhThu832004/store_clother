package com.example.store_clothes.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * Customer Entity — Đại diện cho Khách Hàng trong hệ thống bán lẻ.
 *
 * =====================================================================
 * THIẾT KẾ QUAN TRỌNG:
 * =====================================================================
 *
 * 1. SOFT DELETE với rename UNIQUE field:
 *    phone là UNIQUE → khi xóa mềm, @SQLDelete append "_deleted_<UNIX>"
 *    để giải phóng constraint, cho phép tạo KH mới cùng SĐT trong tương lai.
 *    Quy tắc số 3 bắt buộc trong toàn hệ thống.
 *
 * 2. loyaltyPoints — Điểm tích lũy:
 *    Chỉ được cộng/trừ qua luồng mua hàng (OrderService) hoặc điều chỉnh có kiểm soát.
 *    TUYỆT ĐỐI KHÔNG cho phép sửa trực tiếp qua API cập nhật thông tin KH.
 *
 * 3. totalSpent — Tổng tiền đã mua:
 *    Được cộng dồn mỗi khi khách thanh toán đơn hàng thành công.
 *    KHÔNG tính lại động từ bảng orders (hiệu năng + tránh inconsistency khi orders bị soft-delete).
 *    Đây là "denormalized aggregate" — pattern phổ biến trong hệ thống POS.
 *
 * 4. @Version — Optimistic Lock:
 *    Bảo vệ khi 2 Cashier/Manager cùng cập nhật thông tin khách hàng.
 *    Nếu xung đột → HTTP 409 Conflict → client load lại và retry.
 *
 * 💡 Senior Note — Tại sao KHÔNG cho phép restore KH bị xóa mềm tự động?
 *    (1) Quy tắc nghiệp vụ: KH cũ có thể có lý do hợp lệ để bị xóa (gian lận, yêu cầu GDPR).
 *    (2) Tính minh bạch: Auto-restore ẩn đi lịch sử xóa → gây nhầm lẫn cho OWNER/MANAGER.
 *    (3) Xác nhận có chủ đích: Người dùng phải chủ động "reactivate" → đảm bảo kiểm soát.
 *    (4) Tránh side effect: Restore có thể gây conflict điểm tích lũy, lịch sử mua, v.v.
 */
@Entity
@Table(
    name = "customers",
    indexes = {
        @Index(name = "idx_customer_phone",   columnList = "phone"),
        @Index(name = "idx_customer_name",    columnList = "name"),
        @Index(name = "idx_customer_deleted", columnList = "is_deleted")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = """
    UPDATE customers
    SET is_deleted = true,
        phone = CONCAT(phone, '_deleted_', UNIX_TIMESTAMP())
    WHERE id = ?
    """)
@SQLRestriction("is_deleted = false")
public class Customer extends BaseEntity {

    /**
     * Tên khách hàng. Bắt buộc, tối đa 200 ký tự.
     */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * Số điện thoại khách hàng.
     * UNIQUE: Mỗi KH có một SĐT riêng biệt.
     * Khi xóa mềm → @SQLDelete append "_deleted_<UNIX>" để giải phóng constraint,
     * cho phép tạo KH mới với cùng SĐT trong tương lai.
     */
    @Column(name = "phone", unique = true, length = 30)
    private String phone;

    /**
     * Email khách hàng. Không bắt buộc, không unique (khác với User).
     */
    @Column(name = "email", length = 150)
    private String email;

    /**
     * Ghi chú về khách hàng (ví dụ: "Khách VIP", "Thích màu tối").
     */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /**
     * Điểm tích lũy của khách hàng.
     *
     * QUY TẮC CẬP NHẬT:
     * - Cộng điểm: Sau mỗi lần mua hàng thành công (OrderService).
     * - Trừ điểm: Khi dùng điểm đổi quà/giảm giá.
     * - KHÔNG được sửa trực tiếp qua API update thông tin KH.
     *
     * @Builder.Default: Bắt buộc để Lombok Builder khởi tạo = 0 thay vì null.
     */
    @Column(name = "loyalty_points", nullable = false)
    @Builder.Default
    private Integer loyaltyPoints = 0;

    /**
     * Tổng số tiền khách đã chi tiêu (denormalized aggregate).
     *
     * CÔNG THỨC CẬP NHẬT (BẮT BUỘC TUÂN THỦ):
     *   totalSpent_mới = totalSpent_cũ + finalAmount (của đơn hàng vừa thanh toán)
     *
     * - precision=15: Hỗ trợ tổng chi tiêu tối đa 999 tỷ VND.
     * - scale=2: 2 chữ số thập phân.
     * - @Builder.Default: Khởi tạo = 0 khi tạo KH mới.
     */
    @Column(name = "total_spent", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalSpent = BigDecimal.ZERO;

    /**
     * @Version — Optimistic Locking.
     * Phát hiện xung đột khi 2 nhân viên cùng cập nhật thông tin cùng 1 KH.
     * → HTTP 409 Conflict, yêu cầu client load lại và retry.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
