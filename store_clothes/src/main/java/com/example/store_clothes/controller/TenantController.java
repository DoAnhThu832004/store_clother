package com.example.store_clothes.controller;

import com.example.store_clothes.dto.request.TenantRegisterRequest;
import com.example.store_clothes.dto.response.TenantRegisterResponse;
import com.example.store_clothes.service.TenantOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * TenantController — API endpoints cho quản lý Tenant (Cửa hàng).
 *
 * PHÂN QUYỀN:
 * - POST /register: PUBLIC — Không cần JWT. Ai cũng có thể đăng ký cửa hàng.
 * - Các endpoint khác (nếu có): Chỉ SUPER_ADMIN mới được truy cập.
 *
 * BASE PATH: /api/v1/tenants
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantOnboardingService tenantOnboardingService;

    /**
     * Đăng ký cửa hàng mới (Onboarding Flow).
     *
     * PUBLIC endpoint — không cần JWT.
     * Transaction bao gồm: Tenant + User OWNER + Category mặc định.
     *
     * HTTP 201 Created — thể hiện rằng một resource mới (Tenant) đã được tạo.
     *
     * @param request Thông tin đăng ký đã được validate bởi @Valid
     * @return 201 Created với thông tin cửa hàng vừa tạo
     */
    @PostMapping("/register")
    public ResponseEntity<TenantRegisterResponse> register(
            @Valid @RequestBody TenantRegisterRequest request
    ) {
        log.info("Tenant registration request: storeCode={}, ownerUsername={}",
                request.getStoreCode(), request.getOwnerUsername());

        TenantRegisterResponse response = tenantOnboardingService.register(request);

        log.info("Tenant registered successfully: storeCode={}", request.getStoreCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
