package com.example.store_clothes.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * TenantRegisterResponse — Phản hồi sau khi đăng ký cửa hàng thành công.
 *
 * NGUYÊN TẮC THIẾT KẾ:
 * - Trả về đủ thông tin để client có thể:
 *   (a) Hiển thị thông báo chào mừng (storeName, ownerUsername)
 *   (b) Redirect ngay đến trang đăng nhập với username đã điền sẵn
 *   (c) Cho biết subdomain đã được tạo (storeCode)
 * - KHÔNG trả về tenantId (internal ID, không cần thiết cho client public)
 * - KHÔNG trả về token (user phải đăng nhập riêng — đây là best practice
 *   vì đăng ký ≠ đăng nhập về mặt security audit)
 */
@Getter
@Builder
public class TenantRegisterResponse {

    /** Tên cửa hàng đã đăng ký. */
    private String storeName;

    /** Mã định danh cửa hàng. Dùng để tạo URL: {storeCode}.yoursaas.com */
    private String storeCode;

    /** Username của OWNER vừa được tạo — dùng để pre-fill form đăng nhập. */
    private String ownerUsername;

    /** Thông điệp hướng dẫn bước tiếp theo cho người dùng. */
    private String message;
}
