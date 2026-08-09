package com.example.store_clothes.enums;

/**
 * RoleName - Định nghĩa các vai trò cố định trong hệ thống RBAC.
 *
 * Phân cấp quyền hạn (cao → thấp):
 * ROLE_SUPER_ADMIN      → Quản trị viên nền tảng: bypass tenant filter, quản lý mọi cửa hàng.
 *                          KHÔNG thuộc bất kỳ tenant nào. Chỉ được gán bởi DBA.
 * ROLE_OWNER           → Chủ cửa hàng: toàn quyền trong phạm vi cửa hàng mình.
 * ROLE_MANAGER         → Quản lý: quản lý nhân viên, sản phẩm, báo cáo.
 * ROLE_CASHIER         → Thu ngân: thanh toán hóa đơn, xem sản phẩm.
 * ROLE_WAREHOUSE_STAFF → Nhân viên kho: quản lý nhập xuất kho.
 */
public enum RoleName {
    /**
     * Quản trị viên nền tảng SaaS.
     * - Bypass hoàn toàn tenant filter của Hibernate (xem TenantIdentifierResolver).
     * - Xem và quản lý data của TẤT CẢ cửa hàng.
     * - KHÔNG được gán qua API — chỉ gán thủ công bởi DBA/DevOps trong DB.
     */
    ROLE_SUPER_ADMIN,
    ROLE_OWNER,
    ROLE_MANAGER,
    ROLE_CASHIER,
    ROLE_WAREHOUSE_STAFF
}

