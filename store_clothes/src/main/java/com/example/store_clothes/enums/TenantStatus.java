package com.example.store_clothes.enums;

/**
 * TenantStatus — Trạng thái hoạt động của một Tenant (Cửa hàng).
 *
 * Vòng đời Tenant:
 *   ACTIVE     → Đang hoạt động bình thường, đầy đủ quyền truy cập.
 *   SUSPENDED  → Bị tạm khóa (vi phạm quy định, chưa thanh toán...).
 *               Super Admin có thể reactivate.
 *   EXPIRED    → Gói đăng ký đã hết hạn, chưa gia hạn.
 *               Chỉ cho phép đọc dữ liệu, không được tạo/sửa.
 */
public enum TenantStatus {

    /**
     * Cửa hàng đang hoạt động bình thường.
     * Tất cả API được phép truy cập.
     */
    ACTIVE,

    /**
     * Cửa hàng bị tạm khóa bởi Super Admin.
     * Nhân viên không thể đăng nhập cho đến khi được mở khóa.
     */
    SUSPENDED,

    /**
     * Gói đăng ký đã hết hạn.
     * Có thể hiển thị thông báo nhắc gia hạn, hạn chế một số tính năng.
     */
    EXPIRED
}
