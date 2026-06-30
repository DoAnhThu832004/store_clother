package com.example.store_clothes.repository;

import com.example.store_clothes.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * AuditLogRepository - Repository cho bảng audit_logs (append-only).
 *
 * Không có save/delete operation tùy chỉnh ở đây.
 * Toàn bộ việc ghi log được thực hiện qua AuditLogService.log() bất đồng bộ.
 *
 * Các query phục vụ giao diện quản trị (Admin Dashboard).
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Tra cứu log theo userId — dùng index idx_audit_user.
     * Phân trang để tránh load quá nhiều bản ghi.
     *
     * @param userId   ID nhân viên cần tra cứu
     * @param pageable Thông tin phân trang + sắp xếp
     */
    Page<AuditLog> findByUserId(Long userId, Pageable pageable);

    /**
     * Lọc log theo khoảng thời gian — dùng index idx_audit_created.
     *
     * @param from     Thời điểm bắt đầu
     * @param to       Thời điểm kết thúc
     * @param pageable Thông tin phân trang
     */
    Page<AuditLog> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    /**
     * Truy vết lịch sử biến động của một đối tượng cụ thể.
     * Dùng composite index idx_audit_resource (resource_type, resource_id).
     *
     * Ví dụ: Xem toàn bộ thay đổi của Order ID=123.
     *
     * @param resourceType Loại đối tượng: "ORDER", "PRODUCT", "IMPORT_RECEIPT"...
     * @param resourceId   ID đối tượng cụ thể
     * @param pageable     Thông tin phân trang
     */
    Page<AuditLog> findByResourceTypeAndResourceId(
            String resourceType, Long resourceId, Pageable pageable);

    /**
     * Tra cứu kết hợp: log của một nhân viên trong khoảng thời gian.
     * Dùng khi Manager muốn kiểm tra hoạt động của nhân viên cụ thể.
     *
     * @param userId ID nhân viên
     * @param from   Thời điểm bắt đầu
     * @param to     Thời điểm kết thúc
     * @param pageable Thông tin phân trang
     */
    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId " +
           "AND a.createdAt BETWEEN :from AND :to " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLog> findByUserIdAndTimeRange(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
