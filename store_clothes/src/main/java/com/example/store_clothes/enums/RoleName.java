package com.example.store_clothes.enums;

/**
 * RoleName - Định nghĩa các vai trò cố định trong hệ thống RBAC.
 *
 * Phân cấp quyền hạn (cao → thấp):
 * ROLE_OWNER           → Chủ cửa hàng: toàn quyền.
 * ROLE_MANAGER         → Quản lý: quản lý nhân viên, sản phẩm, báo cáo.
 * ROLE_CASHIER         → Thu ngân: thanh toán hóa đơn, xem sản phẩm.
 * ROLE_WAREHOUSE_STAFF → Nhân viên kho: quản lý nhập xuất kho.
 */
public enum RoleName {
    ROLE_OWNER,
    ROLE_MANAGER,
    ROLE_CASHIER,
    ROLE_WAREHOUSE_STAFF
}
