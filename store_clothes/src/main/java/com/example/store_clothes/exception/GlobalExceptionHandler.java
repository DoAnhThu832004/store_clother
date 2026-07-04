package com.example.store_clothes.exception;

import com.example.store_clothes.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.CannotAcquireLockException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler — Bộ xử lý ngoại lệ tập trung toàn bộ ứng dụng.
 *
 * NGUYÊN TẮC BẢO MẬT QUAN TRỌNG:
 * - TUYỆT ĐỐI không để Stack Trace lộ ra ngoài response trả về client.
 * - Log chi tiết lỗi ở phía Server (log.error/warn) để developer debug.
 * - Chỉ trả về message thân thiện và mã lỗi HTTP phù hợp cho Client.
 *
 * THỨ TỰ XỬ LÝ EXCEPTION (từ cụ thể → tổng quát):
 *  1. DomainException (lỗi nghiệp vụ có Domain Error Code)
 *  2. InsufficientStockException (lỗi tồn kho với context chi tiết)
 *  3. EntityNotFoundException (không tìm thấy entity)
 *  4. BusinessException (legacy — ưu tiên chuyển sang DomainException)
 *  5. MethodArgumentNotValidException (vi phạm @Valid)
 *  6. BadCredentialsException (sai thông tin đăng nhập)
 *  7. AccessDeniedException (không đủ quyền)
 *  8. CannotAcquireLockException (DB lock timeout)
 *  9. Exception (catch-all — lỗi hệ thống không xác định)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Xử lý: DomainException — Lỗi nghiệp vụ có Domain Error Code.
     *
     * Đây là handler TRUNG TÂM xử lý mọi lỗi nghiệp vụ mới.
     * HTTP status được lấy tự động từ ErrorCode enum.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException ex) {
        ErrorCode code = ex.getErrorCode();
        log.warn("[{}] {}", code.name(), ex.getMessage());

        ApiResponse<Void> body;
        if (ex.getDetails() != null) {
            body = ApiResponse.error(code.name(), ex.getMessage(), ex.getDetails());
        } else {
            body = ApiResponse.error(code.name(), ex.getMessage());
        }

        return ResponseEntity.status(code.getHttpStatus()).body(body);
    }

    /**
     * Xử lý: Không tìm thấy entity (ID không tồn tại, đã bị xóa mềm...)
     * → HTTP 404 Not Found
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("ENTITY_NOT_FOUND", ex.getMessage()));
    }

    /**
     * Xử lý: Vi phạm @Valid/@NotNull/@Size... trên Request DTO.
     * → HTTP 400 Bad Request với map chi tiết từng field lỗi.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            fieldErrors.put(field, message);
        });

        log.warn("Validation failed: {}", fieldErrors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .error(ApiResponse.ErrorDetail.builder()
                                .code("VALIDATION_FAILED")
                                .message("Dữ liệu đầu vào không hợp lệ")
                                .details(Map.copyOf(fieldErrors.entrySet().stream()
                                        .collect(java.util.stream.Collectors.toMap(
                                                Map.Entry::getKey,
                                                e -> (Object) e.getValue()
                                        ))))
                                .build())
                        .build());
    }

    /**
     * Xử lý: Tồn kho không đủ khi bán hàng.
     * Legacy handler — ưu tiên chuyển sang DomainException(ErrorCode.INSUFFICIENT_STOCK).
     * → HTTP 409 Conflict với chi tiết SKU, số cần, số có.
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientStock(
            InsufficientStockException ex) {

        log.warn("Insufficient stock: sku={}, requested={}, available={}",
                ex.getSku(), ex.getRequested(), ex.getAvailable());

        Map<String, Object> details = Map.of(
                "sku", ex.getSku(),
                "requested", ex.getRequested(),
                "available", ex.getAvailable()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.INSUFFICIENT_STOCK.name(), ex.getMessage(), details));
    }

    /**
     * Xử lý: Lỗi nghiệp vụ legacy (BusinessException cũ).
     * @deprecated Ưu tiên ném DomainException(ErrorCode.xxx) thay thế.
     * → HTTP 400 Bad Request
     */
    @Deprecated
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.warn("Business error (legacy): {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("BUSINESS_ERROR", ex.getMessage()));
    }

    /**
     * Xử lý: Sai username/password khi đăng nhập.
     * → HTTP 401 Unauthorized
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials login attempt");
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng"));
    }

    /**
     * Xử lý: Không có quyền truy cập tài nguyên.
     * → HTTP 403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.ACCESS_DENIED.name(), ErrorCode.ACCESS_DENIED.getDefaultMessage()));
    }

    /**
     * Xử lý: Optimistic Lock Exception — Xung đột ghi đồng thời.
     *
     * Khi 2 Manager cùng update giá biến thể, người sau commit sẽ gặp:
     *   ObjectOptimisticLockingFailureException (Spring wrap)
     *   hoặc OptimisticLockException (JPA thuần).
     * Hướng xử lý: Client nhận 409 → load lại trang → retry thủ công.
     * → HTTP 409 Conflict
     */
    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(Exception ex) {
        log.warn("Optimistic lock conflict — entity đã bị sửa bởi người khác: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("OPTIMISTIC_LOCK_CONFLICT",
                        "Dữ liệu vừa được cập nhật bởi người khác. Vui lòng tải lại và thử lại."));
    }

    /**
     * Xử lý: Timeout khi chờ acquire Pessimistic Lock ở DB (> 3 giây).
     *
     * Xảy ra khi hệ thống flash sale — nhiều thu ngân cùng lock một row ProductVariant.
     * Spring wrap LockTimeoutException thành CannotAcquireLockException.
     * → HTTP 409 Conflict
     */
    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ApiResponse<Void>> handleCannotAcquireLock(CannotAcquireLockException ex) {
        log.error("DB lock timeout — không thể acquire Pessimistic Lock: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("DB_LOCK_TIMEOUT",
                        "Hệ thống đang xử lý giao dịch khác cho mặt hàng này. Vui lòng thử lại sau giây lát."));
    }

    /**
     * Catch-all: Bắt mọi exception không được handle ở trên.
     * log.error ghi đầy đủ stack trace vào server log để developer debug.
     * TUYỆT ĐỐI không trả stack trace ra response!
     * → HTTP 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.name(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage()));
    }
}
