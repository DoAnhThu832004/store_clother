package com.example.store_clothes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * DTO nhận dữ liệu khi điều chỉnh tồn kho thủ công (TICKET PV-02b).
 *
 * 💡 Senior Note — Tại sao "newQuantity" thay vì "delta"?
 * Giao diện dạng "đặt về số mới" (absolute) an toàn hơn "thay đổi delta" (relative):
 *   - Absolute: Nhân viên nhập 50 → hệ thống tính delta = 50 - currentInventory.
 *     Nếu gửi request trùng → delta = 0 → bị chặn (no-op protection).
 *   - Relative: Nhân viên nhập +5 → nếu gửi trùng request (mạng chập) → trừ thêm 5.
 *     Race condition, over-adjustment, rất khó debug.
 * POS và WMS chuyên nghiệp (KiotViet, MISA) đều dùng absolute quantity cho stock take.
 *
 * 💡 Senior Note — Tại sao "reason" bắt buộc?
 * Điều chỉnh kho thủ công là thao tác nhạy cảm nhất trong nghiệp vụ kho:
 *   - Ai làm? (AuditLog ghi username)
 *   - Khi nào? (AuditLog ghi timestamp)
 *   - Tại sao? → PHẢI có "reason" để audit.
 * Nếu không có reason, kiểm toán viên không thể phân biệt "kiểm kê định kỳ"
 * vs "điều chỉnh bù lỗi" vs "gian lận kho".
 */
public record StockAdjustmentRequest(

        @NotNull(message = "Số lượng mới không được để trống")
        @PositiveOrZero(message = "Số lượng mới phải >= 0")
        Integer newQuantity,

        @NotBlank(message = "Lý do điều chỉnh không được để trống")
        String reason

) {}
