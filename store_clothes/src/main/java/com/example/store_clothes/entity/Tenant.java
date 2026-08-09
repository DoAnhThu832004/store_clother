package com.example.store_clothes.entity;

import com.example.store_clothes.enums.TenantStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tenant Entity — Đại diện cho một "Cửa hàng" trong hệ thống SaaS.
 *
 * THIẾT KẾ QUAN TRỌNG:
 * 1. KHÔNG kế thừa BaseEntity hay TenantAwareBaseEntity.
 *    Lý do: Tenant là entity gốc (root) — nó không thuộc về tenant nào cả.
 *    Kế thừa TenantAwareBaseEntity sẽ tạo ra self-reference vô nghĩa.
 *
 * 2. Không có @SQLRestriction("is_deleted = false"):
 *    Super Admin cần thấy tất cả tenant kể cả đã hủy — không áp dụng soft delete.
 *    Thay vào đó dùng status = SUSPENDED / EXPIRED để quản lý vòng đời.
 *
 * 3. code là định danh duy nhất phụ (Natural Key):
 *    Dùng để tạo subdomain: {code}.yoursaas.com.
 *    Không bao giờ thay đổi sau khi tạo (updatable = false).
 *
 * 4. subscriptionExpiredAt: Ngày hết hạn gói đăng ký.
 *    Khi null → gói không giới hạn thời gian (phù hợp với giai đoạn thử nghiệm).
 *    Một Scheduled job sẽ kiểm tra và chuyển status → EXPIRED khi đến hạn.
 */
@Entity
@Table(
    name = "tenants",
    indexes = {
        @Index(name = "idx_tenant_code",   columnList = "code",   unique = true),
        @Index(name = "idx_tenant_status", columnList = "status")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    /**
     * ID tự tăng — dùng làm tenant_id trong tất cả bảng nghiệp vụ.
     * Kiểu Long để tương thích với @TenantId của Hibernate 6.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tên cửa hàng hiển thị. Ví dụ: "Thời trang Thu Hà".
     */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * Mã định danh duy nhất — không thay đổi sau khi đăng ký.
     * Dùng làm subdomain hoặc prefix trong URL.
     * Ví dụ: "thuha-store" → thuha-store.yoursaas.com
     * Quy tắc: chỉ gồm chữ thường, số, và dấu gạch ngang.
     */
    @Column(name = "code", nullable = false, unique = true, length = 100, updatable = false)
    private String code;

    /**
     * Trạng thái hoạt động của cửa hàng.
     * Mặc định ACTIVE khi mới đăng ký.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;

    /**
     * Ngày hết hạn gói đăng ký.
     * null = gói không giới hạn (dev/internal tenants).
     * Dùng LocalDate thay vì LocalDateTime vì chỉ cần độ chính xác theo ngày.
     */
    @Column(name = "subscription_expired_at")
    private LocalDate subscriptionExpiredAt;

    /**
     * Email liên hệ của OWNER. Dùng để gửi thông báo hết hạn, hóa đơn, v.v.
     */
    @Column(name = "contact_email", length = 150)
    private String contactEmail;

    /**
     * Số điện thoại liên hệ của OWNER.
     */
    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    /**
     * Ghi chú nội bộ của Super Admin (không hiển thị cho OWNER).
     */
    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    // =========================================================================
    // Audit Fields — Tự động set bởi JPA Auditing (không cần kế thừa BaseEntity)
    // =========================================================================

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // =========================================================================
    // Business Methods
    // =========================================================================

    /**
     * Kiểm tra tenant có đang hoạt động và chưa hết hạn subscription hay không.
     * Dùng ở TenantVerificationFilter hoặc aspect trước khi xử lý request.
     */
    public boolean isOperational() {
        if (status != TenantStatus.ACTIVE) {
            return false;
        }
        if (subscriptionExpiredAt == null) {
            return true; // Gói không giới hạn
        }
        return !LocalDate.now().isAfter(subscriptionExpiredAt);
    }
}
