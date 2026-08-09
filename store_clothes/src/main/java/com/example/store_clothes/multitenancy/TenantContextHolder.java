package com.example.store_clothes.multitenancy;

/**
 * TenantContextHolder — Lưu trữ tenantId của request hiện tại trong ThreadLocal.
 *
 * NGUYÊN LÝ HOẠT ĐỘNG:
 * ThreadLocal đảm bảo mỗi thread (= mỗi HTTP request trong Spring MVC) có một
 * bản sao riêng biệt của tenantId. Không có sự chia sẻ state giữa các request.
 *
 * VÒNG ĐỜI (Lifecycle) của tenantId trong một HTTP Request:
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  HTTP Request đến                                                       │
 * │       ↓                                                                 │
 * │  JwtAuthFilter.doFilterInternal()                                       │
 * │       → TenantContextHolder.setTenantId(tenantId)  ← SET               │
 * │       ↓                                                                 │
 * │  Controller → Service → Repository → SQL (Hibernate tự inject tenant)  │
 * │       ↓                                                                 │
 * │  JwtAuthFilter (finally block)                                          │
 * │       → TenantContextHolder.clear()                ← CLEAR             │
 * │       ↓                                                                 │
 * │  HTTP Response trả về                                                   │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * ⚠️  CẢNH BÁO — Thread Pool Leak:
 * Spring MVC dùng thread pool. Nếu không gọi clear() sau khi request kết thúc,
 * tenantId của request cũ sẽ "lây nhiễm" sang request tiếp theo cùng thread.
 * → BẮT BUỘC gọi clear() trong finally block của JwtAuthFilter.
 *
 * ⚠️  CẢNH BÁO — @Async và @Scheduled:
 * Task async chạy trên thread khác → ThreadLocal gốc KHÔNG được kế thừa.
 * Giải pháp: Truyền tenantId vào task và set thủ công khi bắt đầu task.
 * Xem TenantAwareTaskDecorator (sẽ implement ở Giai đoạn 3 — Async/Scheduled).
 */
public final class TenantContextHolder {

    /**
     * ThreadLocal lưu tenantId (Long).
     * InheritableThreadLocal được cân nhắc nhưng KHÔNG dùng vì:
     *   - Thread pool tái sử dụng thread → child thread giữ giá trị cũ của parent.
     *   - Gây ra rò rỉ tenant nếu không clear cẩn thận.
     * Dùng ThreadLocal thuần + clear() trong finally là cách an toàn nhất.
     */
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    // Ngăn khởi tạo instance — đây là utility class thuần static
    private TenantContextHolder() {}

    /**
     * Set tenantId cho thread hiện tại.
     * Gọi ở đầu mỗi HTTP Request trong JwtAuthFilter.
     *
     * @param tenantId ID của tenant (cửa hàng) từ JWT payload
     */
    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * Lấy tenantId của thread hiện tại.
     * Được gọi bởi TenantIdentifierResolver để inject vào câu SQL của Hibernate.
     *
     * @return tenantId hiện tại, hoặc null nếu chưa được set (public endpoint / Super Admin)
     */
    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * Xóa tenantId khỏi thread hiện tại sau khi request hoàn tất.
     * BẮT BUỘC gọi trong finally block của JwtAuthFilter để tránh memory leak
     * và cross-request contamination trong thread pool.
     */
    public static void clear() {
        TENANT_ID.remove();
    }

    /**
     * Kiểm tra xem thread hiện tại có đang chạy trong ngữ cảnh tenant hay không.
     *
     * @return true nếu tenantId đang được set (request của tenant cụ thể)
     */
    public static boolean hasTenant() {
        return TENANT_ID.get() != null;
    }
}
