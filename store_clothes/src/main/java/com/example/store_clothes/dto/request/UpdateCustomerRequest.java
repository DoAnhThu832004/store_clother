package com.example.store_clothes.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * UpdateCustomerRequest — DTO cập nhật thông tin Khách Hàng (CUS-04).
 *
 * Quy tắc quan trọng:
 * - KHÔNG bao gồm loyaltyPoints và totalSpent: Hai trường này chỉ được cập nhật
 *   qua luồng nghiệp vụ cụ thể (OrderService, điều chỉnh điểm có kiểm soát).
 *   Nếu expose qua API update → nhân viên có thể tự ý tặng điểm không hợp lệ.
 * - Optimistic Lock: @Version được quản lý bởi Hibernate, không cần truyền từ client.
 *   Client cần load lại entity trước khi update để tránh xung đột.
 *
 * 💡 Senior Note — Tại sao không có "version" field trong request?
 *   @Version của Hibernate hoạt động ở DB level. Service sẽ load entity từ DB
 *   (version hiện tại), nếu entity đã bị sửa bởi thread khác → DB version mismatch
 *   → OptimisticLockException → GlobalExceptionHandler → HTTP 409.
 *   Client không cần gửi version vì lần nào cũng "làm mới" từ DB trước khi save.
 */
@Data
public class UpdateCustomerRequest {

    /**
     * Tên mới của khách hàng. Bắt buộc.
     */
    @NotBlank(message = "Tên khách hàng không được để trống")
    @Size(max = 200, message = "Tên không được vượt quá 200 ký tự")
    private String name;

    /**
     * Số điện thoại mới. Bắt buộc, validate unique ở Service.
     */
    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(
        regexp = "^(0[3|5|7|8|9])[0-9]{8}$",
        message = "Số điện thoại không hợp lệ (định dạng VN 10 chữ số, bắt đầu 03/05/07/08/09)"
    )
    private String phone;

    /**
     * Email mới. Không bắt buộc.
     */
    @Email(message = "Email không đúng định dạng")
    @Size(max = 150, message = "Email không được vượt quá 150 ký tự")
    private String email;
}
