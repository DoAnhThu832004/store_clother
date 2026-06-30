package com.example.store_clothes.service;

import com.example.store_clothes.dto.request.ChangePasswordRequest;
import com.example.store_clothes.dto.request.LoginRequest;
import com.example.store_clothes.dto.response.AuthResponse;
import com.example.store_clothes.entity.User;
import com.example.store_clothes.exception.BusinessException;
import com.example.store_clothes.repository.UserRepository;
import com.example.store_clothes.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * AuthService - Tầng nghiệp vụ xác thực và phân quyền.
 *
 * ====================================================================
 * TÍCH HỢP AUDIT LOG — QUY TẮC BẮT BUỘC:
 * ====================================================================
 *
 * [1] LUÔN log SAU KHI nghiệp vụ chính hoàn thành (thành công hoặc thất bại).
 *     Gọi auditLogService.log() là lời gọi CUỐI CÙNG trong mỗi luồng xử lý.
 *
 * [2] KHÔNG bao bọc lời gọi auditLogService.log() trong try-catch tại đây.
 *     AuditLogService.log() đã tự bọc try-catch bên trong.
 *     Nếu log thất bại → chỉ có log.error nội bộ, không ảnh hưởng gì.
 *
 * [3] Log NGAY CẢ KHI thất bại (LOGIN_FAILED):
 *     Đây là thông tin bảo mật quan trọng — phát hiện brute-force attack.
 *     Phải log trước khi rethrow exception.
 *
 * [4] details chứa JSON mô tả context. Format tùy use case:
 *     Login thành công: {"roles": ["ROLE_CASHIER"], "ip": "..."}
 *     Login thất bại:   {"reason": "BadCredentials", "attemptedUsername": "..."}
 *     Đổi mật khẩu:     {"result": "success"}
 * ====================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    // Inject AuditLogService để ghi log bất đồng bộ
    // Phải inject qua constructor (không dùng self-invocation) để @Async hoạt động
    private final AuditLogService auditLogService;

    // =========================================================================
    // ĐĂNG NHẬP
    // =========================================================================

    /**
     * Xác thực người dùng và cấp phát JWT token.
     *
     * LUỒNG AUDIT LOG:
     * - Thành công → log action=LOGIN, details chứa roles và thông tin session.
     * - Thất bại (sai mật khẩu, tài khoản khóa...) → log action=LOGIN_FAILED
     *   TRƯỚC KHI rethrow exception để không mất log.
     *
     * @Transactional(readOnly = true): Phương thức này chỉ đọc DB.
     * authenticationManager.authenticate() sẽ trigger một transaction con riêng.
     *
     * @param request Thông tin đăng nhập từ client
     * @return AuthResponse chứa JWT và thông tin người dùng
     * @throws BadCredentialsException Nếu username/password sai
     * @throws LockedException         Nếu tài khoản bị khóa
     * @throws DisabledException       Nếu tài khoản bị vô hiệu hóa
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        // ---------------------------------------------------------------
        // BƯỚC 1: Xác thực qua AuthenticationManager
        // Spring Security sẽ:
        //   1. Gọi UserDetailsServiceImpl.loadUserByUsername(username)
        //   2. So sánh password bằng BCrypt
        //   3. Kiểm tra isEnabled(), isAccountNonLocked()
        // Nếu thất bại → ném BadCredentialsException/LockedException/DisabledException
        // ---------------------------------------------------------------
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException | LockedException | DisabledException ex) {
            // ---------------------------------------------------------------
            // LOG THẤT BẠI — Phải log TRƯỚC KHI rethrow
            //
            // userId = null vì không biết user này có tồn tại không
            // (tránh lộ thông tin: "có tồn tại nhưng sai mật khẩu" vs "không tồn tại")
            //
            // action = LOGIN_FAILED — dùng trong báo cáo bảo mật phát hiện brute-force
            // ---------------------------------------------------------------
            String failReason = ex.getClass().getSimpleName();
            String details = String.format(
                    "{\"reason\": \"%s\", \"attemptedUsername\": \"%s\"}",
                    failReason, request.getUsername()
            );
            // null userId: Không lộ thông tin tài khoản có tồn tại hay không
            auditLogService.log(null, request.getUsername(), "LOGIN_FAILED", details);

            log.warn("Login failed for username={}: {}", request.getUsername(), failReason);
            throw ex; // Re-throw để GlobalExceptionHandler bắt và trả HTTP 401
        }

        // ---------------------------------------------------------------
        // BƯỚC 2: Lấy User từ Authentication principal
        // User entity đã được load EAGER với roles → sẵn sàng dùng ngay
        // ---------------------------------------------------------------
        User user = (User) auth.getPrincipal();

        // ---------------------------------------------------------------
        // BƯỚC 3: Tạo JWT với extra claims
        // Nhúng userId, fullName, roles vào payload → không cần query DB tại Filter
        // ---------------------------------------------------------------
        Map<String, Object> extraClaims = Map.of(
                "userId",   user.getId(),
                "fullName", user.getFullName(),
                "roles",    user.getAuthorities().stream()
                                .map(a -> a.getAuthority())
                                .collect(Collectors.toList())
        );

        String accessToken  = jwtUtil.generateToken(user, extraClaims);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        // ---------------------------------------------------------------
        // BƯỚC 4: LOG THÀNH CÔNG — Bất đồng bộ, không blocking
        //
        // auditLogService.log() sẽ được đẩy sang thread riêng (nhờ @Async).
        // Response trả về client NGAY LẬP TỨC, không chờ ghi log xong.
        //
        // details chứa roles để phục vụ báo cáo "Ai đăng nhập với quyền gì?"
        // ---------------------------------------------------------------
        String rolesStr = user.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.joining(", "));
        String loginDetails = String.format(
                "{\"roles\": [%s], \"result\": \"success\"}",
                user.getAuthorities().stream()
                        .map(a -> "\"" + a.getAuthority() + "\"")
                        .collect(Collectors.joining(", "))
        );
        // Ghi log bất đồng bộ — resourceType/resourceId = null vì đây là hành động xác thực
        auditLogService.log(user.getId(), user.getUsername(), "LOGIN", "USER", user.getId(), loginDetails);

        log.info("User logged in successfully: username={}, roles={}", user.getUsername(), rolesStr);

        // ---------------------------------------------------------------
        // BƯỚC 5: Build và trả response
        // ---------------------------------------------------------------
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(86400L) // 24h = 86400 giây
                .userInfo(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .roles(user.getAuthorities().stream()
                                .map(a -> a.getAuthority())
                                .collect(Collectors.toSet()))
                        .build())
                .build();
    }

    // =========================================================================
    // REFRESH TOKEN
    // =========================================================================

    /**
     * Cấp lại Access Token mới từ Refresh Token hợp lệ.
     *
     * Không cần audit log ở đây — refresh là thao tác tự động của client,
     * không phải hành động chủ động của người dùng.
     *
     * @Transactional(readOnly = true): Chỉ đọc DB.
     */
    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        String username = jwtUtil.extractUsername(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("Token không hợp lệ"));

        if (!jwtUtil.validateToken(refreshToken, user)) {
            throw new BusinessException("Refresh token đã hết hạn hoặc không hợp lệ");
        }

        Map<String, Object> extraClaims = Map.of(
                "userId",   user.getId(),
                "fullName", user.getFullName(),
                "roles",    user.getAuthorities().stream()
                                .map(a -> a.getAuthority())
                                .collect(Collectors.toList())
        );
        String newAccessToken = jwtUtil.generateToken(user, extraClaims);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .expiresIn(86400L)
                .build();
    }

    // =========================================================================
    // ĐỔI MẬT KHẨU
    // =========================================================================

    /**
     * Đổi mật khẩu cho người dùng đang đăng nhập.
     *
     * LUỒNG AUDIT LOG:
     * - Thành công → log action=CHANGE_PASSWORD sau khi commit transaction.
     * - Thất bại (sai mật khẩu cũ) → BusinessException được ném,
     *   @Transactional rollback, log KHÔNG được ghi (vì chưa đến bước log).
     *
     * WHY @Transactional không có readOnly:
     * Method này UPDATE user.passwordHash → cần transaction ghi.
     *
     * @param request Dữ liệu đổi mật khẩu (oldPassword, newPassword)
     * @throws BusinessException Nếu mật khẩu cũ sai hoặc mật khẩu mới trùng mật khẩu cũ
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        // Lấy username từ SecurityContext (đã được JwtAuthFilter set trước đó)
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("Người dùng không tồn tại"));

        // ---------------------------------------------------------------
        // VALIDATE #1: Mật khẩu cũ phải đúng
        // BCrypt.matches(rawPassword, encodedPassword) — không bao giờ compare plain-text
        // ---------------------------------------------------------------
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException("Mật khẩu cũ không đúng");
        }

        // ---------------------------------------------------------------
        // VALIDATE #2: Mật khẩu mới phải khác mật khẩu cũ
        // Tránh tình huống người dùng "đổi" sang mật khẩu giống cũ (bảo mật kém)
        // ---------------------------------------------------------------
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException("Mật khẩu mới phải khác mật khẩu cũ");
        }

        // ---------------------------------------------------------------
        // CẬP NHẬT MẬT KHẨU — Encode bằng BCrypt trước khi lưu
        // Không bao giờ lưu plain-text vào DB
        // ---------------------------------------------------------------
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed successfully for user: {}", username);

        // ---------------------------------------------------------------
        // LOG SAU KHI THÀNH CÔNG — Bất đồng bộ
        //
        // Gọi log SAU userRepository.save() và TRƯỚC khi method kết thúc.
        // @Transactional sẽ commit khi method return bình thường.
        //
        // Lưu ý: @Async chạy trên thread khác, không nằm trong transaction chính.
        // Propagation.REQUIRES_NEW trong AuditLogService đảm bảo điều này.
        //
        // details: Không lưu old/new password dù đã encode — Best Practice bảo mật.
        // ---------------------------------------------------------------
        String details = String.format(
                "{\"result\": \"success\", \"userId\": %d}", user.getId()
        );
        auditLogService.log(user.getId(), username, "CHANGE_PASSWORD", "USER", user.getId(), details);
    }

    // =========================================================================
    // THÔNG TIN NGƯỜI DÙNG HIỆN TẠI
    // =========================================================================

    /**
     * Lấy thông tin của người dùng đang đăng nhập.
     *
     * @Transactional(readOnly = true): Chỉ đọc, tối ưu Hibernate.
     * @return UserInfo với id, username, fullName, email, roles
     */
    @Transactional(readOnly = true)
    public AuthResponse.UserInfo getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("Người dùng không tồn tại"));

        return AuthResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .roles(user.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .collect(Collectors.toSet()))
                .build();
    }
}
