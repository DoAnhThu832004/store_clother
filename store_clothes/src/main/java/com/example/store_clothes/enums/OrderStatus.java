package com.example.store_clothes.enums;

/**
 * OrderStatus — Trạng thái vòng đời của hóa đơn bán hàng POS.
 *
 * COMPLETED → Đơn đã thanh toán thành công (trạng thái mặc định sau checkout).
 * REFUNDED  → Đơn đã bị hủy và hoàn kho.
 * PENDING   → Đơn đang chờ xử lý (dự phòng cho luồng tương lai: giao hàng, layaway...).
 *
 * 💡 Senior Note — Tại sao dùng REFUNDED thay vì CANCELLED?
 * "Hủy" đơn trong hệ thống POS đồng nghĩa với "hoàn lại hàng về kho".
 * REFUNDED tường minh hơn CANCELLED vì nó phản ánh đúng nghiệp vụ:
 * không chỉ hủy trạng thái đơn mà còn có hành động hoàn kho kèm theo.
 * Frontend và báo cáo tài chính có thể dùng REFUNDED để lọc đơn cần đối soát.
 */
public enum OrderStatus {

    /**
     * Đơn đã hoàn thành thanh toán — trạng thái mặc định sau khi checkout thành công.
     * Inventory đã được trừ, StockHistory đã ghi với TransactionType.EXPORT.
     */
    COMPLETED,

    /**
     * Đơn đã bị hủy và toàn bộ hàng đã được hoàn kho.
     * Inventory đã được cộng lại, StockHistory mới đã ghi với TransactionType.RETURN.
     * LoyaltyPoints (nếu có) đã bị trừ lại tương ứng.
     */
    REFUNDED,

    /**
     * Đơn đang chờ xử lý — dự phòng cho luồng tương lai.
     * Hiện tại hệ thống POS thanh toán ngay → PENDING ít được dùng.
     */
    PENDING
}
