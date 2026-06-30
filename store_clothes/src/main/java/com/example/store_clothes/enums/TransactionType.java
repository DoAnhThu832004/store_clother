package com.example.store_clothes.enums;

/**
 * TransactionType - Phân loại giao dịch trong Thẻ Kho (StockHistory).
 *
 * Mỗi bản ghi trong bảng stock_history bắt buộc phải có một TransactionType
 * để xác định nguồn gốc của sự biến động tồn kho.
 *
 * Nguyên tắc:
 * - IMPORT: Số lượng tăng (dương +) - khi nhập hàng từ nhà cung cấp.
 * - EXPORT: Số lượng giảm (âm -) - khi bán hàng, xuất kho nội bộ.
 * - ADJUSTMENT: Điều chỉnh tồn kho thủ công (kiểm kê). Có thể dương hoặc âm.
 * - RETURN: Khách trả hàng - số lượng tăng.
 */
public enum TransactionType {

    /**
     * Nhập hàng từ nhà cung cấp (Import Receipt).
     * changeQuantity > 0 (dương).
     */
    IMPORT,

    /**
     * Xuất kho theo đơn bán hàng hoặc xuất nội bộ.
     * changeQuantity < 0 (âm).
     */
    EXPORT,

    /**
     * Điều chỉnh tồn kho thủ công sau kiểm kê.
     * changeQuantity có thể dương hoặc âm.
     */
    ADJUSTMENT,

    /**
     * Khách hàng trả lại hàng.
     * changeQuantity > 0 (dương).
     */
    RETURN
}
