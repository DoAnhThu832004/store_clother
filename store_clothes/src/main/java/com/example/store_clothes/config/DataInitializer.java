package com.example.store_clothes.config;

import com.example.store_clothes.entity.Role;
import com.example.store_clothes.enums.RoleName;
import com.example.store_clothes.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DataInitializer - Khởi tạo dữ liệu master khi ứng dụng khởi động.
 *
 * Implements ApplicationRunner: Chạy SAU KHI Spring context đã load xong,
 * đảm bảo tất cả Bean (Repository, Transaction Manager...) sẵn sàng.
 *
 * NHIỆM VỤ:
 * Seed 4 Role mặc định vào bảng roles nếu chưa tồn tại.
 * Sử dụng pattern "insert if not exists" — an toàn khi chạy nhiều lần.
 *
 * QUAN TRỌNG:
 * Không seed User admin mặc định trong production code.
 * Tài khoản admin đầu tiên nên được tạo thủ công hoặc qua script migration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("DataInitializer: Checking and seeding master data...");
        seedRoles();
        log.info("DataInitializer: Master data initialization completed.");
    }

    /**
     * Seed 4 roles mặc định — idempotent (chạy nhiều lần không bị lỗi).
     * Chỉ INSERT nếu role chưa tồn tại trong DB.
     */
    private void seedRoles() {
        seedRoleIfNotExists(
                RoleName.ROLE_OWNER,
                "Chủ cửa hàng — Toàn quyền quản lý hệ thống"
        );
        seedRoleIfNotExists(
                RoleName.ROLE_MANAGER,
                "Quản lý — Quản lý nhân viên, sản phẩm, xem báo cáo"
        );
        seedRoleIfNotExists(
                RoleName.ROLE_CASHIER,
                "Thu ngân — Thanh toán hóa đơn, xem sản phẩm"
        );
        seedRoleIfNotExists(
                RoleName.ROLE_WAREHOUSE_STAFF,
                "Nhân viên kho — Quản lý nhập xuất kho, kiểm kê"
        );
    }

    /** Tạo role nếu chưa tồn tại — Pattern safe to run multiple times. */
    private void seedRoleIfNotExists(RoleName roleName, String description) {
        if (roleRepository.findByName(roleName).isEmpty()) {
            roleRepository.save(Role.builder()
                    .name(roleName)
                    .description(description)
                    .build());
            log.info("Seeded role: {}", roleName);
        }
    }
}
