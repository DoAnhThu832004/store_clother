package com.example.store_clothes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO nhận dữ liệu khi cập nhật thông tin sản phẩm gốc (TICKET P-01).
 *
 * 💡 Senior Note — Tại sao KHÔNG có trường "code" ở đây?
 * Mã sản phẩm (code) là khóa nghiệp vụ BẤT BIẾN sau khi tạo.
 * Lý do kỹ thuật:
 *   1. OrderItem snapshot: Khi tạo đơn hàng, hệ thống lưu snapshot của sản phẩm
 *      vào OrderItem (bao gồm product code). Nếu đổi code, tất cả tham chiếu
 *      lịch sử trong báo cáo, đơn hàng cũ sẽ không còn khớp với sản phẩm hiện tại.
 *   2. StockHistory references: Thẻ kho dùng referenceCode để trace nguồn gốc.
 *   3. Business logic integrity: Code là "Natural ID" trong nhiều hệ thống tích hợp
 *      (POS, ERP). Đổi code = đứt toàn bộ tích hợp bên ngoài.
 *
 * NGUYÊN TẮC: Đưa vào DTO CHỈ những gì client được phép thay đổi.
 */
public record UpdateProductRequest(

        @NotBlank(message = "Tên sản phẩm không được để trống")
        @Size(max = 200, message = "Tên sản phẩm không quá 200 ký tự")
        String name,

        // Mô tả có thể null (xóa mô tả cũ)
        String description,

        // categoryId nullable: null = giữ nguyên category hiện tại (hoặc bỏ category)
        Long categoryId

) {}
