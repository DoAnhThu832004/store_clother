package com.example.store_clothes.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * AuditLog Entity - Bảng nhật ký bất biến (Immutable / Append-Only).
 *
 * THIẾT KẾ BẤT BIẾN:
 * - Không kế thừa BaseEntity (tránh is_deleted, updatedAt không cần thiết).
 * - Chỉ có @CreatedDate — không có updatedAt.
 * - Không có @Setter: Một khi đã INSERT, không được phép UPDATE bất kỳ trường nào.
 * - @NoArgsConstructor: Cần cho Hibernate khi load từ DB.
 * - @Builder: Dùng để tạo instance trong AuditLogService.
 *
 * CHIẾN LƯỢC INDEX (3 indexes phục vụ 3 kịch bản tra cứu):
 * - idx_audit_user:     "Nhân viên X đã làm gì trong hệ thống?"
 * - idx_audit_created:  "Sự kiện nào xảy ra trong khoảng thời gian T1 đến T2?"
 * - idx_audit_resource: "Đối tượng Y (Order/Product) đã thay đổi như thế nào?"
 */
@Entity
@Table(
    name = "audit_logs",
    indexes = {
        // Tra cứu lịch sử hành động theo nhân viên
        @Index(name = "idx_audit_user", columnList = "user_id"),
        // Lọc log theo khoảng thời gian
        @Index(name = "idx_audit_created", columnList = "created_at"),
        // Truy vết lịch sử biến động của một đối tượng cụ thể
        @Index(name = "idx_audit_resource", columnList = "resource_type, resource_id")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter                 // Chỉ @Getter, không @Setter — đảm bảo immutability
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID người dùng thực hiện hành động.
     * Có thể null nếu hành động từ hệ thống (scheduler, startup...).
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * Username tại thời điểm ghi log — snapshot để tránh JOIN khi tra cứu.
     * Nhân viên có thể đổi username sau này, log vẫn giữ username gốc.
     */
    @Column(name = "username", length = 100)
    private String username;

    /**
     * Hành động thực hiện. Ví dụ: LOGIN, LOGOUT, CHANGE_PASSWORD,
     * CHECKOUT, COMPLETE_IMPORT, CANCEL_IMPORT, UPDATE_PRICE, LOGIN_FAILED.
     */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    /**
     * Loại đối tượng bị tác động. Ví dụ: USER, ORDER, IMPORT_RECEIPT, PRODUCT.
     * null nếu hành động không liên quan đến đối tượng cụ thể (LOGIN...).
     */
    @Column(name = "resource_type", length = 50)
    private String resourceType;

    /**
     * ID của đối tượng bị tác động.
     * null nếu không áp dụng.
     */
    @Column(name = "resource_id")
    private Long resourceId;

    /**
     * JSON chứa trạng thái trước và sau khi thay đổi.
     * Format: {"before": {...}, "after": {...}}
     * hoặc mô tả sự kiện: {"ip": "...", "userAgent": "...", "reason": "..."}
     *
     * Dùng TEXT thay vì JSON column để tương thích tối đa với MySQL/MariaDB.
     * Schema bảng không cần thay đổi khi cấu trúc đối tượng nghiệp vụ thay đổi.
     */
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    /**
     * Địa chỉ IP của client gửi request.
     */
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    /**
     * User-Agent của browser/app gửi request.
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Thời điểm tạo log — tự động set bởi JPA Auditing.
     * updatable = false: Bảo vệ khỏi bị ghi đè khi UPDATE (không nên có nhưng phòng thủ).
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
