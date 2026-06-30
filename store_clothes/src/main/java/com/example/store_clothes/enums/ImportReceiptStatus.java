package com.example.store_clothes.enums;

/**
 * ImportReceiptStatus - Trạng thái vòng đời của một Phiếu Nhập Hàng.
 *
 * Lifecycle:
 *   DRAFT → COMPLETED (hoàn thành nhập hàng - không thể đảo ngược)
 *   DRAFT → CANCELLED (hủy phiếu nháp)
 *
 * Nguyên tắc thiết kế:
 * - Chỉ có thể gọi completeReceipt() khi status = DRAFT.
 * - Sau khi COMPLETED, phiếu nhập là bất biến (immutable) - không thể sửa/hủy.
 *   Điều này đảm bảo tính toàn vẹn của dữ liệu tồn kho và công nợ.
 */
public enum ImportReceiptStatus {

    /**
     * Phiếu đang ở trạng thái nháp. Có thể chỉnh sửa hoặc hủy.
     * Chưa ảnh hưởng đến tồn kho và công nợ nhà cung cấp.
     */
    DRAFT,

    /**
     * Phiếu đã hoàn thành. Tồn kho đã được cộng, công nợ đã được ghi nhận.
     * Trạng thái KHÔNG THỂ đảo ngược.
     */
    COMPLETED,

    /**
     * Phiếu đã bị hủy (chỉ khi còn ở trạng thái DRAFT).
     */
    CANCELLED
}
