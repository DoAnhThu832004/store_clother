package com.example.store_clothes.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO nhận dữ liệu khi cập nhật Nhà Cung Cấp (SUP-04).
 *
 * QUAN TRỌNG: Không cho phép cập nhật debtAmount qua đây.
 * debtAmount chỉ được hệ thống tự cập nhật khi:
 *   - Hoàn thành phiếu nhập (ImportReceipt COMPLETED): debt += totalAmount - paidAmount
 *   - Thanh toán công nợ: debt -= paymentAmount
 */
public record UpdateSupplierRequest(

        @NotBlank(message = "Tên nhà cung cấp không được để trống")
        @Size(max = 200, message = "Tên nhà cung cấp không quá 200 ký tự")
        String name,

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

        @Pattern(
            regexp = "^[0-9]{10}(-[0-9]{3})?$|^$",
            message = "Mã số thuế phải có 10 chữ số hoặc 10-3 chữ số"
        )
        String taxCode

) {}
