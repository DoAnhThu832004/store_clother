package com.example.store_clothes.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * TenantRegisterRequest — DTO cho API Public: POST /api/v1/tenants/register
 *
 * LUỒNG SỬ DỤNG:
 * Một OWNER tiềm năng điền form đăng ký cửa hàng trên trang landing page.
 * Hệ thống nhận request này và tự động tạo:
 *   1. Bản ghi Tenant (cửa hàng)
 *   2. Tài khoản OWNER đầu tiên (gắn vào tenant vừa tạo)
 *   3. Danh mục mặc định "Chưa phân loại"
 *
 * PHÂN TÁCH TRÁCH NHIỆM:
 * - storeName, storeCode → tạo Tenant entity
 * - ownerUsername, ownerPassword, ownerFullName, ownerEmail, ownerPhone → tạo User OWNER
 *
 * SECURITY REMINDER:
 * - API này là PUBLIC (không cần JWT).
 * - Rate limiting nên được cấu hình ở API Gateway/Nginx để chống spam đăng ký.
 */
@Getter
@Setter
public class TenantRegisterRequest {

    // =========================================================================
    // Thông tin Cửa hàng (Tenant)
    // =========================================================================

    /**
     * Tên cửa hàng hiển thị. Ví dụ: "Thời trang Thu Hà".
     */
    @NotBlank(message = "Tên cửa hàng không được để trống")
    @Size(max = 200, message = "Tên cửa hàng không vượt quá 200 ký tự")
    private String storeName;

    /**
     * Mã định danh duy nhất của cửa hàng.
     * Dùng làm subdomain: {storeCode}.yoursaas.com
     * Quy tắc: chỉ chữ thường, số, dấu gạch ngang. Không bắt đầu/kết thúc bằng gạch ngang.
     * Ví dụ: "thuha-store", "shop123"
     */
    @NotBlank(message = "Mã cửa hàng không được để trống")
    @Size(min = 3, max = 100, message = "Mã cửa hàng phải từ 3 đến 100 ký tự")
    @Pattern(
        regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
        message = "Mã cửa hàng chỉ gồm chữ thường, số và dấu gạch ngang (không đầu, không cuối)"
    )
    private String storeCode;

    /**
     * Email liên hệ của cửa hàng (dùng để gửi thông báo, hóa đơn).
     * Không bắt buộc phải trùng ownerEmail.
     */
    @Email(message = "Email liên hệ không đúng định dạng")
    @Size(max = 150, message = "Email liên hệ không vượt quá 150 ký tự")
    private String contactEmail;

    /**
     * Số điện thoại liên hệ của cửa hàng.
     */
    @Pattern(
        regexp = "^(\\+84|0)[0-9]{9}$",
        message = "Số điện thoại không đúng định dạng (VD: 0912345678)"
    )
    private String contactPhone;

    // =========================================================================
    // Thông tin Tài khoản OWNER (được tạo cùng lúc với Tenant)
    // =========================================================================

    /**
     * Username đăng nhập của OWNER.
     * Duy nhất trong phạm vi cửa hàng (tenant) — sẽ được đảm bảo bởi composite unique.
     */
    @NotBlank(message = "Username không được để trống")
    @Size(min = 4, max = 50, message = "Username phải từ 4 đến 50 ký tự")
    @Pattern(
        regexp = "^[a-z0-9_]+$",
        message = "Username chỉ được chứa chữ thường, số và dấu gạch dưới"
    )
    private String ownerUsername;

    /**
     * Mật khẩu đăng nhập — sẽ được BCrypt encode ở Service.
     */
    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, message = "Mật khẩu phải có ít nhất 8 ký tự")
    private String ownerPassword;

    /**
     * Họ tên đầy đủ của OWNER.
     */
    @NotBlank(message = "Họ tên OWNER không được để trống")
    @Size(max = 200, message = "Họ tên không vượt quá 200 ký tự")
    private String ownerFullName;

    /**
     * Email cá nhân của OWNER.
     */
    @Email(message = "Email OWNER không đúng định dạng")
    @Size(max = 100, message = "Email không vượt quá 100 ký tự")
    private String ownerEmail;

    /**
     * Số điện thoại cá nhân của OWNER.
     */
    @Pattern(
        regexp = "^(\\+84|0)[0-9]{9}$",
        message = "Số điện thoại OWNER không đúng định dạng"
    )
    private String ownerPhone;
}
