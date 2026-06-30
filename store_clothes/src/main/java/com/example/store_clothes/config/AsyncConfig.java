package com.example.store_clothes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * AsyncConfig - Cấu hình Thread Pool riêng biệt cho AuditLogService.
 *
 * TẠI SAO CẦN THREAD POOL RIÊNG?
 *
 * Nếu dùng executor mặc định của @EnableAsync, tác vụ audit log sẽ
 * cạnh tranh thread với các tác vụ async khác trong hệ thống.
 * Khi flash sale → hàng trăm checkout đồng thời → thread pool mặc định
 * có thể bị nghẽn → audit log bị delay hoặc mất.
 *
 * Tách riêng "auditTaskExecutor" đảm bảo:
 * - Audit log luôn có thread riêng để chạy.
 * - Nghiệp vụ chính không bị ảnh hưởng nếu audit log chậm.
 * - Dễ monitoring/tuning riêng lẻ.
 *
 * THÔNG SỐ THREAD POOL:
 * - corePoolSize=2:   Luôn giữ sẵn 2 thread cho audit log.
 * - maxPoolSize=5:    Tối đa 5 thread khi tải cao.
 * - queueCapacity=100: Hàng đợi 100 task trước khi từ chối.
 * - keepAliveSeconds=60: Thread idle > 60s sẽ bị destroy (tiết kiệm resource).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Thread Pool Executor dành riêng cho AuditLogService.
     *
     * Được tham chiếu qua tên bean "auditTaskExecutor" trong:
     * @Async("auditTaskExecutor") tại AuditLogService.
     *
     * threadNamePrefix: Giúp nhận diện thread trong log:
     * "AuditLog-1", "AuditLog-2"... thay vì "task-1", "task-2".
     */
    @Bean("auditTaskExecutor")
    public Executor auditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("AuditLog-");

        // CallerRunsPolicy: Nếu queue đầy → chạy trên thread gọi (không drop task)
        // Đây là chính sách an toàn nhất cho audit log — không mất log
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();
        return executor;
    }

    /**
     * Thread Pool Executor dành riêng cho ReportService.
     *
     * Tách biệt với auditTaskExecutor để tránh tranh chấp thread:
     * - Report queries thường nặng (aggregation, join nhiều bảng).
     * - Audit log là lightweight I/O.
     * - Dùng 2 pool riêng → mỗi loại có SLA riêng, không ảnh hưởng nhau.
     *
     * threadNamePrefix: "ReportAsync-1", "ReportAsync-2"... giúp trace log dễ dàng.
     */
    @Bean(name = "reportTaskExecutor")
    public Executor reportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);       // Số thread chạy thường trực cho report
        executor.setMaxPoolSize(10);       // Tối đa 10 thread khi tải cao
        executor.setQueueCapacity(100);    // Sức chứa hàng đợi nhiệm vụ
        executor.setThreadNamePrefix("ReportAsync-");
        executor.initialize();
        return executor;
    }
}
