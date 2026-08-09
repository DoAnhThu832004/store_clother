package com.example.store_clothes.security;

import com.example.store_clothes.dto.response.ApiResponse;
import com.example.store_clothes.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * CustomAccessDeniedHandler — Xử lý lỗi 403 Forbidden từ Security Filter Chain.
 *
 * VẤN ĐỀ:
 * Khi Spring Security chặn request ở tầng Filter (URL-based rules như hasRole("OWNER")),
 * @RestControllerAdvice (GlobalExceptionHandler) KHÔNG được gọi vì request chưa
 * vào tới Controller. Spring Security mặc định trả về trang HTML 403, không phải JSON.
 *
 * GIẢI PHÁP:
 * Đăng ký handler này vào SecurityConfig.exceptionHandling() để ghi đè hành vi mặc định,
 * đảm bảo mọi lỗi 403 đều trả về JSON chuẩn ApiResponse.
 *
 * KÍCH HOẠT KHI:
 * - User đã đăng nhập (có JWT hợp lệ) nhưng KHÔNG đủ role để truy cập endpoint.
 * - Ví dụ: CASHIER gọi POST /api/v1/users (chỉ OWNER được phép).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {

        log.warn("Access denied [{}] {}: {}",
                request.getMethod(),
                request.getRequestURI(),
                accessDeniedException.getMessage());

        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.ACCESS_DENIED.name(),
                ErrorCode.ACCESS_DENIED.getDefaultMessage()
        );

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
