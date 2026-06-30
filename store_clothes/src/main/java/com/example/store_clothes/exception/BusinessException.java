package com.example.store_clothes.exception;

/**
 * Exception chung cho các lỗi nghiệp vụ (Business Logic Errors).
 * Được ném khi dữ liệu đầu vào hợp lệ về mặt format nhưng vi phạm quy tắc nghiệp vụ.
 * Ví dụ: Tạo sản phẩm với mã code đã tồn tại, SKU trùng lặp.
 *
 * GlobalExceptionHandler sẽ bắt exception này và trả về HTTP 400 Bad Request.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
