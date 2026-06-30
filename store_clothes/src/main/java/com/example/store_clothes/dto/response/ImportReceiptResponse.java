package com.example.store_clothes.dto.response;

import com.example.store_clothes.enums.ImportReceiptStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ImportReceiptResponse - DTO trả về thông tin Phiếu Nhập Hàng cho client.
 *
 * Nguyên tắc thiết kế:
 * - Không expose trực tiếp Entity ra ngoài (tránh lộ sensitive data, tránh
 *   vòng lặp JSON serialization do quan hệ 2 chiều, tránh Lazy load exception).
 * - Chỉ include các trường thực sự cần thiết cho UI.
 * - Dữ liệu Supplier và Detail được flatten vào các inner DTO đơn giản.
 */
@Getter
@Builder
public class ImportReceiptResponse {

    /** ID của phiếu nhập */
    private Long id;

    /** Mã phiếu nhập, ví dụ: PN-20260626-0001 */
    private String receiptCode;

    /** Trạng thái phiếu */
    private ImportReceiptStatus status;

    /** Thông tin nhà cung cấp (rút gọn) */
    private SupplierSummary supplier;

    /** Tổng giá trị hàng hóa trong phiếu */
    private BigDecimal totalAmount;

    /** Số tiền đã thanh toán */
    private BigDecimal paidAmount;

    /** Số tiền còn nợ = totalAmount - paidAmount */
    private BigDecimal debtAmount;

    /** Ghi chú */
    private String note;

    /** Danh sách chi tiết hàng hóa */
    private List<DetailSummary> details;

    /** Thời điểm tạo phiếu */
    private LocalDateTime createdAt;

    /** Thời điểm cập nhật cuối */
    private LocalDateTime updatedAt;

    // =========================================================================
    // INNER DTOs
    // =========================================================================

    /**
     * Thông tin rút gọn của Nhà Cung Cấp - chỉ những trường cần hiển thị.
     */
    @Getter
    @Builder
    public static class SupplierSummary {
        private Long id;
        private String name;
        private String phone;
    }

    /**
     * Chi tiết từng mặt hàng trong phiếu nhập.
     */
    @Getter
    @Builder
    public static class DetailSummary {
        private Long variantId;
        private String sku;
        private String color;
        private String size;
        private Integer quantity;
        private BigDecimal importPrice;
        /** Thành tiền = quantity * importPrice */
        private BigDecimal lineTotal;
    }
}
