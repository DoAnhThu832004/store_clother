package com.example.store_clothes.security;

import com.example.store_clothes.dto.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * CustomAuthenticationEntryPoint — Xử lý lỗi 401 Unauthorized từ Security Filter Chain.
 *
 * VẤN ĐỀ:
 * Khi request không mang JWT hoặc JWT đã hết hạn / sai chữ ký, Spring Security
 * mặc định trả về trang HTML 401, không phải JSON.
 *
 * GIẢI PHÁP:
 * Đăng ký handler này vào SecurityConfig.exceptionHandling().authenticationEntryPoint()
 * để đảm bảo mọi lỗi 401 trả về JSON chuẩn ApiResponse.
 *
 * KÍCH HOẠT KHI:
 * - Gọi endpoint cần xác thực mà không có Authorization header.
 * - JWT đã hết hạn, sai chữ ký, hoặc malformed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        log.warn("Unauthorized access [{}] {}: {}",
                request.getMethod(),
                request.getRequestURI(),
                authException.getMessage());

        ApiResponse<Void> body = ApiResponse.error(
                "UNAUTHORIZED",
                "Bạn chưa đăng nhập hoặc phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
