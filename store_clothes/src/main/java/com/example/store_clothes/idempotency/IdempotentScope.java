package com.example.store_clothes.idempotency;

/**
 * IdempotentScope — Enum phân loại phạm vi áp dụng Idempotency.
 *
 * Mang tính SEMANTIC — giúp developer hiểu ngữ cảnh khi đọc code.
 * Không ảnh hưởng đến logic xử lý (TTL được khai báo riêng trong @Idempotent).
 *
 * HƯỚNG DẪN CHỌN SCOPE:
 *  - POS: Hành động tại quầy thu ngân — chống double-click (TTL 30s)
 *  - FINANCIAL: Nghiệp vụ tài chính/kho — chống retry network sâu (TTL 24h)
 */
public enum IdempotentScope {

    /**
     * Phạm vi POS (Point of Sale):
     * - Checkout hóa đơn
     * - Tạo đơn hàng
     * Khuyến nghị TTL: 30 giây
     */
    POS,

    /**
     * Phạm vi FINANCIAL (Tài chính / Kho bãi):
     * - Hủy hóa đơn (hoàn kho + hoàn tiền)
     * - Hoàn thành phiếu nhập kho
     * - Điều chỉnh tồn kho thủ công
     * Khuyến nghị TTL: 86400 giây (24 giờ)
     */
    FINANCIAL
}
