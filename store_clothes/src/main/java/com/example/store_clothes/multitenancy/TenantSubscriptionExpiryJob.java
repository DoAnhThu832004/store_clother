package com.example.store_clothes.multitenancy;

import com.example.store_clothes.entity.Tenant;
import com.example.store_clothes.enums.TenantStatus;
import com.example.store_clothes.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * TenantSubscriptionExpiryJob — Tự động chuyển Tenant sang EXPIRED khi quá hạn subscription.
 *
 * VẤN ĐỀ NẾU KHÔNG CÓ JOB NÀY:
 * Nếu chỉ dựa vào TenantLifecycleFilter để check date,
 * tenant.status sẽ vẫn là ACTIVE dù đã quá hạn.
 * → Dashboard Super Admin hiển thị sai (tenant vẫn "ACTIVE").
 * → Báo cáo "số tenant active" sai.
 * → Log không nhất quán.
 *
 * THIẾT KẾ:
 * - @Scheduled chạy mỗi ngày lúc 00:05 SA (5 phút sau nửa đêm để tránh tranh chấp với job khác).
 * - Query tất cả tenant ACTIVE có subscriptionExpiredAt < today.
 * - Batch update status → EXPIRED.
 * - Log số lượng tenant bị expire để monitoring.
 *
 * KHÔNG CẦN TenantContextHolder:
 * Tenant entity KHÔNG có @TenantId → Hibernate không filter.
 * Job này là "System Job" (Chiến lược A trong ScheduledTenantUtil) — xử lý toàn bộ dữ liệu.
 *
 * IDEMPOTENT: Chạy lại nhiều lần vẫn an toàn.
 * Tenant đã EXPIRED → query WHERE status = ACTIVE không select nữa → không thay đổi gì.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantSubscriptionExpiryJob {

    private final TenantRepository tenantRepository;

    /**
     * Chạy mỗi ngày lúc 00:05 SA để sync trạng thái hết hạn subscription.
     *
     * Cron: "0 5 0 * * ?" = giây 0, phút 5, giờ 0, mọi ngày, mọi tháng, mọi thứ trong tuần.
     *
     * TRANSACTION:
     * @Transactional để đảm bảo tất cả update commit cùng lúc.
     * Nếu lỗi xảy ra giữa chừng → toàn bộ rollback → job sẽ retry vào ngày hôm sau.
     */
    @Scheduled(cron = "0 5 0 * * ?")
    @Transactional
    public void expireOverdueTenants() {
        LocalDate today = LocalDate.now();
        log.info("TenantSubscriptionExpiryJob started: checking expired subscriptions as of {}", today);

        // Lấy tất cả tenant ACTIVE có subscription đã quá hạn
        List<Tenant> expiredTenants = tenantRepository
                .findAllActiveExpiredBefore(today);

        if (expiredTenants.isEmpty()) {
            log.info("TenantSubscriptionExpiryJob: No tenants expired today.");
            return;
        }

        // Batch update status
        expiredTenants.forEach(tenant -> {
            tenant.setStatus(TenantStatus.EXPIRED);
            log.warn("Tenant expired: id={}, name={}, expiredAt={}",
                    tenant.getId(), tenant.getName(), tenant.getSubscriptionExpiredAt());
        });

        tenantRepository.saveAll(expiredTenants);

        log.info("TenantSubscriptionExpiryJob completed: {} tenants marked as EXPIRED.", expiredTenants.size());

        // TODO (production): Gửi email thông báo hết hạn cho OWNER của mỗi tenant
        // emailService.sendExpirationNotice(expiredTenants);
    }
}
