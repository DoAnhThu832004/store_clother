package com.example.store_clothes.dto.request;

import com.example.store_clothes.entity.ProductStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * DTO nhận dữ liệu khi cập nhật giá và trạng thái biến thể (TICKET PV-02).
 *
 * THIẾT KẾ "PATCH SEMANTICS":
 * - Tất cả field đều nullable → null = không thay đổi trường đó.
 * - Service chỉ update những field nào không null.
 * - Không cho phép sửa inventory qua đây (xem TICKET PV-02b).
 *
 * 💡 Senior Note — Tại sao không cho sửa inventory trực tiếp qua đây?
 * Inventory là dữ liệu tài chính nhạy cảm, mọi thay đổi phải có:
 *   1. StockHistory record để audit trail đầy đủ.
 *   2. Pessimistic Lock để tránh race condition.
 *   3. AuditLog với reason bắt buộc.
 * Endpoint UPDATE_VARIANT chỉ là "update metadata", không phải "adjust stock".
 * Tách biệt concern = tách biệt endpoint = tách biệt authorization.
 */
public record UpdateVariantRequest(

        // null = không đổi giá bán
        @Positive(message = "Giá bán phải lớn hơn 0")
        @Digits(integer = 12, fraction = 2, message = "Giá bán tối đa 12 chữ số nguyên, 2 thập phân")
        BigDecimal salePrice,

        // null = không đổi giá nhập
        @Positive(message = "Giá nhập phải lớn hơn 0")
        @Digits(integer = 12, fraction = 2, message = "Giá nhập tối đa 12 chữ số nguyên, 2 thập phân")
        BigDecimal importPrice,

        // null = không đổi trạng thái
        ProductStatus status

) {}
