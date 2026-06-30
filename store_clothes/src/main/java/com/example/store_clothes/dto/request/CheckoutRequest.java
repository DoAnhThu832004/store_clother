package com.example.store_clothes.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * CheckoutRequest - Dữ liệu đầu vào cho API thanh toán hóa đơn.
 *
 * @Valid trên items: Kích hoạt validation đệ quy xuống từng CheckoutItemRequest.
 * @NotEmpty: Không cho phép giỏ hàng rỗng.
 */
@Getter
@Setter
public class CheckoutRequest {

    /**
     * Danh sách sản phẩm trong giỏ hàng.
     * Phải có ít nhất 1 sản phẩm. @Valid kích hoạt validation cho nested object.
     */
    @NotEmpty(message = "Giỏ hàng không được rỗng")
    @Valid
    private List<CheckoutItemRequest> items;

    /**
     * Số tiền khách đưa. Phải >= tổng tiền hàng (validate trong Service).
     */
    @NotNull(message = "Số tiền khách thanh toán không được để trống")
    @DecimalMin(value = "0", inclusive = false, message = "Số tiền khách đưa phải > 0")
    private BigDecimal paidAmount;

    /**
     * Ghi chú của thu ngân (không bắt buộc).
     */
    @Size(max = 500, message = "Ghi chú không vượt quá 500 ký tự")
    private String note;

    // =========================================================================
    // NESTED DTO — Dòng sản phẩm trong giỏ hàng
    // =========================================================================

    @Getter
    @Setter
    public static class CheckoutItemRequest {

        /**
         * ID của ProductVariant.
         */
        @NotNull(message = "variantId không được để trống")
        @Positive(message = "variantId phải là số dương")
        private Long variantId;

        /**
         * Số lượng mua.
         */
        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng mua tối thiểu là 1")
        private Integer quantity;
    }
}
