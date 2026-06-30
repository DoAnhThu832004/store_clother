package com.example.store_clothes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * ChangePasswordRequest - Dữ liệu đổi mật khẩu.
 *
 * oldPassword: Mật khẩu hiện tại để xác minh danh tính.
 * newPassword: Mật khẩu mới, phải ≥ 8 ký tự.
 */
@Getter
@Setter
public class ChangePasswordRequest {

    @NotBlank(message = "Mật khẩu cũ không được để trống")
    private String oldPassword;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 8, message = "Mật khẩu mới phải có ít nhất 8 ký tự")
    private String newPassword;
}
