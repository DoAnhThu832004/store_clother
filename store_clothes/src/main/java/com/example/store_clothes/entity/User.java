package com.example.store_clothes.entity;

import com.example.store_clothes.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User Entity - Đại diện cho một người dùng trong hệ thống.
 *
 * SENIOR NOTES:
 * 1. implements UserDetails: Tích hợp trực tiếp với Spring Security.
 *    User entity là UserDetails → không cần class adapter riêng.
 *
 * 2. FetchType.EAGER cho roles — Ngoại lệ hợp lý có CHỦ ĐÍCH:
 *    Spring Security gọi getAuthorities() tại JwtAuthFilter (ngoài transaction).
 *    Nếu LAZY → LazyInitializationException tại Filter layer.
 *    Tần suất load roles = mỗi request → EAGER không gây N+1 ở đây.
 *
 * 3. Soft Delete: @SQLDelete + @SQLRestriction — nhân viên nghỉ việc
 *    không xóa data lịch sử (hóa đơn, nhập kho đã thực hiện).
 *
 * 4. KHÔNG dùng @Data: Tránh StackOverflowError do circular toString()
 *    nếu sau này thêm quan hệ bidirectional.
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_user_username", columnList = "username"),
        @Index(name = "idx_user_email", columnList = "email")
    }
)
@SQLDelete(sql = """
    UPDATE users
    SET is_deleted = true,
        status = 'LOCKED',
        username = CONCAT(username, '_deleted_', UNIX_TIMESTAMP()),
        email = CONCAT(IFNULL(email,''), '_deleted_', UNIX_TIMESTAMP())
    WHERE id = ?
    """)
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity implements UserDetails {

    /**
     * Tên đăng nhập — duy nhất toàn hệ thống.
     */
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    /**
     * Mật khẩu đã mã hóa BCrypt. Không bao giờ lưu plain-text.
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * Họ tên đầy đủ của nhân viên.
     */
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * Trạng thái tài khoản. Mặc định = ACTIVE khi tạo mới.
     * @Builder.Default: Bắt buộc khi dùng @Builder với giá trị mặc định.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * Danh sách vai trò — EAGER load có chủ đích.
     * Xem giải thích chi tiết tại Senior Note #2 ở trên.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    /**
     * Thời điểm đăng nhập gần nhất.
     * Được cập nhật trong AuthService.login() sau khi xác thực thành công.
     * Nullable: null nếu user chưa bao giờ đăng nhập (tài khoản mới tạo chưa login).
     *
     * 💡 Senior Note — Không dùng @LastModifiedDate ở đây:
     * @LastModifiedDate cập nhật mỗi lần entity bị sửa (kể cả đổi role, reset password).
     * lastLoginAt chỉ nên được set khi user đăng nhập thành công,
     * không cập nhật khi admin sửa thông tin user.
     */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    // =========================================================================
    // UserDetails Implementation — Spring Security Contract
    // =========================================================================

    /**
     * Convert Set<Role> → Set<GrantedAuthority> cho Spring Security.
     * SimpleGrantedAuthority("ROLE_OWNER") → hasRole("OWNER") trong @PreAuthorize.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toSet());
    }

    /** Spring Security dùng getPassword() — NOT getPasswordHash(). */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** Tài khoản không hết hạn — hệ thống POS không có tính năng expire account. */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Tài khoản bị khóa khi status = LOCKED.
     * Spring Security tự ném LockedException nếu false.
     */
    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    /** Credentials không hết hạn — JWT đảm nhiệm vòng đời token. */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Tài khoản chỉ hoạt động khi status = ACTIVE.
     * Spring Security tự ném DisabledException nếu false.
     */
    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
