package com.example.store_clothes.dto.request;

import com.example.store_clothes.enums.RoleName;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * CreateUserRequest — DTO cho API USR-01: Tạo nhân viên mới.
 *
 * Chỉ ROLE_OWNER được phép gọi API này.
 * Không cho phép tạo ROLE_OWNER mới thông qua API này (phải validate ở Service).
 */
@Getter
@Setter
public class CreateUserRequest {

    /**
     * Tên đăng nhập — phải unique, chỉ chứa a-z, 0-9, dấu gạch dưới.
     *
     * 💡 Senior Note — Regex pattern chỉ cho a-z0-9_ (không uppercase):
     * Chuẩn hóa lowercase giúp tránh nhầm lẫn "Admin" vs "admin".
     * POS system thường có policy username lowercase để tránh case-sensitive bug.
     */
    @NotBlank(message = "Username không được để trống")
    @Size(min = 4, max = 50, message = "Username phải từ 4 đến 50 ký tự")
    @Pattern(regexp = "^[a-z0-9_]+$",
             message = "Username chỉ được chứa chữ thường (a-z), số (0-9) và dấu gạch dưới (_)")
    private String username;

    /**
     * Mật khẩu plain-text — sẽ được BCrypt encode ở Service.
     * KHÔNG bao giờ lưu raw password vào DB.
     */
    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, message = "Mật khẩu phải có ít nhất 8 ký tự")
    private String password;

    /**
     * Họ tên đầy đủ của nhân viên.
     */
    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 200, message = "Họ tên không vượt quá 200 ký tự")
    private String fullName;

    /**
     * Email của nhân viên (nullable).
     */
    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không vượt quá 100 ký tự")
    private String email;

    /**
     * Số điện thoại (nullable).
     * Validate 10 số bắt đầu bằng 0 hoặc +84.
     */
    @Pattern(regexp = "^(\\+84|0)[0-9]{9}$",
             message = "Số điện thoại không đúng định dạng (VD: 0912345678 hoặc +84912345678)")
    private String phone;

    /**
     * Vai trò của nhân viên.
     * OWNER KHÔNG được tạo OWNER khác qua API này — validate ở Service.
     * Chỉ được chọn: ROLE_MANAGER, ROLE_CASHIER, ROLE_WAREHOUSE_STAFF.
     */
    @NotNull(message = "Vai trò (roleName) không được để trống")
    private RoleName roleName;
}
