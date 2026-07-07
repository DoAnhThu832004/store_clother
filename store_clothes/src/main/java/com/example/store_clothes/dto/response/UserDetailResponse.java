package com.example.store_clothes.dto.response;

import com.example.store_clothes.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * UserDetailResponse — DTO chi tiết nhân viên (USR-03).
 *
 * Bao gồm thông tin cơ bản + thống kê hoạt động của nhân viên.
 * KHÔNG chứa passwordHash, refreshToken.
 *
 * 💡 Senior Note — Tại sao tách UserResponse và UserDetailResponse?
 * Danh sách (USR-02): Cần compact, nhanh, không cần thống kê → ít field hơn.
 * Chi tiết (USR-03): Cần đầy đủ kể cả thống kê → có thêm totalOrdersCreated, totalImportsCreated.
 * Nếu dùng chung 1 DTO → phải tính thống kê cho mỗi row trong danh sách → N+1 query performance nightmare.
 */
@Getter
@Builder
public class UserDetailResponse {

    private Long id;

    /** Tên đăng nhập — readonly. */
    private String username;

    /** Họ tên đầy đủ. */
    private String fullName;

    /** Email (nullable). */
    private String email;

    /** Số điện thoại (nullable). */
    private String phone;

    /** Trạng thái tài khoản: ACTIVE / INACTIVE / LOCKED. */
    private UserStatus status;

    /**
     * Danh sách vai trò (Set<String> để tránh expose entity internal).
     * Ví dụ: {"ROLE_MANAGER"}.
     */
    private Set<String> roles;

    /** Thời điểm tạo tài khoản. */
    private LocalDateTime createdAt;

    /** Thời điểm cập nhật lần cuối. */
    private LocalDateTime updatedAt;

    /** Thời điểm đăng nhập gần nhất (null nếu chưa bao giờ đăng nhập). */
    private LocalDateTime lastLoginAt;

    // =========================================================================
    // THỐNG KÊ HOẠT ĐỘNG
    // =========================================================================

    /**
     * Tổng số hóa đơn nhân viên này đã tạo (từ AuditLog action=CHECKOUT).
     * Dùng để đánh giá năng suất thu ngân.
     */
    private Long totalOrdersCreated;

    /**
     * Tổng số phiếu nhập kho nhân viên này đã tạo (từ AuditLog action=CREATE_IMPORT_RECEIPT).
     * Dùng để đánh giá năng suất nhân viên kho.
     */
    private Long totalImportsCreated;
}
