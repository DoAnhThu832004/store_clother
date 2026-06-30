package com.example.store_clothes.entity;

import com.example.store_clothes.enums.RoleName;
import jakarta.persistence.*;
import lombok.*;

/**
 * Role Entity - Đại diện cho một vai trò trong hệ thống RBAC.
 *
 * Không kế thừa BaseEntity vì Role không cần Soft Delete,
 * createdAt/updatedAt — đây là dữ liệu master cố định được seeding lúc khởi tạo.
 *
 * Các role được seeding sẵn khi ứng dụng khởi động thông qua DataInitializer.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tên vai trò — lưu dạng String (ROLE_OWNER, ROLE_MANAGER, ...).
     * unique = true: Mỗi role chỉ tồn tại 1 bản ghi trong DB.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 30)
    private RoleName name;

    /**
     * Mô tả phạm vi quyền hạn của vai trò này.
     */
    @Column(name = "description", length = 200)
    private String description;
}
