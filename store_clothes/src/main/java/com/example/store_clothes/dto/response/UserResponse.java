package com.example.store_clothes.dto.response;

import com.example.store_clothes.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * UserResponse — DTO cho danh sách nhân viên (USR-02).
 *
 * KHÔNG chứa passwordHash, refreshToken hoặc bất kỳ thông tin nhạy cảm nào.
 * Thiết kế compact — chỉ các field cần thiết cho màn hình danh sách.
 */
@Getter
@Builder
public class UserResponse {

    private Long id;

    /** Tên đăng nhập — readonly, không cho sửa qua API. */
    private String username;

    /** Họ tên đầy đủ. */
    private String fullName;

    /** Email (nullable). */
    private String email;

    /** Số điện thoại (nullable). */
    private String phone;

    /** Trạng thái tài khoản: ACTIVE / INACTIVE / LOCKED. */
    private UserStatus status;

    /**
     * Danh sách tên vai trò (Set<String> thay vì Set<Role> để không expose internal entity).
     * Ví dụ: {"ROLE_CASHIER"}, {"ROLE_MANAGER"}.
     */
    private Set<String> roles;

    /** Thời điểm tạo tài khoản. */
    private LocalDateTime createdAt;

    /** Thời điểm đăng nhập gần nhất (null nếu chưa bao giờ đăng nhập). */
    private LocalDateTime lastLoginAt;
}
