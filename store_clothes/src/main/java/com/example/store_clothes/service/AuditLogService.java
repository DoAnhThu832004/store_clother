package com.example.store_clothes.service;

import com.example.store_clothes.entity.AuditLog;
import com.example.store_clothes.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuditLogService - Ghi nhận lịch sử thao tác hệ thống BẤT ĐỒNG BỘ.
 *
 * ====================================================================
 * KIẾN TRÚC BẤT ĐỒNG BỘ — ĐỌC TRƯỚC KHI SỬA:
 * ====================================================================
 *
 * [1] @Async — Chạy trên Thread Pool riêng biệt:
 *     Khi AuthService.login() gọi auditLogService.log(...), Spring sẽ
 *     KHÔNG chạy method này trực tiếp. Thay vào đó, Spring tạo một
 *     Runnable task và đẩy vào TaskExecutor thread pool.
 *     → Luồng chính (thu ngân) nhận response NGAY LẬP TỨC.
 *     → Việc ghi log xảy ra SONG SONG trên thread khác.
 *     → Bắt buộc: @EnableAsync trong AsyncConfig.
 *
 * [2] Propagation.REQUIRES_NEW — Transaction độc lập hoàn toàn:
 *     Luồng ghi log chạy trên thread riêng nên KHÔNG chia sẻ transaction
 *     của nghiệp vụ chính (vốn đã commit trước đó).
 *     REQUIRES_NEW tạo một connection DB mới độc lập.
 *     → Nếu ghi log lỗi → chỉ rollback transaction log.
 *     → Không ảnh hưởng gì đến checkout/login đã thành công.
 *
 * [3] try-catch BẮT BUỘC — Cô lập lỗi hoàn toàn:
 *     BẤT KỲ exception nào trong method này phải được bắt tại đây.
 *     Không được để exception truyền ngược ra ngoài vì:
 *     - Thread pool của @Async không forward exception về caller.
 *     - Tuy nhiên nếu exception xảy ra trước khi return → log lỗi,
 *       không ghi được audit nhưng nghiệp vụ vẫn an toàn.
 *
 * [4] KHÔNG gọi method này trong cùng class (self-invocation):
 *     @Async dùng Spring AOP Proxy. Nếu gọi this.log() từ trong class
 *     này → Spring không thể intercept → không async!
 *     Phải inject AuditLogService qua constructor để sử dụng.
 * ====================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Ghi một bản ghi nhật ký hệ thống bất đồng bộ.
     *
     * Được thiết kế để gọi sau khi nghiệp vụ chính hoàn thành thành công.
     * Không bao giờ ném exception ra ngoài — mọi lỗi chỉ được log nội bộ.
     *
     * @param userId       ID người dùng thực hiện hành động (null nếu anonymous)
     * @param username     Username tại thời điểm hành động (snapshot)
     * @param action       Tên hành động: LOGIN, LOGIN_FAILED, CHANGE_PASSWORD, CHECKOUT...
     * @param resourceType Loại đối tượng bị tác động: USER, ORDER, PRODUCT... (null nếu N/A)
     * @param resourceId   ID đối tượng bị tác động (null nếu N/A)
     * @param details      Chuỗi JSON mô tả chi tiết sự kiện
     */
    @Async("auditTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId,
                    String username,
                    String action,
                    String resourceType,
                    Long resourceId,
                    String details) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .username(username)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .details(details)
                    .build();

            auditLogRepository.save(auditLog);

            log.debug("Audit log saved: action={}, user={}, resource={}/{}",
                    action, username, resourceType, resourceId);

        } catch (Exception e) {
            // BẮT BUỘC: Bọc kín exception, chỉ log error nội bộ.
            // Tuyệt đối KHÔNG rethrow — nghiệp vụ chính đã thành công,
            // không được rollback hay phát sinh lỗi vì log thất bại.
            log.error("AUDIT LOG FAILED — action={}, user={}, resource={}/{}. Error: {}",
                    action, username, resourceType, resourceId, e.getMessage());
        }
    }

    /**
     * Overload tiện lợi: Ghi log không có resourceType/resourceId.
     * Dùng cho các hành động không liên quan đến đối tượng cụ thể:
     * LOGIN, LOGOUT, LOGIN_FAILED, CHANGE_PASSWORD...
     */
    @Async("auditTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String username, String action, String details) {
        log(userId, username, action, null, null, details);
    }
}
