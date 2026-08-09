package com.example.store_clothes.multitenancy;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * TenantIdentifierResolver — Cầu nối giữa TenantContextHolder và Hibernate ORM.
 *
 * LUỒNG HOẠT ĐỘNG:
 *   1. JwtAuthFilter đọc tenantId từ JWT → set vào TenantContextHolder.
 *   2. Khi Hibernate chuẩn bị execute SQL, nó gọi resolveCurrentTenantIdentifier().
 *   3. Resolver lấy tenantId từ TenantContextHolder và trả về cho Hibernate.
 *   4. Hibernate tự động thêm điều kiện tenant_id = ? vào câu SQL.
 *
 * SUPER ADMIN BYPASS:
 *   Khi user có ROLE_SUPER_ADMIN, resolver trả về null.
 *   Hibernate 6 với @TenantId sẽ KHÔNG thêm filter khi tenantId = null.
 *   → Super Admin thấy được toàn bộ data của tất cả tenants.
 *
 *   ⚠️  BẢO MẬT QUAN TRỌNG:
 *   Cơ chế bypass này PHỤ THUỘC VÀO Spring Security đã authenticate đúng.
 *   ROLE_SUPER_ADMIN phải được gán cẩn thận và không bao giờ được lộ ra ngoài.
 *
 * VALIDATE EMPTY TENANT (validateExistingCurrentSessions):
 *   Trả về true → Hibernate báo lỗi nếu resolver trả về null trong khi đang
 *   có session với tenant cụ thể. Đây là safety net để phát hiện lỗi logic.
 *   Tuy nhiên, vì Super Admin cần null, chúng ta trả về false và xử lý
 *   security ở tầng Spring Security.
 *
 * ĐĂNG KÝ VÀO HIBERNATE qua HibernatePropertiesCustomizer:
 *   Cách đăng ký này an toàn hơn việc dùng property string trong application.yaml
 *   vì tránh được ClassCastException nếu Hibernate cần bean instance (không phải class name).
 */
@Slf4j
@Component
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

    private static final String SUPER_ADMIN_ROLE = "ROLE_SUPER_ADMIN";

    /**
     * Constant used to bypass the tenant filter for system/super-admin tasks.
     * In Hibernate 6, resolveCurrentTenantIdentifier() MUST NOT return null.
     */
    private static final Long SYSTEM_TENANT_ID = 0L;

    /**
     * Hibernate gọi method này TRƯỚC MỖI câu SQL liên quan đến @TenantId entity.
     *
     * Logic ưu tiên:
     *   1. Nếu user hiện tại là SUPER_ADMIN → trả về SYSTEM_TENANT_ID (bypass filter).
     *   2. Nếu TenantContextHolder có tenantId → trả về tenantId đó.
     *   3. Nếu không có gì → trả về SYSTEM_TENANT_ID (public endpoint, Flyway, DataInitializer).
     *
     * @return tenantId để Hibernate dùng làm filter.
     */
    @Override
    public Long resolveCurrentTenantIdentifier() {
        // Check Super Admin bypass trước
        if (isSuperAdmin()) {
            log.trace("SUPER_ADMIN detected — returning SYSTEM_TENANT_ID");
            return SYSTEM_TENANT_ID;
        }

        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            log.trace("Resolved tenant: {}", tenantId);
            return tenantId;
        }
        return SYSTEM_TENANT_ID;
    }

    /**
     * Hibernate gọi method này để xác định có cần validate tenant identifier
     * trong các session đang mở hay không.
     *
     * Trả về false vì:
     *   - Super Admin hợp lệ cần trả về null.
     *   - Flyway migration, DataInitializer cũng chạy với null tenant.
     *   - Nếu trả về true, Hibernate sẽ ném lỗi khi resolver trả về null
     *     trong khi đang có session active → phá vỡ các luồng hợp lệ.
     */
    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    /**
     * Đăng ký TenantIdentifierResolver vào Hibernate properties.
     *
     * Sử dụng HibernatePropertiesCustomizer thay vì application.yaml property string
     * để tránh lỗi khi Hibernate cần bean instance thay vì class name.
     *
     * Hibernate 6 dùng key "hibernate.tenant_identifier_resolver" (string literal)
     * — constant này không có trong AvailableSettings của Spring Boot 3.x.
     */
    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        // Register this resolver so Hibernate 6 knows how to get the current tenant ID.
        // In Hibernate 6, simply registering CurrentTenantIdentifierResolver is sufficient
        // for @TenantId to work. DO NOT set "hibernate.multi_tenancy" property —
        // that is a Hibernate 5 property that forces strict tenant validation on every
        // session open, which breaks startup validation of repositories like TenantRepository
        // that operate without a tenant context.
        hibernateProperties.put(
            "hibernate.tenant_identifier_resolver",
            this
        );
    }

    /**
     * Kiểm tra user hiện tại có phải SUPER_ADMIN không.
     * Đọc từ SecurityContext — đã được set bởi JwtAuthFilter.
     */
    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(SUPER_ADMIN_ROLE::equals);
    }
}
