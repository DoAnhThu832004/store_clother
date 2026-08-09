package com.example.store_clothes.repository;

import com.example.store_clothes.entity.Tenant;
import com.example.store_clothes.enums.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * TenantRepository — Truy cập dữ liệu bảng tenants.
 *
 * LƯU Ý QUAN TRỌNG — KHÔNG CÓ @TenantId ở đây:
 * Tenant entity KHÔNG kế thừa TenantAwareBaseEntity, do đó Hibernate
 * KHÔNG tự động thêm WHERE tenant_id = ? vào query của repository này.
 * Đây là hành vi chính xác — TenantRepository phải đọc được tất cả tenants
 * bất kể ngữ cảnh hiện tại là ai.
 *
 * Repository này được dùng bởi:
 *   - ScheduledTenantUtil: Lấy danh sách tenant để batch job
 *   - TenantOnboardingService: Tạo tenant mới khi đăng ký
 *   - Super Admin APIs: Quản lý tenant
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    /**
     * Tìm tenant theo code (mã định danh dùng làm subdomain).
     * Dùng khi đăng ký để kiểm tra code đã tồn tại chưa.
     *
     * Native query để bypass Hibernate multi-tenancy query plan validation lúc startup.
     * Hibernate 6 validate JPQL queries qua resolver nhưng KHÔNG validate native SQL.
     */
    @Query(value = "SELECT * FROM tenants WHERE code = :code LIMIT 1", nativeQuery = true)
    Optional<Tenant> findByCode(@org.springframework.data.repository.query.Param("code") String code);

    /**
     * Kiểm tra code đã tồn tại trong hệ thống chưa.
     * Native query để tránh Hibernate multi-tenancy validation error.
     */
    @Query(value = "SELECT COUNT(*) FROM tenants WHERE code = :code", nativeQuery = true)
    long countByCode(@org.springframework.data.repository.query.Param("code") String code);

    default boolean existsByCode(String code) {
        return countByCode(code) > 0;
    }

    /**
     * Lấy tất cả tenant ACTIVE có subscription đã quá hạn.
     * Dùng bởi TenantSubscriptionExpiryJob để batch update status → EXPIRED.
     *
     * @param cutoffDate Ngày so sánh (thường là LocalDate.now())
     * @return Danh sách tenant cần được chuyển sang EXPIRED
     */
    @Query(value = "SELECT * FROM tenants WHERE status = 'ACTIVE' AND subscription_expired_at IS NOT NULL AND subscription_expired_at < :cutoffDate", nativeQuery = true)
    List<Tenant> findAllActiveExpiredBefore(@org.springframework.data.repository.query.Param("cutoffDate") java.time.LocalDate cutoffDate);

    /**
     * Lấy danh sách ID của tất cả tenant đang ACTIVE.
     * Dùng bởi ScheduledTenantUtil.forEachActiveTenant().
     *
     * Chỉ lấy ID (không load toàn bộ entity) để tiết kiệm memory khi hệ thống
     * có nhiều tenant.
     */
    @Query(value = "SELECT id FROM tenants WHERE status = :status", nativeQuery = true)
    List<Long> findAllIdsByStatus(@org.springframework.data.repository.query.Param("status") String status);

    /**
     * Shortcut to get all ACTIVE tenant IDs.
     */
    default List<Long> findAllActiveIds() {
        return findAllIdsByStatus(TenantStatus.ACTIVE.name());
    }

    /**
     * Lấy danh sách tenant theo status — dùng cho Super Admin management APIs.
     */
    @Query(value = "SELECT * FROM tenants WHERE status = :status", nativeQuery = true)
    List<Tenant> findAllByStatus(@org.springframework.data.repository.query.Param("status") String status);
}
