package com.example.store_clothes.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * CreateImportReceiptRequest - DTO nhận dữ liệu từ client để tạo Phiếu Nhập Hàng.
 *
 * Validation được thực hiện ở tầng Controller (qua @Valid).
 * Service KHÔNG cần validate lại những gì đã được ràng buộc ở đây.
 *
 * Nghiệp vụ bổ sung cần validate ở tầng Service (không thể validate bằng annotation):
 * - Supplier phải tồn tại trong DB.
 * - Mỗi variantId phải tồn tại và đang ACTIVE.
 * - paidAmount không được vượt quá totalAmount (được tính bởi Service).
 */
@Data
public class CreateImportReceiptRequest {

    /**
     * ID của nhà cung cấp. Bắt buộc, phải tồn tại trong DB.
     */
    @NotNull(message = "Nhà cung cấp không được để trống")
    private Long supplierId;

    /**
     * Danh sách hàng hóa cần nhập. Bắt buộc và không được rỗng.
     * @Valid kích hoạt validation xuống từng phần tử trong list.
     */
    @NotEmpty(message = "Danh sách hàng nhập không được rỗng")
    @Valid
    private List<ImportDetailRequest> items;

    /**
     * Số tiền thanh toán ngay. Mặc định = 0 nếu không truyền (mua chịu hoàn toàn).
     * Giá trị âm sẽ bị từ chối bởi @PositiveOrZero.
     */
    @PositiveOrZero(message = "Số tiền thanh toán không được âm")
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /**
     * Ghi chú thêm. Không bắt buộc.
     */
    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    private String note;

    /**
     * Chi tiết từng mặt hàng trong phiếu nhập.
     * Inner static class để đóng gói logic validation liên quan.
     */
    @Data
    public static class ImportDetailRequest {

        /**
         * ID của biến thể sản phẩm (ProductVariant.id). Bắt buộc.
         */
        @NotNull(message = "ID biến thể không được để trống")
        private Long variantId;

        /**
         * Số lượng nhập. Bắt buộc và phải > 0.
         */
        @NotNull(message = "Số lượng không được để trống")
        @Positive(message = "Số lượng phải lớn hơn 0")
        private Integer quantity;

        /**
         * Giá nhập tại thời điểm lập phiếu. Bắt buộc và phải > 0.
         * Đây là snapshot giá - sẽ được ghi vào ImportReceiptDetail.importPrice
         * và cập nhật vào ProductVariant.importPrice khi hoàn thành phiếu.
         */
        @NotNull(message = "Giá nhập không được để trống")
        @Positive(message = "Giá nhập phải lớn hơn 0")
        private BigDecimal importPrice;
    }
}
