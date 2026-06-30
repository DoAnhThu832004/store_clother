package com.example.store_clothes.exception;

/**
 * Exception được ném khi không tìm thấy Entity trong database.
 * Tương đương HTTP 404 Not Found.
 *
 * Ví dụ sử dụng:
 *   throw new EntityNotFoundException("Sản phẩm với ID " + id + " không tồn tại");
 *   throw new EntityNotFoundException("Biến thể với SKU '" + sku + "' không tồn tại");
 *
 * GlobalExceptionHandler sẽ bắt exception này và trả về HTTP 404.
 *
 * LƯU Ý: Đây là custom exception của project, KHÔNG phải jakarta.persistence.EntityNotFoundException.
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }
}
