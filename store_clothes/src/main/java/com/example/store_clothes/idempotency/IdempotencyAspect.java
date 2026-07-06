package com.example.store_clothes.idempotency;

import com.example.store_clothes.exception.DomainException;
import com.example.store_clothes.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * IdempotencyAspect — Xử lý chống trùng lặp request (API Idempotency) sử dụng Redis.
 *
 * CƠ CHẾ HOẠT ĐỘNG:
 *  1. Intercept các method có annotation @Idempotent.
 *  2. Đọc header "Idempotency-Key" từ HTTP Request.
 *     - Nếu không có: Ném lỗi Bad Request yêu cầu truyền Header này.
 *  3. Sinh Redis Key dạng: `idempotency:{request_uri}:{idempotency_key}`
 *  4. Thử set key vào Redis với value là "PROCESSING" và TTL tương ứng cấu hình.
 *     - Nếu set thành công: Cho phép method chạy tiếp.
 *       - Nếu method chạy thành công và trả về kết quả (thường là ResponseEntity hoặc ApiResponse):
 *         Ghi đè value của key thành "RESPONSE:{serialized_json_of_response}" giữ nguyên TTL còn lại.
 *       - Nếu method ném ra Exception (lỗi validation, lỗi hệ thống...):
 *         Xóa key khỏi Redis ngay lập tức để cho phép Client sửa dữ liệu và retry lại.
 *     - Nếu set thất bại (key đã tồn tại):
 *       - Đọc giá trị hiện tại của key từ Redis:
 *         - Nếu là "PROCESSING": Ném lỗi DUPLICATE_REQUEST (yêu cầu đang xử lý, vui lòng không gửi lại).
 *         - Nếu có tiền tố "RESPONSE:": Tách lấy phần JSON, phục hồi lại object Response trước đó
 *           và trả về trực tiếp cho Client ngay mà không chạy lại nghiệp vụ (idempotent response).
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final RedisTemplate<String, String> idempotencyRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String REDIS_KEY_PREFIX = "idempotency:";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String RESPONSE_PREFIX = "RESPONSE:";

    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        // 1. Lấy HttpServletRequest hiện tại
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        HttpServletRequest request = attributes.getRequest();

        // 2. Đọc Idempotency-Key từ header
        String idempotencyKey = request.getHeader(HEADER_IDEMPOTENCY_KEY);
        if (!StringUtils.hasText(idempotencyKey)) {
            log.warn("Missing Idempotency-Key header on idempotent endpoint: {}", request.getRequestURI());
            throw new DomainException(ErrorCode.INTERNAL_SERVER_ERROR, "HTTP Header 'Idempotency-Key' là bắt buộc đối với endpoint này.");
        }

        // 3. Tạo Redis Key kết hợp URI để tránh đụng độ key giữa các API khác nhau
        String requestUri = request.getRequestURI();
        String redisKey = REDIS_KEY_PREFIX + requestUri + ":" + idempotencyKey;
        long ttlSeconds = idempotent.ttlSeconds();

        log.debug("Checking idempotency key: {} with TTL: {}s", redisKey, ttlSeconds);

        // 4. Thực hiện setIfAbsent (SETNX) với value "PROCESSING" và TTL tương ứng
        Boolean success = idempotencyRedisTemplate.opsForValue().setIfAbsent(
                redisKey,
                STATUS_PROCESSING,
                Duration.ofSeconds(ttlSeconds)
        );

        if (Boolean.TRUE.equals(success)) {
            // Set key thành công -> Request này là DUY NHẤT tại thời điểm hiện tại. Tiến hành xử lý.
            try {
                Object result = joinPoint.proceed();
                
                // Lưu kết quả xử lý thành công vào Redis để trả về cho các request trùng lặp đến sau
                try {
                    String serializedResult = objectMapper.writeValueAsString(result);
                    // Lấy thời gian TTL còn lại của key để lưu đè chính xác không làm reset TTL gốc
                    Long remainingTtl = idempotencyRedisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
                    if (remainingTtl == null || remainingTtl <= 0) {
                        remainingTtl = ttlSeconds;
                    }
                    
                    idempotencyRedisTemplate.opsForValue().set(
                            redisKey,
                            RESPONSE_PREFIX + serializedResult,
                            Duration.ofSeconds(remainingTtl)
                    );
                    log.debug("Saved idempotent response to Redis for key: {}", redisKey);
                } catch (Exception e) {
                    log.error("Failed to serialize response for idempotency key: {}", redisKey, e);
                }
                
                return result;
            } catch (Throwable ex) {
                // Nếu xảy ra lỗi trong quá trình xử lý, xóa key khỏi Redis để cho phép retry
                idempotencyRedisTemplate.delete(redisKey);
                log.debug("Deleted idempotency key due to error: {}", redisKey);
                throw ex;
            }
        } else {
            // Set key thất bại -> Key đã tồn tại trong Redis.
            String currentValue = idempotencyRedisTemplate.opsForValue().get(redisKey);
            
            if (STATUS_PROCESSING.equals(currentValue)) {
                log.warn("Duplicate request detected and is still processing: {}", redisKey);
                throw new DomainException(ErrorCode.DUPLICATE_REQUEST, "Yêu cầu đang được hệ thống xử lý. Vui lòng không click liên tục.");
            }
            
            if (currentValue != null && currentValue.startsWith(RESPONSE_PREFIX)) {
                log.info("Duplicate request detected. Returning cached response for key: {}", redisKey);
                String jsonResponse = currentValue.substring(RESPONSE_PREFIX.length());
                
                // Lấy kiểu dữ liệu trả về của Method để deserialize chính xác
                MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                Method method = signature.getMethod();
                Class<?> returnType = method.getReturnType();
                
                try {
                    return objectMapper.readValue(jsonResponse, returnType);
                } catch (Exception e) {
                    log.error("Failed to deserialize cached response for key: {}", redisKey, e);
                    throw new DomainException(ErrorCode.DUPLICATE_REQUEST, "Yêu cầu đã được xử lý trước đó.");
                }
            }
            
            // Trường hợp dự phòng nếu key tồn tại nhưng value không hợp lệ
            throw new DomainException(ErrorCode.DUPLICATE_REQUEST);
        }
    }
}
