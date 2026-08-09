package com.example.store_clothes.entity;

import com.example.store_clothes.util.UlidAttributeConverter;
import com.example.store_clothes.util.UlidGenerator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BaseUuidEntity — Lớp cha trừu tượng cho các Entity sử dụng định danh UUIDv7/ULID.
 *
 * CHIẾN LƯỢC LƯU TRỮ VÀ SINH ID:
 *  - ID kiểu Java: UUID (để tương thích tối đa với Hibernate/JPA và thư viện sẵn có).
 *  - ID kiểu DB: BINARY(16) trong MySQL (tiết kiệm không gian lưu trữ và tối ưu index).
 *  - Converter: UlidAttributeConverter thực hiện chuyển đổi tự động UUID ↔ BINARY(16).
 *  - Sinh ID: @PrePersist gọi UlidGenerator để tự sinh UUID dựa trên timestamp ULID (time-sortable, offline-first).
 *
 * MULTI-TENANCY:
 *  - @TenantId: Hibernate 6 tự động inject tenant_id vào mọi INSERT/SELECT/UPDATE.
 *  - Order và OrderItem kế thừa class này → tự động có tenant isolation.
 *  - updatable = false: Một record không thể "chuyển chủ" sang tenant khác.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseUuidEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    @Convert(converter = UlidAttributeConverter.class)
    private UUID id;

    /**
     * Định danh Tenant (Cửa hàng) sở hữu bản ghi này.
     * Hibernate 6 @TenantId tự động inject vào mọi câu SQL — không cần WHERE thủ công.
     */
    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    /**
     * Tự động sinh ID time-sortable (ULID dạng UUID) trước khi INSERT vào Database.
     * Cho phép Client tự truyền ID (offline-first). Nếu Client không truyền, Server sẽ sinh tự động.
     */
    @PrePersist
    protected void generateId() {
        if (this.id == null) {
            this.id = UlidGenerator.generateAsUuid();
        }
    }
}
