package com.example.store_clothes.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * DTO nhận dữ liệu khi thêm biến thể đơn lẻ vào sản phẩm (TICKET PV-01).
 *
 * Khác biệt so với CreateVariantRequest (dùng trong createProduct):
 * - sku: NULLABLE — nếu null thì Service tự sinh bằng VietnameseUtil.generateSku().
 * - color + size: @NotBlank — bắt buộc để sinh SKU và nhận diện biến thể.
 * - initialInventory: thay thế cho "inventory" để rõ ý nghĩa ngữ nghĩa hơn.
 *
 * 💡 Senior Note — Tại sao sku nullable?
 * Khi thêm từng biến thể lẻ, nhân viên có thể không biết quy tắc đặt SKU.
 * Service sẽ tự sinh SKU theo chuẩn của hệ thống, đảm bảo nhất quán.
 * Nếu nhân viên muốn đặt SKU thủ công (ví dụ từ nhà cung cấp), họ có thể truyền vào.
 */
public record AddVariantRequest(

        @NotBlank(message = "Màu sắc không được để trống")
        @Size(max = 50, message = "Màu sắc không quá 50 ký tự")
        String color,

        @NotBlank(message = "Kích cỡ không được để trống")
        @Size(max = 50, message = "Kích cỡ không quá 50 ký tự")
        String size,

        // SKU nullable — null = tự sinh; non-null = validate trùng rồi dùng
        @Size(max = 50, message = "SKU không quá 50 ký tự")
        String sku,

        @Size(max = 50, message = "Barcode không quá 50 ký tự")
        String barcode,

        @NotNull(message = "Giá nhập không được để trống")
        @Positive(message = "Giá nhập phải lớn hơn 0")
        @Digits(integer = 12, fraction = 2, message = "Giá nhập tối đa 12 chữ số nguyên, 2 thập phân")
        BigDecimal importPrice,

        @NotNull(message = "Giá bán không được để trống")
        @Positive(message = "Giá bán phải lớn hơn 0")
        @Digits(integer = 12, fraction = 2, message = "Giá bán tối đa 12 chữ số nguyên, 2 thập phân")
        BigDecimal salePrice,

        @PositiveOrZero(message = "Tồn kho khởi tạo phải >= 0")
        Integer initialInventory

) {
    /**
     * Compact constructor: Gán giá trị mặc định nếu initialInventory null.
     * Tránh NullPointerException downstream mà không cần null-check ở Service.
     */
    public AddVariantRequest {
        if (initialInventory == null) {
            initialInventory = 0;
        }
    }
}
