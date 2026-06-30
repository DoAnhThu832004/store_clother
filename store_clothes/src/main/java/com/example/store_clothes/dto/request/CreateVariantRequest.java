package com.example.store_clothes.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * DTO nhận dữ liệu khi tạo mới một Biến thể sản phẩm.
 * Được dùng lồng bên trong CreateProductRequest.
 *
 * Lưu ý về validation giá tiền:
 * - @DecimalMin("0.00"): Giá không thể âm.
 * - @Digits(integer=10, fraction=2): Tối đa 10 chữ số nguyên, 2 chữ số thập phân.
 *   Đảm bảo khớp với định nghĩa precision=12, scale=2 trong DB.
 */
public record CreateVariantRequest(

        @NotBlank(message = "SKU không được để trống")
        @Size(max = 50, message = "SKU không quá 50 ký tự")
        String sku,

        @Size(max = 50, message = "Barcode không quá 50 ký tự")
        String barcode,

        @Size(max = 50, message = "Màu sắc không quá 50 ký tự")
        String color,

        @Size(max = 50, message = "Kích cỡ không quá 50 ký tự")
        String size,

        @DecimalMin(value = "0.00", message = "Giá nhập phải >= 0")
        @Digits(integer = 10, fraction = 2, message = "Giá nhập tối đa 10 chữ số nguyên, 2 chữ số thập phân")
        BigDecimal importPrice,

        @DecimalMin(value = "0.00", message = "Giá bán phải >= 0")
        @Digits(integer = 10, fraction = 2, message = "Giá bán tối đa 10 chữ số nguyên, 2 chữ số thập phân")
        BigDecimal salePrice,

        @Min(value = 0, message = "Số lượng tồn kho phải >= 0")
        Integer inventory,

        @NotBlank(message = "Trạng thái biến thể không được để trống")
        String status

) {}
