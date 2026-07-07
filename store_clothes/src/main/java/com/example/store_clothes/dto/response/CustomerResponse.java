package com.example.store_clothes.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CustomerResponse — DTO trả về thông tin Khách Hàng cho danh sách (CUS-02).
 *
 * Nguyên tắc thiết kế:
 * - Không expose Entity trực tiếp (tránh vòng lặp JSON, Lazy load exception, lộ dữ liệu nhạy cảm).
 * - Danh sách: Chỉ bao gồm các trường thực sự cần thiết để hiển thị.
 * - loyaltyPoints và totalSpent được include để hỗ trợ filter hasLoyaltyPoints.
 */
@Getter
@Builder
public class CustomerResponse {

    /** ID khách hàng */
    private Long id;

    /** Tên khách hàng */
    private String name;

    /** Số điện thoại */
    private String phone;

    /** Email */
    private String email;

    /** Điểm tích lũy hiện tại */
    private Integer loyaltyPoints;

    /**
     * Tổng số tiền đã chi tiêu (denormalized).
     * Dùng để phân loại khách hàng VIP, hiển thị trong danh sách.
     */
    private BigDecimal totalSpent;

    /** Ghi chú */
    private String note;

    /** Thời điểm tạo */
    private LocalDateTime createdAt;

    /** Thời điểm cập nhật */
    private LocalDateTime updatedAt;
}
