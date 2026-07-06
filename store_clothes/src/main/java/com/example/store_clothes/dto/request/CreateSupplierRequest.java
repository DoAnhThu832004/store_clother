package com.example.store_clothes.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO nhận dữ liệu khi tạo mới Nhà Cung Cấp (SUP-01).
 *
 * @ValidPhone: Validate số điện thoại Việt Nam.
 * phone: bắt buộc và unique trong hệ thống.
 * email: tùy chọn nhưng nếu có phải đúng format.
 * taxCode: tùy chọn, mã số thuế 10-13 chữ số.
 */
public record CreateSupplierRequest(

        @NotBlank(message = "Tên nhà cung cấp không được để trống")
        @Size(max = 200, message = "Tên nhà cung cấp không quá 200 ký tự")
        String name,

        /**
         * Số điện thoại — bắt buộc và phải unique.
         * Pattern: Số điện thoại Việt Nam (0[3|5|7|8|9]xxxxxxxx hoặc +84...).
         *
         * 💡 Senior Note — Validate phone ở nhiều tầng:
         * Tầng 1: @Pattern ở DTO → nhanh, không cần DB query.
         * Tầng 2: Service kiểm tra unique trong DB → tránh race condition.
         */
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(
            regexp = "^(\\+84|0)(3[2-9]|5[6-9]|7[06-9]|8[0-9]|9[0-9])[0-9]{7}$",
            message = "Số điện thoại không đúng định dạng Việt Nam"
        )
        String phone,

        @Email(message = "Email không đúng định dạng")
        @Size(max = 150, message = "Email không quá 150 ký tự")
        String email,

        @Size(max = 500, message = "Địa chỉ không quá 500 ký tự")
        String address,

        /**
         * Mã số thuế (Tax Code). Tùy chọn.
         * Format Việt Nam: 10 hoặc 13 chữ số (công ty có chi nhánh thêm "-XXX").
         */
        @Pattern(
            regexp = "^[0-9]{10}(-[0-9]{3})?$|^$",
            message = "Mã số thuế phải có 10 chữ số hoặc 10-3 chữ số (VD: 0123456789 hoặc 0123456789-001)"
        )
        String taxCode

) {}
