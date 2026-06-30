package com.example.store_clothes.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthFilter - Bộ lọc xác thực JWT cho mỗi HTTP request.
 *
 * Kế thừa OncePerRequestFilter: Đảm bảo filter chỉ chạy MỘT LẦN mỗi request,
 * kể cả khi request được forward nội bộ (ví dụ: error dispatcher).
 *
 * LUỒNG XỬ LÝ (cho mỗi request):
 * 1. Đọc Authorization header.
 * 2. Nếu không có hoặc không bắt đầu bằng "Bearer " → bỏ qua (tiếp tục filter chain).
 * 3. Trích xuất username từ JWT.
 * 4. Nếu username hợp lệ và SecurityContext chưa có authentication:
 *    a. Load UserDetails từ DB.
 *    b. Validate token.
 *    c. Set Authentication vào SecurityContextHolder.
 * 5. Tiếp tục filter chain.
 *
 * WHY NOT in AuthService?
 * Filter chạy TRƯỚC khi request đến Controller/Service.
 * Việc set SecurityContext tại đây đảm bảo @PreAuthorize hoạt động đúng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Không có Authorization header hoặc không phải Bearer token → bỏ qua
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Trích xuất JWT (bỏ "Bearer " prefix)
        final String jwt = authHeader.substring(7);
        String username = null;

        try {
            username = jwtUtil.extractUsername(jwt);
        } catch (Exception e) {
            // Token malformed, expired, hoặc signature sai
            // Không set authentication → Spring Security sẽ trả 401
            log.warn("JWT parsing failed: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        // Chỉ xử lý nếu username hợp lệ VÀ SecurityContext chưa có authentication
        // (Tránh authenticate lại nếu đã được xử lý bởi filter trước đó)
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtUtil.validateToken(jwt, userDetails)) {
                // Tạo authentication token với đầy đủ authorities từ UserDetails
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,                           // credentials = null sau khi đã xác thực
                                userDetails.getAuthorities()   // roles từ User.getAuthorities()
                        );

                // Gắn thêm thông tin request (IP, session) vào authentication
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Đặt Authentication vào SecurityContext — thread-local, chỉ tồn tại trong request này
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("JWT authenticated: username={}, roles={}", username, userDetails.getAuthorities());
            }
        }

        filterChain.doFilter(request, response);
    }
}
