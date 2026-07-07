package com.example.store_clothes.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * UpdateImportReceiptRequest — DTO cập nhật Phiếu Nhập ở trạng thái DRAFT (IR-02).
 *
 * THIẾT KẾ NULLABLE PATCH:
 * Tất cả các field đều nullable. Nếu null → không thay đổi field đó.
 * Đây là "partial update" pattern (tương tự PATCH ngữ nghĩa nhưng dùng PUT về mặt HTTP).
 *
 * Nullable semantics:
 * - supplierId = null  → giữ NCC hiện tại, không đổi.
 * - items = null       → giữ danh sách items hiện tại, không đổi.
 * - paidAmount = null  → giữ paidAmount hiện tại, không đổi.
 * - note = null        → giữ note hiện tại, không đổi.
 *
 * Validation:
 * - Nếu supplierId được truyền → không được null/negative.
 * - Nếu items được truyền → không được rỗng, mỗi item phải valid.
 * - Nếu paidAmount được truyền → không được âm.
 */
@Data
public class UpdateImportReceiptRequest {

    /**
     * ID nhà cung cấp mới (nullable — null = không đổi NCC).
     * Nếu truyền → validate NCC tồn tại và đang active trong Service.
     */
    @Positive(message = "ID nhà cung cấp phải là số dương")
    private Long supplierId;

    /**
     * Danh sách items mới (nullable — null = không đổi items).
     * Nếu truyền → XÓA toàn bộ details cũ và tạo lại từ danh sách này.
     * Không được truyền list rỗng (dùng null nếu không muốn thay đổi).
     */
    @Valid
    @Size(min = 1, message = "Danh sách hàng nhập không được rỗng (dùng null nếu không muốn thay đổi)")
    private List<ImportDetailRequest> items;

    /**
     * Số tiền thanh toán mới (nullable — null = không đổi paidAmount).
     */
    @PositiveOrZero(message = "Số tiền thanh toán không được âm")
    private BigDecimal paidAmount;

    /**
     * Ghi chú mới (nullable — null = không đổi note).
     */
    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    private String note;

    /**
     * DTO cho từng dòng chi tiết hàng nhập.
     * Cùng cấu trúc với CreateImportReceiptRequest.ImportDetailRequest.
     */
    @Data
    public static class ImportDetailRequest {

        @NotNull(message = "ID biến thể không được để trống")
        private Long variantId;

        @NotNull(message = "Số lượng không được để trống")
        @Positive(message = "Số lượng phải lớn hơn 0")
        private Integer quantity;

        @NotNull(message = "Giá nhập không được để trống")
        @Positive(message = "Giá nhập phải lớn hơn 0")
        private BigDecimal importPrice;
    }
}
