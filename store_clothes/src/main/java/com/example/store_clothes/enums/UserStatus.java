package com.example.store_clothes.enums;

/**
 * UserStatus - Trạng thái tài khoản người dùng.
 *
 * ACTIVE   → Tài khoản hoạt động bình thường.
 * INACTIVE → Tài khoản bị vô hiệu hóa (isEnabled() = false trong Spring Security).
 * LOCKED   → Tài khoản bị khóa tạm thời (isAccountNonLocked() = false).
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    LOCKED
}
