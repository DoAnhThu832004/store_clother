package com.example.store_clothes.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * ApiResponse&lt;T&gt; — Chuẩn Envelope DTO cho MỌI response trả về Client.
 *
 * SCHEMA THÀNH CÔNG:
 * {
 *   "success": true,
 *   "data": { ... },
 *   "message": "Thao tác thành công",
 *   "timestamp": "2026-06-30T15:20:36Z"
 * }
 *
 * SCHEMA LỖI (với Domain Error Code):
 * {
 *   "success": false,
 *   "error": {
 *     "code": "CATEGORY_HAS_ACTIVE_PRODUCTS",
 *     "message": "Không thể xóa danh mục đang chứa sản phẩm",
 *     "details": { "activeProductsCount": 12 }
 *   },
 *   "timestamp": "2026-06-30T15:20:36Z"
 * }
 *
 * @JsonInclude(NON_NULL): Field null KHÔNG serialize ra JSON → response gọn gàng.
 * @param &lt;T&gt; Kiểu dữ liệu của payload data.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** true = request thành công, false = có lỗi */
    private boolean success;

    /** Dữ liệu trả về — null khi response là lỗi hoặc thao tác không có data */
    private T data;

    /** Thông báo thành công ngắn gọn */
    private String message;

    /** Thông tin lỗi đầy đủ — chỉ có khi success = false */
    private ErrorDetail error;

    /** Thời điểm response được sinh ra (ISO 8601 UTC) */
    @Builder.Default
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant timestamp = Instant.now();

    // =========================================================================
    // INNER CLASS: ErrorDetail — Cấu trúc lỗi chi tiết
    // =========================================================================

    /**
     * Cấu trúc thông tin lỗi kèm Domain Error Code.
     * Frontend dùng "code" để ánh xạ ra message cục bộ hoặc xử lý luồng riêng.
     */
    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {
        /** Domain Error Code dạng SCREAMING_SNAKE_CASE — đồng bộ với enum ErrorCode */
        private String code;

        /** Message tiếng Việt mô tả lỗi */
        private String message;

        /**
         * Context data bổ sung — dùng khi lỗi cần trả thêm thông tin chi tiết.
         * Ví dụ: { "sku": "SKU-001", "available": 3, "requested": 10 }
         */
        private Map<String, Object> details;
    }

    // =========================================================================
    // STATIC FACTORY METHODS — Tạo response theo kịch bản phổ biến
    // =========================================================================

    /**
     * HTTP 200 OK với data và message mặc định.
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Thành công")
                .data(data)
                .build();
    }

    /**
     * HTTP 200 OK với data và message tùy chỉnh.
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * HTTP 200 OK chỉ message — dùng cho: xóa, cập nhật trạng thái, soft delete.
     */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
    }

    /**
     * HTTP 201 Created với data của resource vừa tạo.
     */
    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Tạo mới thành công")
                .data(data)
                .build();
    }

    /**
     * Response lỗi với Domain Error Code và message.
     * Dùng trong GlobalExceptionHandler khi xử lý DomainException.
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorDetail.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }

    /**
     * Response lỗi với Domain Error Code + context details.
     * Dùng khi lỗi cần trả thêm thông tin chi tiết (vd: tồn kho thiếu bao nhiêu).
     */
    public static <T> ApiResponse<T> error(String code, String message, Map<String, Object> details) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorDetail.builder()
                        .code(code)
                        .message(message)
                        .details(details)
                        .build())
                .build();
    }

    /**
     * Response lỗi đơn giản với HTTP status code và message — tương thích ngược.
     * @deprecated Ưu tiên dùng error(String code, String message) với Domain Error Code.
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public static <T> ApiResponse<T> error(int status, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorDetail.builder()
                        .code("HTTP_" + status)
                        .message(message)
                        .build())
                .build();
    }
}
