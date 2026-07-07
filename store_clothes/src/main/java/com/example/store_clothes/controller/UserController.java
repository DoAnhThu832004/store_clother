package com.example.store_clothes.controller;

import com.example.store_clothes.dto.request.CreateUserRequest;
import com.example.store_clothes.dto.request.UpdateUserRequest;
import com.example.store_clothes.dto.response.ApiResponse;
import com.example.store_clothes.dto.response.UserDetailResponse;
import com.example.store_clothes.dto.response.UserResponse;
import com.example.store_clothes.entity.User;
import com.example.store_clothes.enums.RoleName;
import com.example.store_clothes.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * UserController — Controller quản lý nhân viên (5 APIs: USR-01 đến USR-05).
 *
 * Base URL: /api/v1/users
 *
 * Controller layer CỰC KỲ mỏng — delegate toàn bộ business logic sang UserService.
 * @AuthenticationPrincipal: Inject User entity trực tiếp từ SecurityContext.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "API quản lý nhân viên — yêu cầu ROLE_OWNER hoặc ROLE_MANAGER")
public class UserController {

    private final UserService userService;

    // =========================================================================
    // USR-01 — TẠO NHÂN VIÊN: POST /api/v1/users
    // =========================================================================

    /**
     * POST /api/v1/users
     *
     * Tạo tài khoản nhân viên mới.
     * Chỉ ROLE_OWNER được phép gọi.
     * KHÔNG cho phép tạo ROLE_OWNER khác qua endpoint này.
     *
     * @param request     Thông tin nhân viên mới
     * @param currentUser User đang đăng nhập (OWNER)
     * @return 201 Created + UserResponse
     */
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    @Operation(
        summary = "USR-01: Tạo nhân viên mới",
        description = """
            Chỉ OWNER được tạo tài khoản nhân viên.
            KHÔNG cho tạo tài khoản OWNER khác qua đây.
            Password được BCrypt encode với strength 12 trước khi lưu.
            Ghi AuditLog action=CREATE_USER bất đồng bộ.
            """
    )
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal User currentUser) {

        log.info("API createUser: username={}, role={}, by={}",
                request.getUsername(), request.getRoleName(), currentUser.getUsername());

        UserResponse response = userService.createUser(
                request, currentUser.getId(), currentUser.getUsername());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    // =========================================================================
    // USR-02 — DANH SÁCH NHÂN VIÊN: GET /api/v1/users
    // =========================================================================

    /**
     * GET /api/v1/users?page=0&size=20&keyword=&roleName=
     *
     * Danh sách nhân viên với filter keyword (username hoặc fullName) + roleName.
     * OWNER và MANAGER được phép xem.
     *
     * @param keyword  Từ khóa tìm theo username HOẶC fullName
     * @param roleName Filter theo vai trò (null = tất cả)
     * @param page     Số trang (bắt đầu từ 0)
     * @param size     Số phần tử/trang (mặc định 20)
     * @return 200 OK + Page<UserResponse>
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @Operation(
        summary = "USR-02: Danh sách nhân viên",
        description = """
            Tìm kiếm nhân viên theo keyword (username hoặc fullName) và lọc theo roleName.
            KHÔNG trả về passwordHash hay refresh token.
            Kết quả phân trang, sắp xếp mới nhất trước.
            """
    )
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getUsers(
            @Parameter(description = "Từ khóa tìm theo username hoặc fullName")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Lọc theo vai trò: ROLE_OWNER | ROLE_MANAGER | ROLE_CASHIER | ROLE_WAREHOUSE_STAFF")
            @RequestParam(required = false) RoleName roleName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<UserResponse> result = userService.getUsers(keyword, roleName, pageable);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // =========================================================================
    // USR-03 — CHI TIẾT NHÂN VIÊN: GET /api/v1/users/{id}
    // =========================================================================

    /**
     * GET /api/v1/users/{id}
     *
     * Chi tiết nhân viên kèm thống kê: tổng hóa đơn và phiếu nhập đã tạo.
     * OWNER và MANAGER được phép xem.
     *
     * @param id ID nhân viên cần xem chi tiết
     * @return 200 OK + UserDetailResponse
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    @Operation(
        summary = "USR-03: Chi tiết nhân viên",
        description = """
            Lấy chi tiết nhân viên kèm:
            - Thông tin cơ bản (không có password).
            - roles: Set<String> tên các vai trò.
            - totalOrdersCreated: số hóa đơn đã tạo (từ AuditLog).
            - totalImportsCreated: số phiếu nhập kho đã tạo (từ AuditLog).
            - lastLoginAt: thời điểm đăng nhập gần nhất.
            """
    )
    public ResponseEntity<ApiResponse<UserDetailResponse>> getUserDetail(
            @Parameter(description = "ID nhân viên cần xem", required = true)
            @PathVariable Long id) {

        UserDetailResponse response = userService.getUserDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // =========================================================================
    // USR-04 — CẬP NHẬT & ĐỔI ROLE: PUT /api/v1/users/{id}
    // =========================================================================

    /**
     * PUT /api/v1/users/{id}
     *
     * Cập nhật thông tin và/hoặc đổi phân quyền nhân viên.
     * Chỉ ROLE_OWNER được phép gọi.
     * KHÔNG cho đổi username hoặc password qua đây.
     *
     * @param id          ID nhân viên cần cập nhật
     * @param request     Thông tin cần cập nhật (tất cả nullable — PATCH semantic)
     * @param currentUser User đang đăng nhập (OWNER)
     * @return 200 OK + UserResponse sau cập nhật
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(
        summary = "USR-04: Cập nhật thông tin & đổi phân quyền",
        description = """
            Cập nhật fullName, email, phone, status và/hoặc roleName.
            KHÔNG cho phép đổi username và password qua endpoint này.
            Nếu roleName thay đổi → xóa toàn bộ role cũ, gán role mới.
            KHÔNG cho đổi role của chính mình (ngăn self-privilege-escalation).
            KHÔNG cho hạ cấp OWNER cuối cùng còn lại.
            Ghi AuditLog action=UPDATE_USER_ROLE khi đổi role.
            """
    )
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @Parameter(description = "ID nhân viên cần cập nhật", required = true)
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal User currentUser) {

        log.info("API updateUser: targetId={}, by={}", id, currentUser.getUsername());

        UserResponse response = userService.updateUser(
                id, request, currentUser.getId(), currentUser.getUsername());

        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật nhân viên thành công."));
    }

    // =========================================================================
    // USR-05 — XÓA MỀM: DELETE /api/v1/users/{id}
    // =========================================================================

    /**
     * DELETE /api/v1/users/{id}
     *
     * Soft delete tài khoản nhân viên — KHÔNG xóa vật lý khỏi DB.
     * @SQLDelete trên User entity sẽ: set is_deleted=true, status=LOCKED,
     * rename username và email thêm "_deleted_UNIX_TIMESTAMP".
     *
     * Chỉ ROLE_OWNER được phép gọi.
     * KHÔNG cho tự xóa chính mình.
     * KHÔNG cho xóa OWNER cuối cùng còn lại.
     *
     * @param id          ID nhân viên cần xóa
     * @param currentUser User đang đăng nhập (OWNER)
     * @return 200 OK + message xác nhận
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(
        summary = "USR-05: Xóa mềm tài khoản nhân viên",
        description = """
            Soft delete: set is_deleted=true, status=LOCKED.
            Rename username → username_deleted_UNIX_TIMESTAMP (giải phóng UNIQUE constraint).
            Rename email → email_deleted_UNIX_TIMESTAMP (giải phóng UNIQUE constraint).
            KHÔNG cho tự xóa chính mình.
            KHÔNG cho xóa OWNER cuối cùng còn lại trong hệ thống.
            Ghi AuditLog action=DELETE_USER bất đồng bộ.
            """
    )
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @Parameter(description = "ID nhân viên cần xóa", required = true)
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        log.info("API deleteUser: targetId={}, by={}", id, currentUser.getUsername());

        userService.deleteUser(id, currentUser.getId(), currentUser.getUsername());

        return ResponseEntity.ok(ApiResponse.success("Tài khoản nhân viên đã được khóa và xóa mềm thành công."));
    }
}
