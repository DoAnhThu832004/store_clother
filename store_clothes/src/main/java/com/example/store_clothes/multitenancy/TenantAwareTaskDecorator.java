package com.example.store_clothes.multitenancy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

/**
 * TenantAwareTaskDecorator — Truyền tenantId từ calling thread sang async thread.
 *
 * VẤN ĐỀ CỐT LÕI:
 * ThreadLocal KHÔNG được kế thừa qua ranh giới thread pool.
 * Khi @Async được gọi:
 *
 *   HTTP Request Thread (thread A, tenantId=5)
 *       │
 *       ├─ gọi orderService.someAsyncMethod()
 *       │       ↓
 *       │   Thread Pool chọn Thread B (tenantId=null hoặc tenantId=3 từ request trước)
 *       │       ↓
 *       │   Hibernate query KHÔNG có tenant filter → Cross-tenant data leak!
 *       │
 *
 * GIẢI PHÁP — TaskDecorator Pattern:
 * TaskDecorator là hook của Spring ThreadPoolTaskExecutor.
 * Khi task được submit, Spring gọi decorate(Runnable) trên calling thread (A)
 * → Snapshot tenantId vào local variable.
 * Khi task thực sự chạy (thread B), set tenantId đã snapshot vào context mới.
 *
 *   HTTP Request Thread (thread A, tenantId=5) — decorate() chạy ở đây
 *       │  snapshot tenantId = 5
 *       │
 *   Thread Pool Thread B — Runnable.run() chạy ở đây
 *       ├─ TenantContextHolder.setTenantId(5)  ← inject snapshot
 *       ├─ originalTask.run()                   ← async logic chạy đúng tenant
 *       └─ TenantContextHolder.clear()          ← cleanup
 *
 * ĐĂNG KÝ:
 * Được đăng ký vào ThreadPoolTaskExecutor qua AsyncConfig.setTaskDecorator().
 * Áp dụng cho TẤT CẢ task submit vào executor đó.
 *
 * LƯU Ý @Scheduled:
 * @Scheduled chạy trên thread riêng biệt, KHÔNG phải HTTP request thread.
 * TaskDecorator không áp dụng cho Scheduled task.
 * Xem ghi chú chi tiết tại ScheduledTenantContextUtil.
 */
@Slf4j
public class TenantAwareTaskDecorator implements TaskDecorator {

    /**
     * Bọc Runnable gốc để truyền tenantId từ calling thread sang worker thread.
     *
     * QUAN TRỌNG — Thread Safety:
     * tenantId được capture như một local variable (effectively final).
     * Local variable không shared giữa các thread → hoàn toàn thread-safe.
     *
     * @param runnable Task gốc cần chạy async
     * @return Task đã được bọc, có khả năng truyền tenant context
     */
    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        // Capture tenantId tại thời điểm task được SUBMIT (calling thread)
        // Đây là thời điểm duy nhất có thể đọc đúng tenantId từ HTTP request thread
        final Long capturedTenantId = TenantContextHolder.getTenantId();

        if (capturedTenantId == null) {
            log.trace("Async task submitted without tenant context (SUPER_ADMIN or system task)");
        } else {
            log.trace("Async task submitted with tenantId={}", capturedTenantId);
        }

        return () -> {
            // Đây là code chạy trên WORKER THREAD (thread pool thread)
            // capturedTenantId đã được capture an toàn từ calling thread
            try {
                if (capturedTenantId != null) {
                    TenantContextHolder.setTenantId(capturedTenantId);
                    log.trace("Async task running with tenantId={}, thread={}",
                            capturedTenantId, Thread.currentThread().getName());
                }
                runnable.run();
            } finally {
                // BẮT BUỘC: clear sau khi task hoàn tất
                // Thread pool thread được tái sử dụng cho task tiếp theo
                // Không clear = task tiếp theo nhận sai tenantId
                TenantContextHolder.clear();
                log.trace("Async task tenant context cleared, thread={}",
                        Thread.currentThread().getName());
            }
        };
    }
}
