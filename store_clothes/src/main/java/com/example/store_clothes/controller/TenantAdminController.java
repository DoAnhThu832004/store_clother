package com.example.store_clothes.controller;

import com.example.store_clothes.entity.Tenant;
import com.example.store_clothes.enums.TenantStatus;
import com.example.store_clothes.exception.BusinessException;
import com.example.store_clothes.multitenancy.TenantContextHolder;
import com.example.store_clothes.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * TenantAdminController — API quản trị Tenant dành riêng cho SUPER_ADMIN.
 *
 * BẢO MẬT 2 TẦNG:
 * Tầng 1 — URL Pattern: SecurityConfig đã cấu hình /api/v1/admin/** chỉ cho SUPER_ADMIN.
 * Tầng 2 — Method Level: @PreAuthorize("hasRole('SUPER_ADMIN')") tại mỗi method.
 * → Dù có bypass SecurityConfig, method-level check vẫn chặn được.
 *
 * SUPER ADMIN BYPASS HOẠT ĐỘNG NHƯ THẾ NÀO:
 * 1. JwtAuthFilter đọc JWT → tenantId = null (SUPER_ADMIN không có tenant).
 * 2. TenantContextHolder.getTenantId() = null.
 * 3. TenantIdentifierResolver.resolveCurrentTenantIdentifier() trả về null.
 * 4. Hibernate KHÔNG thêm WHERE tenant_id = ? vào bất kỳ query nào.
 * → Super Admin thấy tất cả data của mọi tenant.
 *
 * LƯU Ý: TenantRepository không có @TenantId filter nên luôn trả về
 * tất cả tenant bất kể context — đây là thiết kế đúng.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
public class TenantAdminController {

    private final TenantRepository tenantRepository;

    /**
     * Lấy danh sách TẤT CẢ tenant trong hệ thống.
     * Super Admin xem được tất cả — không filter theo tenant_id.
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<Tenant>> getAllTenants(
            @RequestParam(required = false) TenantStatus status
    ) {
        List<Tenant> tenants = (status != null)
                ? tenantRepository.findAllByStatus(status.name())
                : tenantRepository.findAll();

        log.info("SUPER_ADMIN fetched {} tenants (filter status={})", tenants.size(), status);
        return ResponseEntity.ok(tenants);
    }

    /**
     * Xem chi tiết một Tenant cụ thể.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Tenant> getTenantById(@PathVariable Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Tenant không tồn tại: " + id));
        return ResponseEntity.ok(tenant);
    }

    /**
     * Suspend (tạm khóa) một cửa hàng.
     *
     * Khi Tenant bị SUSPENDED:
     * - Mọi request của cửa hàng đó sẽ bị từ chối (ở TenantVerificationFilter hoặc @PreAuthorize).
     * - Data không bị xóa — có thể ACTIVE lại bất kỳ lúc nào.
     *
     * USE CASES: Vi phạm terms, chưa thanh toán, yêu cầu chủ động của OWNER.
     */
    @PatchMapping("/{id}/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Tenant> suspendTenant(
            @PathVariable Long id,
            @RequestParam(required = false) String reason
    ) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Tenant không tồn tại: " + id));

        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            throw new BusinessException("Tenant đã ở trạng thái SUSPENDED.");
        }

        tenant.setStatus(TenantStatus.SUSPENDED);
        tenant.setAdminNote("Suspended: " + (reason != null ? reason : "no reason provided"));
        tenantRepository.save(tenant);

        log.warn("SUPER_ADMIN suspended tenant: id={}, name={}, reason={}",
                id, tenant.getName(), reason);
        return ResponseEntity.ok(tenant);
    }

    /**
     * Kích hoạt lại một cửa hàng đã bị suspend/expired.
     */
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Tenant> activateTenant(@PathVariable Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Tenant không tồn tại: " + id));

        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setAdminNote(null);
        tenantRepository.save(tenant);

        log.info("SUPER_ADMIN activated tenant: id={}, name={}", id, tenant.getName());
        return ResponseEntity.ok(tenant);
    }

    /**
     * Xem data của một tenant cụ thể (Demo Super Admin cross-tenant access).
     *
     * CÁCH HOẠT ĐỘNG:
     * Endpoint này set TenantContextHolder thủ công → Hibernate filter theo tenant đó.
     * Super Admin có thể "switch" sang bất kỳ tenant nào để debug data.
     *
     * IMPORTANT: Phải clear sau khi dùng (trong finally).
     * Đây là pattern "Admin Impersonation" — chỉ dành cho debug, không dùng để ghi.
     */
    @GetMapping("/{id}/preview")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> previewTenantContext(@PathVariable Long id) {
        // Xác nhận tenant tồn tại
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Tenant không tồn tại: " + id));

        // Demo: Set context để query trong phạm vi tenant đó
        // Thực tế, bạn sẽ inject các service khác và gọi queries ở đây
        try {
            TenantContextHolder.setTenantId(id);
            log.info("SUPER_ADMIN previewing tenant: id={}, name={}", id, tenant.getName());

            // Ở đây có thể gọi: productRepository.count(), userRepository.count(), v.v.
            return ResponseEntity.ok(
                "Previewing tenant [" + id + "] " + tenant.getName() +
                " — Status: " + tenant.getStatus() +
                " — Operational: " + tenant.isOperational()
            );
        } finally {
            TenantContextHolder.clear(); // BẮT BUỘC
        }
    }
}
