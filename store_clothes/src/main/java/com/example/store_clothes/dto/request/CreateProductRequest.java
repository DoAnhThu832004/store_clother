package com.example.store_clothes.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * DTO nhận dữ liệu khi tạo mới một Sản phẩm (gốc + biến thể).
 *
 * Sử dụng @Valid kết hợp với @NotNull, @NotBlank, @Size để validate đầu vào
 * ngay tại tầng Controller, trước khi vào Service.
 *
 * DESIGN DECISION:
 * - Bắt buộc tạo ít nhất 1 Variant khi tạo Product.
 *   Lý do: Một sản phẩm không có biến thể nào không thể bán được,
 *   việc tạo Product rỗng sẽ gây dữ liệu không nhất quán.
 *
 * Sử dụng Java Record (Java 16+) thay vì class thông thường cho DTO:
 * - Tự động có constructor, getter, equals, hashCode, toString.
 * - Immutable theo mặc định - phù hợp với Request DTO.
 */
public record CreateProductRequest(

        @NotBlank(message = "Tên sản phẩm không được để trống")
        @Size(max = 255, message = "Tên sản phẩm không quá 255 ký tự")
        String name,

        @NotBlank(message = "Mã sản phẩm không được để trống")
        @Size(max = 50, message = "Mã sản phẩm không quá 50 ký tự")
        String code,

        String description,

        @NotBlank(message = "Trạng thái sản phẩm không được để trống")
        String status,

        @NotEmpty(message = "Phải có ít nhất một biến thể sản phẩm")
        @Valid // Kích hoạt validate cho từng phần tử trong list
        List<CreateVariantRequest> variants

) {}
