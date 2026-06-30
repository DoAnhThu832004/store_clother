package com.example.store_clothes.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/**
 * AuthResponse - DTO trả về sau khi đăng nhập hoặc refresh token thành công.
 *
 * Chứa:
 * - accessToken: JWT để gọi các API protected.
 * - refreshToken: JWT dùng để lấy lại accessToken khi hết hạn.
 * - expiresIn: Thời hạn access token (giây) — frontend dùng để countdown.
 * - userInfo: Thông tin người dùng hiển thị trên UI (menu, tên, role).
 */
@Getter
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;     // Đơn vị: giây (seconds)
    private UserInfo userInfo;

    @Getter
    @Builder
    public static class UserInfo {
        private Long id;
        private String username;
        private String fullName;
        private String email;
        private Set<String> roles;
    }
}
