package com.example.store_clothes.entity;

/**
 * Trạng thái của Danh mục sản phẩm.
 *
 * ACTIVE  — Danh mục đang hoạt động, hiển thị cho nhân viên và khách hàng.
 * INACTIVE — Danh mục tạm ẩn, không hiển thị nhưng dữ liệu vẫn còn trong DB.
 *
 * 💡 Senior Note — Tại sao không dùng boolean?
 * Enum STRING trong DB dễ đọc hơn (ACTIVE/INACTIVE vs 0/1),
 * dễ mở rộng thêm trạng thái mới (PENDING, ARCHIVED) không cần migration phức tạp.
 */
public enum CategoryStatus {
    ACTIVE,
    INACTIVE
}
