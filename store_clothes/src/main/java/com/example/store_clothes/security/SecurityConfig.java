package com.example.store_clothes.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfig - Cấu hình Spring Security toàn cục.
 *
 * THIẾT KẾ:
 * - Stateless: Không dùng Session (STATELESS) — mỗi request tự xác thực qua JWT.
 * - CSRF disabled: Không cần với REST API (không có form submit, không có cookie session).
 * - @EnableMethodSecurity: Kích hoạt @PreAuthorize, @PostAuthorize tại Service/Controller.
 *
 * PHÂN QUYỀN ENDPOINT:
 * - /api/v1/auth/login + /api/v1/auth/refresh: Public — không cần token.
 * - Swagger UI: Public trong môi trường dev.
 * - Tất cả còn lại: Phải có JWT hợp lệ.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthFilter jwtAuthFilter;

    /**
     * Chuỗi bộ lọc bảo mật chính.
     *
     * JwtAuthFilter được thêm VÀO TRƯỚC UsernamePasswordAuthenticationFilter
     * để JWT được xác thực trước khi Spring Security xử lý form-based auth.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Tắt CSRF — không cần với stateless REST API
            .csrf(AbstractHttpConfigurer::disable)

            // Cấu hình phân quyền theo URL
            .authorizeHttpRequests(auth -> auth

                // -------------------------------------------------------
                // PUBLIC ENDPOINTS — Không cần JWT
                // -------------------------------------------------------
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh"
                ).permitAll()

                // Swagger UI & API docs (chỉ dùng trong dev, cần restrict trên production)
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()

                // -------------------------------------------------------
                // PROTECTED ENDPOINTS — Phân quyền chi tiết theo role
                // -------------------------------------------------------

                // Quản lý nhân viên (User): Chỉ OWNER mới được tạo/xóa nhân viên
                .requestMatchers(HttpMethod.POST, "/api/v1/users/**").hasRole("OWNER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/users/**").hasRole("OWNER")

                // Nhập kho: OWNER, MANAGER, WAREHOUSE_STAFF
                .requestMatchers("/api/v1/import-receipts/**")
                    .hasAnyRole("OWNER", "MANAGER", "WAREHOUSE_STAFF")

                // Bán hàng (checkout): OWNER, MANAGER, CASHIER
                .requestMatchers("/api/v1/orders/**")
                    .hasAnyRole("OWNER", "MANAGER", "CASHIER")

                // Sản phẩm: Xem — tất cả; Tạo/Sửa — OWNER, MANAGER
                .requestMatchers(HttpMethod.GET, "/api/v1/products/**").authenticated()
                .requestMatchers("/api/v1/products/**").hasAnyRole("OWNER", "MANAGER")

                // Mọi endpoint còn lại: phải đăng nhập
                .anyRequest().authenticated()
            )

            // Stateless: Không lưu session, mỗi request tự mang JWT
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Đăng ký AuthenticationProvider dùng UserDetailsService + BCrypt
            .authenticationProvider(authenticationProvider())

            // Đặt JwtAuthFilter TRƯỚC filter xác thực username/password mặc định
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * AuthenticationProvider — Kết nối Spring Security với UserDetailsService và PasswordEncoder.
     *
     * DaoAuthenticationProvider:
     * 1. Gọi userDetailsService.loadUserByUsername(username) để nạp User.
     * 2. Dùng passwordEncoder.matches(rawPassword, encodedPassword) để kiểm tra mật khẩu.
     * 3. Kiểm tra isEnabled(), isAccountNonLocked()...
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * AuthenticationManager — Được inject vào AuthService.login() để thực hiện xác thực.
     * Spring Boot tự cấu hình bean này nhưng cần expose ra đây để inject vào Service.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * PasswordEncoder — BCrypt với strength mặc định = 10.
     * Dùng để:
     * - Mã hóa mật khẩu khi tạo/đổi mật khẩu (AuthService).
     * - Xác minh mật khẩu khi đăng nhập (DaoAuthenticationProvider).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
