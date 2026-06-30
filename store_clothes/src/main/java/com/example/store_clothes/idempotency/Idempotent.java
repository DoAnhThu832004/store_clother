package com.example.store_clothes.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Idempotent — Annotation đánh dấu một API endpoint cần bảo vệ chống request trùng lặp.
 *
 * ĐẶT ANNOTATION NÀY trên method @PostMapping hoặc bất kỳ endpoint nào thực hiện
 * thao tác ghi (Create/Update/Delete) mà cần phòng ngừa:
 *  - User double-click nút "Thanh toán" hoặc "Hủy đơn"
 *  - Frontend retry khi network timeout
 *  - Bot/script gửi lại request nhiều lần
 *
 * CÁCH HOẠT ĐỘNG:
 *  1. Client PHẢI gửi kèm Header: "Idempotency-Key: {UUID}" trong mỗi request.
 *  2. IdempotencyAspect kiểm tra key trong Redis:
 *     - Nếu chưa có → cho request đi qua, lưu key vào Redis với TTL.
 *     - Nếu đã có ("PROCESSING") → request đang xử lý, trả về 409.
 *     - Nếu đã có ("PROCESSED") → đã xử lý xong, trả về 409 với thông báo rõ ràng.
 *  3. Sau khi method hoàn thành (success hoặc exception) → cập nhật trạng thái Redis.
 *
 * VÍ DỤ SỬ DỤNG:
 *
 *   // Chống click đúp tại màn hình POS — TTL 30 giây:
 *   @PostMapping("/checkout")
 *   @Idempotent(ttlSeconds = 30, scope = IdempotentScope.POS)
 *   public ResponseEntity&lt;ApiResponse&lt;OrderResponse&gt;&gt; checkout(...) { ... }
 *
 *   // Phòng ngừa retry network cho nghiệp vụ tài chính — TTL 24 giờ:
 *   @PostMapping("/{id}/cancel")
 *   @Idempotent(ttlSeconds = 86400, scope = IdempotentScope.FINANCIAL)
 *   public ResponseEntity&lt;ApiResponse&lt;Void&gt;&gt; cancelOrder(...) { ... }
 *
 * @Target(METHOD): Chỉ áp dụng trên method, không áp dụng trên class.
 * @Retention(RUNTIME): Cần thiết để AOP Aspect đọc annotation tại runtime.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * Thời gian sống (TTL) của Idempotency Key trong Redis (đơn vị: giây).
     *
     * Giá trị khuyến nghị:
     *  - 30 giây: Chống double-click tức thời tại POS
     *  - 86400 giây (24 giờ): Chống retry sâu cho nghiệp vụ tài chính/kho bãi
     *
     * Mặc định: 30 giây (chế độ POS an toàn)
     */
    long ttlSeconds() default 30L;

    /**
     * Phạm vi idempotency — chỉ mang tính semantic, không ảnh hưởng logic.
     * Giúp developer dễ hiểu mục đích khi đọc annotation trên method.
     */
    IdempotentScope scope() default IdempotentScope.POS;
}
