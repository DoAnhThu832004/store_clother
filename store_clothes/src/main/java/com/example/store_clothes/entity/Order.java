package com.example.store_clothes.entity;

import com.example.store_clothes.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Order Entity - Phiếu bán hàng POS (đầu hóa đơn).
 *
 * SENIOR NOTES:
 * 1. Không lưu tổng tiền bằng cách tính động (SUM items) — đã snapshot tại thời điểm checkout.
 * 2. orderCode sinh tự động trong Service để đảm bảo uniqueness.
 * 3. Quan hệ 1-N với OrderItem (cascade ALL + orphanRemoval).
 * 4. @SQLDelete + @SQLRestriction: Soft Delete nhất quán với phần còn lại của hệ thống.
 */
@Entity
@Table(
    name = "orders",
    indexes = {
        @Index(name = "idx_order_code", columnList = "order_code", unique = true),
        @Index(name = "idx_order_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE orders SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
public class Order extends BaseUuidEntity {

    /**
     * Mã hóa đơn duy nhất. Format: HD-YYYYMMDD-XXXX.
     * Được sinh tự động trong OrderService.
     */
    @Column(name = "order_code", nullable = false, unique = true, length = 30)
    private String orderCode;

    /**
     * Tổng tiền hàng (trước chiết khấu, trước thuế).
     * Snapshot tại thời điểm checkout — KHÔNG tính lại từ bảng Product sau này.
     */
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    /**
     * Tiền khách trả. Dùng để tính tiền thừa trả khách.
     */
    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal paidAmount;

    /**
     * Tiền thừa trả lại khách = paidAmount - totalAmount.
     */
    @Column(name = "change_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal changeAmount;

    /**
     * ID khách hàng (nullable — null = khách vãng lai không cần đăng ký).
     *
     * 💡 Senior Note — Tại sao lưu customerId dạng Long thay vì @ManyToOne Customer?
     * (1) Khách vãng lai: Không phải lần nào cũng có KH đăng ký → nullable FK đơn giản.
     * (2) Soft delete an toàn: Nếu Customer bị xóa mềm, Order vẫn giữ nguyên lịch sử.
     *     @ManyToOne LAZY load sẽ ném LazyInitializationException nếu KH bị xóa mềm
     *     và @SQLRestriction filter ra. Long ID luôn tồn tại trong row.
     * (3) Hiệu năng: Không JOIN sang bảng customers khi chỉ cần xử lý đơn hàng.
     */
    @Column(name = "customer_id", nullable = true)
    private Long customerId;

    /**
     * Trạng thái hóa đơn.
     * Mặc định = COMPLETED ngay sau khi checkout (POS thanh toán ngay lập tức).
     * Chuyển sang REFUNDED khi OWNER/MANAGER hủy đơn và hoàn kho.
     *
     * @Builder.Default: Bắt buộc để Lombok Builder khởi tạo đúng giá trị mặc định.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.COMPLETED;

    /**
     * Ghi chú tự do của thu ngân (ví dụ: "Khách mua cho con").
     */
    @Column(columnDefinition = "TEXT")
    private String note;

    /**
     * Danh sách dòng hàng hóa trong hóa đơn.
     * cascade ALL: mọi thao tác trên Order cascade xuống OrderItem.
     * orphanRemoval: nếu xóa item khỏi list → tự xóa khỏi DB.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    /** Helper: Thêm item vào hóa đơn và đồng bộ 2 chiều. */
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
