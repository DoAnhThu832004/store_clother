package com.example.store_clothes.exception;

import java.util.Map;

/**
 * DomainException — Exception chuẩn hóa mang Domain Error Code và optional context data.
 *
 * ĐÂY LÀ EXCEPTION TRUNG TÂM của hệ thống thay thế cho BusinessException cũ.
 * Ưu điểm so với BusinessException:
 *  - Mang ErrorCode (enum) thay vì chuỗi text cứng.
 *  - Mang thêm details (Map) để trả về context data chi tiết cho Frontend.
 *  - GlobalExceptionHandler tự động lấy HTTP status từ ErrorCode.
 *
 * CÁC CÁCH DÙNG:
 *
 *   // Ném lỗi với message mặc định từ ErrorCode:
 *   throw new DomainException(ErrorCode.CATEGORY_NOT_FOUND);
 *
 *   // Ném lỗi với message tùy chỉnh:
 *   throw new DomainException(ErrorCode.INSUFFICIENT_STOCK, "Hàng [SKU-001] chỉ còn 3 sản phẩm");
 *
 *   // Ném lỗi với context details để Frontend hiển thị:
 *   throw new DomainException(ErrorCode.INSUFFICIENT_STOCK, Map.of(
 *       "sku", "SKU-001",
 *       "requested", 10,
 *       "available", 3
 *   ));
 *
 *   // Ném lỗi với cả message tùy chỉnh lẫn details:
 *   throw new DomainException(ErrorCode.CATEGORY_HAS_ACTIVE_PRODUCTS,
 *       "Danh mục chứa 12 sản phẩm còn hoạt động",
 *       Map.of("activeProductsCount", 12));
 */
public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    /**
     * Ném lỗi với message mặc định từ ErrorCode.
     */
    public DomainException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    /**
     * Ném lỗi với message tùy chỉnh (override message mặc định của ErrorCode).
     */
    public DomainException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.details = null;
    }

    /**
     * Ném lỗi với message mặc định + context data chi tiết.
     */
    public DomainException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.details = details;
    }

    /**
     * Ném lỗi với message tùy chỉnh + context data chi tiết.
     */
    public DomainException(ErrorCode errorCode, String customMessage, Map<String, Object> details) {
        super(customMessage);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
