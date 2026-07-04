package com.example.store_clothes.entity;

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
