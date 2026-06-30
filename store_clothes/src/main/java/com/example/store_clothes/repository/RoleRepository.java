package com.example.store_clothes.repository;

import com.example.store_clothes.entity.Role;
import com.example.store_clothes.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * RoleRepository - Repository cho Role entity.
 *
 * Role là master data — được seeding khi ứng dụng khởi động.
 * Không cần Soft Delete vì roles không bao giờ bị xóa trong vòng đời hệ thống.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Tìm role theo tên enum — dùng khi gán quyền cho User mới.
     * Ví dụ: roleRepository.findByName(RoleName.ROLE_CASHIER)
     */
    Optional<Role> findByName(RoleName name);
}
