package com.example.store_clothes.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * CreateCustomerRequest — DTO nhận dữ liệu tạo mới Khách Hàng (CUS-01).
 *
 * Validation tầng DTO (format):
 *  - phone: Pattern E.164-lite cho số VN (10 chữ số, bắt đầu 0).
 *  - email: RFC-compliant email format.
 *  - name: Bắt buộc, không rỗng.
 *
 * Validation tầng Service (nghiệp vụ):
 *  - phone unique trong danh sách KH đang active.
 *  - Nếu phone tồn tại nhưng bị soft-delete → trả lỗi rõ ràng, KHÔNG auto-restore.
 *
 * 💡 Senior Note — @ValidPhone vs @Pattern:
 *  Dùng @Pattern trực tiếp đủ cho dự án này. Nếu hệ thống mở rộng ra thị trường
 *  quốc tế thì mới cần tạo custom @ValidPhone annotation với PhoneNumberUtil (libphonenumber).
 *  Tránh over-engineering khi chưa có requirement rõ ràng.
 */
@Data
public class CreateCustomerRequest {

    /**
     * Tên khách hàng. Bắt buộc, 1-200 ký tự.
     */
    @NotBlank(message = "Tên khách hàng không được để trống")
    @Size(max = 200, message = "Tên khách hàng không được vượt quá 200 ký tự")
    private String name;

    /**
     * Số điện thoại. Bắt buộc, định dạng số VN 10 chữ số (bắt đầu bằng 0).
     * Pattern hỗ trợ: 0xxxxxxxxx (ví dụ: 0912345678).
     */
    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(
        regexp = "^(0[3|5|7|8|9])[0-9]{8}$",
        message = "Số điện thoại không hợp lệ (định dạng VN 10 chữ số, bắt đầu 03/05/07/08/09)"
    )
    private String phone;

    /**
     * Email. Không bắt buộc, nhưng nếu có phải đúng format.
     */
    @Email(message = "Email không đúng định dạng")
    @Size(max = 150, message = "Email không được vượt quá 150 ký tự")
    private String email;

    /**
     * Ghi chú. Không bắt buộc, tối đa 500 ký tự.
     */
    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    private String note;
}
