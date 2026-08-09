package com.example.store_clothes.multitenancy;

import com.example.store_clothes.entity.Tenant;
import com.example.store_clothes.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;

/**
 * ScheduledTenantUtil — Tiện ích chạy @Scheduled task trong ngữ cảnh Tenant cụ thể.
 *
 * VẤN ĐỀ:
 * @Scheduled chạy trên thread riêng biệt (SchedulerThread), KHÔNG phải HTTP request thread.
 * Không có JWT → không có tenantId → Hibernate không filter → query toàn bộ data (nguy hiểm!).
 * TaskDecorator không áp dụng cho @Scheduled.
 *
 * GIẢI PHÁP:
 * Scheduled job phải TỰ quản lý tenant context theo một trong 2 chiến lược:
 *
 * Chiến lược A — "System Job" (không cần tenant):
 *   Job đọc/ghi toàn bộ data (ví dụ: gửi email hết hạn subscription cho TẤT CẢ tenants).
 *   → Không set tenantId → Hibernate trả về tất cả data (giống Super Admin).
 *   → PHẢI cẩn thận: chỉ được dùng cho task đọc toàn bộ, không được ghi nhầm.
 *
 * Chiến lược B — "Per-Tenant Job" (xử lý từng tenant):
 *   Job lặp qua danh sách tenant, set context, xử lý, rồi clear.
 *   → Dùng forEachActiveTenant() từ class này.
 *   → An toàn nhất: mỗi lần chỉ xử lý đúng data của 1 tenant.
 *
 * VÍ DỤ SỬ DỤNG (Chiến lược B):
 * <pre>
 * {@literal @}Scheduled(cron = "0 0 2 * * ?")  // Mỗi ngày 2:00 AM
 * public void dailyInventoryReport() {
 *     scheduledTenantUtil.forEachActiveTenant(tenantId -> {
 *         // Tất cả code trong lambda này chạy với đúng tenantId
 *         List<ProductVariant> lowStock = variantRepository.findLowStock(threshold);
 *         reportService.sendLowStockAlert(tenantId, lowStock);
 *     });
 * }
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class ScheduledTenantUtil {

    private final TenantRepository tenantRepository;

    /**
     * Chạy logic nghiệp vụ lần lượt cho từng Tenant đang ACTIVE.
     *
     * Với mỗi tenant:
     *   1. Set TenantContextHolder.setTenantId(tenant.getId())
     *   2. Gọi tenantTask.accept(tenantId) — logic của caller
     *   3. Clear TenantContextHolder (trong finally)
     *
     * Error Isolation: Nếu một tenant bị lỗi, các tenant còn lại vẫn được xử lý.
     * Lỗi được log nhưng không ném ra ngoài để tránh dừng toàn bộ batch.
     *
     * @param tenantTask Consumer nhận tenantId và thực hiện logic nghiệp vụ
     */
    public void forEachActiveTenant(Consumer<Long> tenantTask) {
        List<Long> activeTenantIds = tenantRepository.findAllActiveIds();

        log.info("Scheduled job: processing {} active tenants", activeTenantIds.size());

        for (Long tenantId : activeTenantIds) {
            try {
                TenantContextHolder.setTenantId(tenantId);
                log.debug("Scheduled job: processing tenant {}", tenantId);
                tenantTask.accept(tenantId);
            } catch (Exception ex) {
                // Error isolation: tenant này lỗi → log + tiếp tục tenant kế tiếp
                // Không ném ra ngoài để tránh dừng toàn bộ scheduled job
                log.error("Scheduled job failed for tenantId={}: {}", tenantId, ex.getMessage(), ex);
            } finally {
                // BẮT BUỘC: clear sau mỗi tenant để tránh rò rỉ sang tenant kế tiếp
                TenantContextHolder.clear();
            }
        }

        log.info("Scheduled job: completed processing {} tenants", activeTenantIds.size());
    }

    /**
     * Chạy logic cho một tenant cụ thể (dùng khi job chỉ cần xử lý 1 tenant).
     * Ví dụ: retry job cho tenant đang bị lỗi.
     *
     * @param tenantId ID tenant cần xử lý
     * @param tenantTask Logic cần thực hiện
     */
    public void runForTenant(Long tenantId, Runnable tenantTask) {
        try {
            TenantContextHolder.setTenantId(tenantId);
            tenantTask.run();
        } finally {
            TenantContextHolder.clear();
        }
    }
}
