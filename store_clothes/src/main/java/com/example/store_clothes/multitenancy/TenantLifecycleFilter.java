package com.example.store_clothes.multitenancy;

import com.example.store_clothes.entity.Tenant;
import com.example.store_clothes.enums.TenantStatus;
import com.example.store_clothes.exception.ErrorCode;
import com.example.store_clothes.exception.TenantNotOperationalException;
import com.example.store_clothes.repository.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * TenantLifecycleFilter — Bảo vệ API khỏi các Tenant bị Suspended hoặc Expired.
 *
 * VỊ TRÍ TRONG FILTER CHAIN:
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  Request → JwtAuthFilter (set TenantContext + auth) → TenantLifecycleFilter │
 * │                                                              ↓               │
 * │         Kiểm tra tenant.isOperational() từ DB/Cache          │               │
 * │                                                              ↓               │
 * │         PASS → Controller → Service → Repository → SQL       │               │
 * │         FAIL → TenantNotOperationalException → 402/403        │               │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *
 * PHẢI CHẠY SAU JwtAuthFilter vì cần TenantContextHolder đã được set.
 * Thứ tự đăng ký trong SecurityConfig.
 *
 * BYPASS TỰ ĐỘNG (không check):
 * 1. Public endpoints — chưa có TenantContext (tenantId = null).
 *    Ví dụ: POST /api/v1/tenants/register, POST /api/v1/auth/login.
 * 2. SUPER_ADMIN — tenantId = null trong JWT → không thuộc tenant nào.
 *    Super Admin phải luôn vào được để suspend/activate tenant.
 *
 * HIỆU NĂNG — Caching:
 * Mỗi request đều query DB để lấy tenant status là N+1 calls không cần thiết.
 * Để tối ưu production:
 *   - Inject CacheManager và cache kết quả isOperational() với TTL = 5 phút.
 *   - Khi Super Admin thay đổi tenant status → evict cache tương ứng.
 * Phiên bản này query DB trực tiếp để đơn giản hóa, phù hợp với tải thấp đến trung bình.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantLifecycleFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        Long tenantId = TenantContextHolder.getTenantId();

        // =====================================================================
        // BYPASS: Không có tenantId → Public endpoint hoặc Super Admin
        // Không cần kiểm tra lifecycle — tiếp tục filter chain ngay
        // =====================================================================
        if (tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // =====================================================================
        // KIỂM TRA TENANT LIFECYCLE
        // =====================================================================
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);

        if (tenant == null) {
            // Tenant ID trong JWT không tồn tại trong DB.
            // Tình huống: DBA xóa thủ công row trong tenants table,
            // hoặc JWT cũ còn hạn nhưng tenant đã bị xóa.
            // Trả 403 thay vì 404 để không lộ thông tin nội bộ.
            log.warn("JWT contains non-existent tenantId={}. Request blocked: {}",
                    tenantId, request.getRequestURI());
            throw new TenantNotOperationalException(ErrorCode.TENANT_NOT_FOUND);
        }

        // Kiểm tra trạng thái Tenant
        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            log.warn("Request blocked: tenantId={} is SUSPENDED. URI={}",
                    tenantId, request.getRequestURI());
            throw new TenantNotOperationalException(ErrorCode.TENANT_SUSPENDED);
        }

        // Kiểm tra hết hạn subscription
        if (tenant.getStatus() == TenantStatus.EXPIRED ||
            (tenant.getSubscriptionExpiredAt() != null &&
             LocalDate.now().isAfter(tenant.getSubscriptionExpiredAt()))) {

            // Tự động sync status nếu date đã qua nhưng status chưa được cron job cập nhật
            if (tenant.getStatus() != TenantStatus.EXPIRED) {
                log.warn("Tenant id={} subscription expired on {} but status not yet updated by cron.",
                        tenantId, tenant.getSubscriptionExpiredAt());
            }

            String expiredDate = tenant.getSubscriptionExpiredAt() != null
                    ? tenant.getSubscriptionExpiredAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : "N/A";

            log.warn("Request blocked: tenantId={} subscription expired on {}. URI={}",
                    tenantId, expiredDate, request.getRequestURI());

            throw new TenantNotOperationalException(
                ErrorCode.TENANT_EXPIRED,
                "Gói đăng ký của cửa hàng đã hết hạn vào ngày " + expiredDate +
                ". Vui lòng gia hạn để tiếp tục sử dụng."
            );
        }

        // =====================================================================
        // TENANT OPERATIONAL → Tiếp tục xử lý request bình thường
        // =====================================================================
        log.trace("Tenant lifecycle check passed: tenantId={}", tenantId);
        filterChain.doFilter(request, response);
    }
}
