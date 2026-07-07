package com.example.store_clothes.repository;

import com.example.store_clothes.entity.User;
import com.example.store_clothes.enums.RoleName;
import com.example.store_clothes.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository - Repository cho User entity.
 *
 * @SQLRestriction("is_deleted = false") tự động áp dụng cho tất cả query.
 * User đã bị xóa mềm sẽ không xuất hiện trong kết quả — bao gồm cả
 * findByUsername() dùng bởi Spring Security → tài khoản xóa mềm không đăng nhập được.
 *
 * 💡 Senior Note — Tại sao cần findByIdIgnoringSoftDelete()?
 * @SQLRestriction tự động inject "is_deleted = false" vào mọi JPQL/Criteria query.
 * Để check username/email trùng kể cả user đã bị xóa mềm (đề phòng username gốc
 * bị lưu với suffix "_deleted_xxx" nhưng vẫn cần check username gốc) → dùng native SQL.
 * Mục đích: Tránh tái sử dụng username đã từng tồn tại → lịch sử audit log sẽ bị confusing.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Tìm user theo username — dùng bởi UserDetailsServiceImpl.
     * Index idx_user_username đảm bảo query O(log n).
     */
    Optional<User> findByUsername(String username);

    /** Validate username unique khi tạo tài khoản mới (chỉ check active users). */
    boolean existsByUsername(String username);

    /** Validate email unique khi tạo tài khoản mới (chỉ check active users). */
    boolean existsByEmail(String email);

    /** Validate email unique khi update (loại trừ chính user đó). */
    boolean existsByEmailAndIdNot(String email, Long id);

    // =========================================================================
    // USR-01: Kiểm tra username/email kể cả soft-deleted (bypass @SQLRestriction)
    // =========================================================================

    /**
     * Tìm user theo ID — bypass @SQLRestriction để check kể cả đã soft-delete.
     *
     * Dùng trong USR-01 để validate username chưa tồn tại trong toàn bộ DB (kể cả deleted).
     * Lý do: Username của user bị xóa đã được append "_deleted_UNIX", nên username gốc
     * thực ra đã giải phóng → có thể tái sử dụng. Chỉ cần check ACTIVE users.
     *
     * 💡 Senior Note — Dùng native query bypass @SQLRestriction:
     * Nếu cần query user deleted (hiếm), dùng native SQL.
     * Ở đây existsByUsername() đã đủ vì username gốc được đổi khi xóa.
     *
     * @param id ID của user cần tìm
     * @return Optional<User> kể cả user đã bị soft-delete
     */
    @Query(value = "SELECT * FROM users WHERE id = :id", nativeQuery = true)
    Optional<User> findByIdIgnoringSoftDelete(@Param("id") Long id);

    /**
     * Kiểm tra username có tồn tại trong bảng users không (kể cả deleted).
     * Dùng để đảm bảo không có 2 active user cùng username (đã đủ bởi existsByUsername).
     * Native query bypass @SQLRestriction.
     *
     * @param username Username cần kiểm tra
     * @return true nếu username tồn tại (kể cả deleted rows)
     */
    @Query(value = "SELECT COUNT(*) > 0 FROM users WHERE username = :username", nativeQuery = true)
    boolean existsByUsernameIgnoringSoftDelete(@Param("username") String username);

    // =========================================================================
    // USR-02: Danh sách nhân viên — tìm kiếm theo keyword + roleName + phân trang
    // =========================================================================

    /**
     * Danh sách nhân viên với filter keyword (username HOẶC fullName) và roleName.
     *
     * 💡 Senior Note — Tại sao dùng JOIN r.roles trong JPQL thay vì subquery?
     * JPQL JOIN trên @ManyToMany collection sẽ sinh INNER JOIN với bảng user_roles.
     * Kết quả chỉ trả về user có ít nhất 1 role khớp → đúng business logic.
     * Spring Data tự xử lý phân trang qua Pageable.
     *
     * @param keyword  Tìm theo username HOẶC fullName (null = không filter)
     * @param roleName Filter theo tên role (null = không filter)
     * @param pageable Phân trang
     * @return Page<User>
     */
    @Query("""
        SELECT DISTINCT u FROM User u LEFT JOIN u.roles r
        WHERE (:keyword IS NULL OR
               LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:roleName IS NULL OR r.name = :roleName)
        ORDER BY u.createdAt DESC
        """)
    Page<User> findUsersWithFilter(
        @Param("keyword") String keyword,
        @Param("roleName") RoleName roleName,
        Pageable pageable
    );

    // =========================================================================
    // USR-05: Kiểm tra OWNER cuối cùng trước khi soft delete
    // =========================================================================

    /**
     * Đếm số OWNER đang ACTIVE (không bao gồm user đang bị xóa).
     *
     * 💡 Senior Note — Tại sao phải kiểm tra còn OWNER cuối cùng trước khi xóa?
     * Nếu xóa OWNER duy nhất còn lại → không ai có quyền quản lý hệ thống.
     * Cửa hàng sẽ bị lock hoàn toàn: không tạo được user mới, không khôi phục quyền.
     * Đây là "dead end" không thể phục hồi nếu không access trực tiếp DB.
     * Business rule: Phải luôn có ít nhất 1 OWNER active trong hệ thống.
     *
     * @return Số lượng OWNER đang ACTIVE
     */
    @Query("""
        SELECT COUNT(u) FROM User u JOIN u.roles r
        WHERE r.name = :roleName AND u.status = :status
        """)
    long countByRoleNameAndStatus(
        @Param("roleName") RoleName roleName,
        @Param("status") UserStatus status
    );

    // =========================================================================
    // USR-03: Thống kê số hóa đơn và phiếu nhập đã tạo
    // =========================================================================

    /**
     * Đếm số hóa đơn đã tạo bởi user — dùng customerId tham chiếu.
     *
     * 💡 Senior Note — Order hiện tại lưu customerId (Long) không phải createdByUserId.
     * Nếu muốn thống kê chính xác "cashier tạo bao nhiêu đơn" cần thêm field createdByUserId
     * vào Order entity. Tạm thời query dựa trên AuditLog action=CHECKOUT nếu có,
     * hoặc để service trả về 0 nếu chưa implement.
     * JPQL không thể query sang bảng khác nếu không có @ManyToOne.
     */
    @Query(value = """
        SELECT COUNT(*) FROM audit_logs
        WHERE user_id = :userId AND action = 'CHECKOUT'
        """, nativeQuery = true)
    long countOrdersCreatedByUser(@Param("userId") Long userId);

    /**
     * Đếm số phiếu nhập kho do user tạo — dựa trên AuditLog action=CREATE_IMPORT_RECEIPT.
     */
    @Query(value = """
        SELECT COUNT(*) FROM audit_logs
        WHERE user_id = :userId AND action = 'CREATE_IMPORT_RECEIPT'
        """, nativeQuery = true)
    long countImportsCreatedByUser(@Param("userId") Long userId);
}
