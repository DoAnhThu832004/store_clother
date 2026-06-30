package com.example.store_clothes.controller;

import com.example.store_clothes.dto.request.ChangePasswordRequest;
import com.example.store_clothes.dto.request.LoginRequest;
import com.example.store_clothes.dto.response.ApiResponse;
import com.example.store_clothes.dto.response.AuthResponse;
import com.example.store_clothes.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController - Controller xử lý các API xác thực.
 *
 * Base URL: /api/v1/auth
 *
 * PUBLIC (không cần JWT):
 *   POST /api/v1/auth/login       → Đăng nhập
 *   POST /api/v1/auth/refresh     → Làm mới access token
 *
 * PROTECTED (cần JWT):
 *   GET  /api/v1/auth/me          → Thông tin người dùng hiện tại
 *   PUT  /api/v1/auth/me/password → Đổi mật khẩu
 *
 * Controller CỰC KỲ MỎNG — chỉ nhận request và delegate cho Service.
 * Không có bất kỳ logic nghiệp vụ nào ở đây.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Xác thực và phân quyền — JWT based")
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/v1/auth/login
     *
     * Đăng nhập — endpoint PUBLIC, không cần JWT.
     * @Valid kích hoạt Bean Validation trên LoginRequest.
     * Thành công: HTTP 200 + AuthResponse (accessToken, refreshToken, userInfo).
     * Thất bại: HTTP 401 (GlobalExceptionHandler xử lý BadCredentialsException).
     */
    @PostMapping("/login")
    @Operation(summary = "Đăng nhập — trả về JWT access token và refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    /**
     * POST /api/v1/auth/refresh
     *
     * Cấp lại access token từ refresh token — endpoint PUBLIC.
     * refreshToken truyền qua query param để đơn giản hóa client implementation.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Lấy access token mới từ refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @RequestParam String refreshToken) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(refreshToken)));
    }

    /**
     * GET /api/v1/auth/me
     *
     * Thông tin người dùng đang đăng nhập — cần JWT.
     * JwtAuthFilter đã set SecurityContext, AuthService đọc username từ đó.
     */
    @GetMapping("/me")
    @Operation(summary = "Thông tin người dùng hiện tại (từ JWT)")
    public ResponseEntity<ApiResponse<AuthResponse.UserInfo>> getCurrentUser() {
        return ResponseEntity.ok(ApiResponse.success(authService.getCurrentUser()));
    }

    /**
     * PUT /api/v1/auth/me/password
     *
     * Đổi mật khẩu — cần JWT.
     * Phải cung cấp mật khẩu cũ để xác minh danh tính.
     * Sau khi thành công: Token cũ vẫn còn hiệu lực đến khi hết hạn (24h).
     * Nếu cần invalidate token ngay → cần implement Token Blacklist (ngoài scope).
     */
    @PutMapping("/me/password")
    @Operation(summary = "Đổi mật khẩu (yêu cầu mật khẩu cũ)")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công"));
    }
}
