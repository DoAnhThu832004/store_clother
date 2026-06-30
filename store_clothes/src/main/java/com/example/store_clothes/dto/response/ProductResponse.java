package com.example.store_clothes.dto.response;

import com.example.store_clothes.entity.Product;
import com.example.store_clothes.entity.ProductStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO trả về thông tin Sản phẩm cho Client.
 *
 * DESIGN DECISION - Dùng Java Record cho Response DTO:
 * - Immutable: phù hợp với dữ liệu chỉ đọc trả về.
 * - Tự sinh getter dạng name() thay vì getName().
 *
 * QUAN TRỌNG:
 * - DTO KHÔNG chứa trường isDeleted, createdAt theo mặc định.
 *   Chỉ expose những field thực sự cần thiết cho phía Client (Principle of Least Privilege).
 * - Dùng static factory method fromEntity() để tách biệt logic mapping
 *   khỏi Service. Service chỉ cần gọi ProductResponse.fromEntity(product).
 */
public record ProductResponse(
        Long id,
        String name,
        String code,
        String description,
        ProductStatus status,
        List<VariantResponse> variants,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * Static factory method: Chuyển đổi từ Entity sang DTO.
     *
     * LƯU Ý VỀ PERFORMANCE:
     * Method này gọi product.getVariants() - nếu FetchType là LAZY,
     * đây là điểm sẽ trigger thêm một query xuống DB.
     * Cần đảm bảo query lấy Product đã JOIN FETCH variants (xem ProductRepository).
     *
     * @param product Entity Product đã được load đầy đủ variants
     * @return ProductResponse DTO sẵn sàng trả về Client
     */
    public static ProductResponse fromEntity(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getCode(),
                product.getDescription(),
                product.getStatus(),
                product.getVariants().stream()
                        .map(VariantResponse::fromEntity)
                        .toList(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
