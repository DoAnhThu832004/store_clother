package com.example.store_clothes.dto.response;

import com.example.store_clothes.entity.ProductVariant;
import com.example.store_clothes.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO trả về thông tin Biến thể sản phẩm.
 *
 * Không expose product_id trong response khi trả lồng trong ProductResponse
 * vì thông tin product đã được bao bởi ProductResponse cha.
 * Tuy nhiên vẫn giữ productId để có thể dùng response này độc lập
 * khi search theo SKU/Barcode (kết quả cần biết thuộc sản phẩm nào).
 */
public record VariantResponse(
        Long id,
        String sku,
        String barcode,
        String color,
        String size,
        BigDecimal importPrice,
        BigDecimal salePrice,
        Integer inventory,
        ProductStatus status,
        Long productId,
        String productName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * Static factory method: Chuyển đổi từ Entity sang DTO.
     *
     * Lưu ý: variant.getProduct() có thể trigger LAZY load.
     * Cần đảm bảo query đã JOIN FETCH product khi cần productId/productName.
     *
     * @param variant Entity ProductVariant đã được load
     * @return VariantResponse DTO
     */
    public static VariantResponse fromEntity(ProductVariant variant) {
        return new VariantResponse(
                variant.getId(),
                variant.getSku(),
                variant.getBarcode(),
                variant.getColor(),
                variant.getSize(),
                variant.getImportPrice(),
                variant.getSalePrice(),
                variant.getInventory(),
                variant.getStatus(),
                // Truy cập product qua LAZY proxy - an toàn vì đang trong session
                variant.getProduct() != null ? variant.getProduct().getId() : null,
                variant.getProduct() != null ? variant.getProduct().getName() : null,
                variant.getCreatedAt(),
                variant.getUpdatedAt()
        );
    }
}
