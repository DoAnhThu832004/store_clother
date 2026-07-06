package com.example.store_clothes.dto.response;

import com.example.store_clothes.entity.Supplier;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO trả về thông tin Nhà Cung Cấp.
 * Dùng cho SUP-01, SUP-02, SUP-03, SUP-04.
 *
 * recentImportReceipts: Chỉ có trong response chi tiết (SUP-03),
 * null trong danh sách (SUP-02) để tránh over-fetching.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SupplierResponse {

    private Long id;
    private String name;
    private String phone;
    private String email;
    private String address;
    private String taxCode;
    private BigDecimal debtAmount;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Danh sách 5 phiếu nhập gần nhất.
     * Chỉ có trong SUP-03 (chi tiết), null trong SUP-02 (danh sách).
     */
    private List<ImportReceiptSummary> recentImportReceipts;

    /**
     * Factory method: Map từ Supplier Entity sang DTO (không kèm importReceipts).
     * Dùng cho SUP-01 (tạo mới), SUP-02 (danh sách), SUP-04 (cập nhật).
     */
    public static SupplierResponse from(Supplier supplier) {
        return SupplierResponse.builder()
                .id(supplier.getId())
                .name(supplier.getName())
                .phone(supplier.getPhone())
                .email(supplier.getEmail())
                .address(supplier.getAddress())
                .taxCode(supplier.getTaxCode())
                .debtAmount(supplier.getDebtAmount())
                .version(supplier.getVersion())
                .createdAt(supplier.getCreatedAt())
                .updatedAt(supplier.getUpdatedAt())
                .build();
    }

    /**
     * Factory method: Map từ Supplier Entity kèm danh sách phiếu nhập gần đây.
     * Dùng cho SUP-03 (chi tiết).
     */
    public static SupplierResponse from(Supplier supplier, List<ImportReceiptSummary> recentReceipts) {
        return SupplierResponse.builder()
                .id(supplier.getId())
                .name(supplier.getName())
                .phone(supplier.getPhone())
                .email(supplier.getEmail())
                .address(supplier.getAddress())
                .taxCode(supplier.getTaxCode())
                .debtAmount(supplier.getDebtAmount())
                .version(supplier.getVersion())
                .createdAt(supplier.getCreatedAt())
                .updatedAt(supplier.getUpdatedAt())
                .recentImportReceipts(recentReceipts)
                .build();
    }

    // =========================================================================
    // INNER CLASS: ImportReceiptSummary — Tóm tắt phiếu nhập cho NCC
    // =========================================================================

    /**
     * Tóm tắt thông tin phiếu nhập — dùng trong response chi tiết NCC (SUP-03).
     * Không trả về toàn bộ ImportReceiptResponse để tránh over-fetching.
     */
    @Getter
    @Builder
    public static class ImportReceiptSummary {
        private Long id;
        private String receiptCode;
        private BigDecimal totalAmount;
        private BigDecimal paidAmount;
        private String status;
        private LocalDateTime createdAt;
    }
}
