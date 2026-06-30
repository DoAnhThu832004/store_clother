package com.example.store_clothes.entity;

/**
 * Enum định nghĩa trạng thái của Sản phẩm và Biến thể sản phẩm.
 * Dùng EnumType.STRING để dữ liệu DB có tính đọc được và không bị phá vỡ
 * khi thêm/xóa phần tử trong enum (so với EnumType.ORDINAL).
 */
public enum ProductStatus {
    ACTIVE,
    INACTIVE
}
