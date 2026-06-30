package com.example.store_clothes.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception đặc thù cho nghiệp vụ bán hàng: Số lượng tồn kho không đủ.
 *
 * Chứa thêm context data (sku, requested, available) để Frontend có thể
 * hiển thị thông báo chi tiết cho người dùng.
 *
 * GlobalExceptionHandler sẽ bắt và trả về HTTP 409 Conflict với body chi tiết.
 */
@Getter
@ResponseStatus(HttpStatus.CONFLICT)
public class InsufficientStockException extends RuntimeException {

    private final String sku;
    private final int requested;
    private final int available;

    public InsufficientStockException(String sku, int requested, int available) {
        super(String.format("Hàng [%s] không đủ số lượng. Yêu cầu: %d, tồn kho: %d",
                sku, requested, available));
        this.sku = sku;
        this.requested = requested;
        this.available = available;
    }
}
