package com.example.store_clothes.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * BaseEntity: Lớp cha trừu tượng cho mọi Entity trong hệ thống.
 * - @MappedSuperclass: Hibernate sẽ không tạo bảng riêng cho class này,
 *   thay vào đó các field sẽ được "ánh xạ" vào bảng của class con.
 * - @EntityListeners(AuditingEntityListener.class): Kích hoạt JPA Auditing
 *   để tự động điền giá trị cho @CreatedDate và @LastModifiedDate.
 * - Yêu cầu @EnableJpaAuditing trên class @Configuration (đã có trong AppConfig).
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * @CreatedDate: Hibernate tự động set giá trị khi record được INSERT lần đầu.
     * updatable = false: Đảm bảo trường này không bao giờ bị ghi đè khi UPDATE.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * @LastModifiedDate: Hibernate tự động cập nhật mỗi khi record được UPDATE.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Cờ Soft Delete. Mặc định là false (chưa xóa).
     * Kết hợp với @SQLDelete và @SQLRestriction ở class con để hoạt động.
     */
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
