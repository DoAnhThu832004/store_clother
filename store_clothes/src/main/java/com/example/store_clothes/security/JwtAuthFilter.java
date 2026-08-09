package com.example.store_clothes.security;

import com.example.store_clothes.multitenancy.TenantContextHolder;
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
 * JwtAuthFilter - Bộ lọc xác thực JWT + thiết lập Tenant Context cho mỗi HTTP request.
 *
 * Kế thừa OncePerRequestFilter: Đảm bảo filter chỉ chạy MỘT LẦN mỗi request,
 * kể cả khi request được forward nội bộ (ví dụ: error dispatcher).
 *
 * LUỒNG XỬ LÝ (cho mỗi request):
 * ┌────────────────────────────────────────────────────────────────────────┐
 * │  1. Đọc Authorization header.                                          │
 * │  2. Nếu không có / không phải Bearer → bỏ qua, tiếp tục chain.        │
 * │  3. Parse JWT, trích xuất username + tenantId.                         │
 * │  4. Set TenantContextHolder.setTenantId(tenantId)  ← KEY STEP         │
 * │  5. Load UserDetails từ DB, validate token.                            │
 * │  6. Set Authentication vào SecurityContextHolder.                       │
 * │  7. Tiếp tục filter chain (Controller → Service → Repository → SQL).   │
 * │  8. [FINALLY] TenantContextHolder.clear()          ← BẮT BUỘC         │
 * └────────────────────────────────────────────────────────────────────────┘
 *
 * TẠI SAO SET TENANT CONTEXT TRƯỚC KHI SET SECURITY CONTEXT?
 * TenantIdentifierResolver có thể đọc SecurityContext để detect SUPER_ADMIN.
 * Do đó, tenantId từ JWT phải được set vào TenantContextHolder TRƯỚC,
 * và nếu user là SUPER_ADMIN, resolver sẽ override bằng null (bypass mode).
 *
 * THREAD SAFETY — Thread Pool Leak Prevention:
 * Spring MVC tái sử dụng thread từ pool. Nếu không gọi clear() trong finally,
 * tenantId của request này sẽ "lây" sang request tiếp theo cùng thread.
 * finally block đảm bảo clear() luôn được gọi dù request thành công hay lỗi.
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
        Long tenantId   = null;

        try {
            username = jwtUtil.extractUsername(jwt);
            tenantId = jwtUtil.extractTenantId(jwt);  // null = SUPER_ADMIN hoặc token cũ
        } catch (Exception e) {
            // Token malformed, expired, hoặc signature sai
            // Không set authentication → Spring Security sẽ trả 401
            log.warn("JWT parsing failed for request [{}]: {}", request.getRequestURI(), e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        // =====================================================================
        // SET TENANT CONTEXT — Đây là bước quan trọng nhất của Multi-Tenancy
        // =====================================================================
        // Phải set TRƯỚC khi filterChain.doFilter() để Hibernate nhận được
        // tenantId ngay khi bắt đầu xử lý request trong Service/Repository.
        //
        // tenantId = null → TenantContextHolder sẽ không set gì.
        //   → TenantIdentifierResolver trả về null.
        //   → Hibernate KHÔNG thêm WHERE tenant_id = ? (Super Admin bypass).
        //
        // tenantId != null → Set vào ThreadLocal.
        //   → TenantIdentifierResolver đọc và inject vào mọi câu SQL.
        if (tenantId != null) {
            TenantContextHolder.setTenantId(tenantId);
            log.trace("Tenant context set: tenantId={}, uri={}", tenantId, request.getRequestURI());
        } else {
            log.trace("No tenant context (SUPER_ADMIN or public endpoint), uri={}", request.getRequestURI());
        }

        try {
            // ==================================================================
            // AUTHENTICATE USER — Chỉ xử lý nếu username hợp lệ
            // VÀ SecurityContext chưa có authentication
            // (Tránh authenticate lại nếu đã được xử lý bởi filter trước đó)
            // ==================================================================
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtil.validateToken(jwt, userDetails)) {
                    // Tạo authentication token với đầy đủ authorities từ UserDetails
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,                          // credentials = null sau khi đã xác thực
                                    userDetails.getAuthorities()   // roles từ User.getAuthorities()
                            );

                    // Gắn thêm thông tin request (IP, session) vào authentication
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Đặt Authentication vào SecurityContext — thread-local, chỉ tồn tại trong request
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("JWT authenticated: username={}, tenantId={}, roles={}",
                            username, tenantId, userDetails.getAuthorities());
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            // ==================================================================
            // BẮT BUỘC: Xóa Tenant Context sau khi request hoàn tất
            // ==================================================================
            // finally đảm bảo clear() luôn được gọi dù:
            //   - Request thành công (200)
            //   - Xảy ra exception trong Controller/Service (500)
            //   - Response được viết trực tiếp (401, 403)
            //
            // KHÔNG gọi clear() = thread pool contamination = Cross-Tenant data leak
            TenantContextHolder.clear();
            log.trace("Tenant context cleared for thread: {}", Thread.currentThread().getName());
        }
    }
}
