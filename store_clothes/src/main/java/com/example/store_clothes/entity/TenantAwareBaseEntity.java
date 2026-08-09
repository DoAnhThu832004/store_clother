package com.example.store_clothes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * TenantAwareBaseEntity — Lớp trung gian bổ sung cột tenant_id vào mọi entity nghiệp vụ.
 *
 * KIẾN TRÚC KẾ THỪA:
 * <pre>
 *   BaseEntity (id, createdAt, updatedAt, isDeleted)
 *       └── TenantAwareBaseEntity (+ @TenantId tenantId)
 *               ├── User
 *               ├── Product
 *               ├── ProductVariant
 *               ├── Category
 *               ├── Customer
 *               ├── Supplier
 *               ├── ImportReceipt
 *               └── ImportReceiptDetail
 * </pre>
 *
 * TẠI SAO TÁCH THÀNH LỚP RIÊNG (không sửa BaseEntity)?
 *   1. BaseEntity vẫn giữ nguyên → backward compatible nếu có entity không cần tenant_id.
 *   2. AuditLog và StockHistory là entity bất biến (immutable) — chúng thêm tenantId
 *      theo cách riêng để bảo toàn tính chất immutable.
 *   3. Tenant entity tự quản lý, không kế thừa để tránh circular logic.
 *
 * @TenantId (Hibernate 6):
 *   - Khi TenantContextHolder có tenantId → Hibernate TỰ ĐỘNG thêm:
 *       INSERT: SET tenant_id = ?
 *       SELECT: WHERE tenant_id = ?
 *       UPDATE: WHERE id = ? AND tenant_id = ?  ← bảo vệ khỏi cross-tenant update
 *   - Developer KHÔNG CẦN và KHÔNG ĐƯỢC viết tay WHERE tenant_id = ?
 *
 * LƯU Ý QUAN TRỌNG — @TenantId và null:
 *   - Nếu CurrentTenantIdentifierResolver trả về null, Hibernate sẽ KHÔNG filter tenant.
 *   - Đây là cơ chế "Super Admin bypass" — xem TenantIdentifierResolver.
 *   - Đảm bảo chỉ SUPER_ADMIN mới có thể khiến resolver trả về null.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class TenantAwareBaseEntity extends BaseEntity {

    /**
     * Định danh Tenant (Cửa hàng) sở hữu bản ghi này.
     *
     * @TenantId: Annotation đặc biệt của Hibernate 6 — báo hiệu đây là discriminator
     * cho Multi-Tenancy theo chiến lược DISCRIMINATOR.
     * Hibernate sẽ tự động inject giá trị này vào mọi câu SQL liên quan đến entity này.
     *
     * Column definition:
     *   - nullable = false → DB đảm bảo không có record nào thiếu tenant_id
     *   - updatable = false → Sau khi INSERT, tenant_id không bao giờ được thay đổi
     *     (một bản ghi không thể "chuyển chủ" sang cửa hàng khác)
     *   - insertable = false vì Hibernate @TenantId tự quản lý việc insert
     *     (nếu set insertable=true + @TenantId sẽ bị double-set, gây lỗi)
     */
    @TenantId
    @Column(
        name = "tenant_id",
        nullable = false,
        updatable = false
    )
    private Long tenantId;
}
