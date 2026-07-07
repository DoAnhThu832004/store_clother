package com.example.store_clothes.dto.request;

import com.example.store_clothes.enums.RoleName;
import com.example.store_clothes.enums.UserStatus;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * UpdateUserRequest — DTO cho API USR-04: Cập nhật thông tin & đổi phân quyền nhân viên.
 *
 * Chỉ ROLE_OWNER được phép gọi API này.
 * KHÔNG cho phép đổi username hoặc password qua endpoint này.
 * Tất cả field đều nullable — null = không cập nhật field đó (PATCH semantic).
 */
@Getter
@Setter
public class UpdateUserRequest {

    /**
     * Họ tên mới (nullable — null = không cập nhật).
     */
    @Size(max = 200, message = "Họ tên không vượt quá 200 ký tự")
    private String fullName;

    /**
     * Email mới (nullable — null = không cập nhật).
     */
    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không vượt quá 100 ký tự")
    private String email;

    /**
     * Số điện thoại mới (nullable — null = không cập nhật).
     */
    @Pattern(regexp = "^(\\+84|0)[0-9]{9}$",
             message = "Số điện thoại không đúng định dạng (VD: 0912345678 hoặc +84912345678)")
    private String phone;

    /**
     * Trạng thái tài khoản mới (nullable — null = không cập nhật).
     * ACTIVE / INACTIVE / LOCKED.
     *
     * 💡 Senior Note — Tại sao cho phép set LOCKED qua đây?
     * Đây là "khóa tạm thời" (giống suspend) — khác với soft delete.
     * Khóa tạm thời: user vẫn tồn tại trong DB, có thể mở lại.
     * Soft delete (USR-05): rename username + set is_deleted=true → không thể đăng nhập lại.
     */
    private UserStatus status;

    /**
     * Vai trò mới (nullable — null = không đổi role).
     * Nếu cung cấp → xóa toàn bộ role cũ, gán role mới.
     *
     * 💡 Senior Note — Tại sao không cho tự đổi role của mình?
     * Nếu OWNER A có thể tự đổi role của mình thành ROLE_CASHIER:
     * (1) Hệ thống mất đi một OWNER.
     * (2) Nếu không còn OWNER nào khác → toàn hệ thống bị lock như trường hợp USR-05.
     * (3) Tạo lỗ hổng privilege escalation: user cố tình downgrade role mình,
     *     sau đó request admin khác nâng role → audit trail bị nhiễu.
     * Kiểm tra id != currentUserId bắt buộc ở Service layer.
     */
    private RoleName roleName;
}
