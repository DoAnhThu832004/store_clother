package com.example.store_clothes.dto.response;

import com.example.store_clothes.entity.Product;
import com.example.store_clothes.entity.ProductStatus;
import com.example.store_clothes.entity.ProductVariant;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ProductMatrixResponse - DTO trả về sau khi sinh ma trận biến thể thành công.
 *
 * 💡 Senior Note — Tại sao tách ProductMatrixResponse riêng khỏi ProductResponse?
 * ProductResponse: Dùng cho flow CRUD thông thường (GET by ID, POST thủ công).
 * ProductMatrixResponse: Chuyên biệt cho kết quả tích Descartes, bổ sung thêm:
 * - totalVariants: Tổng số biến thể đã sinh (client confirm số lượng đúng).
 * - colors / sizes: Phản chiếu lại input để client dễ debug/verify.
 * Việc tách riêng tuân thủ Interface Segregation Principle (ISP) —
 * mỗi response chỉ chứa đúng những field mà consumer của nó thực sự cần.
 *
 * 💡 Senior Note — Dùng Java Record:
 * Immutable, tự sinh equals/hashCode/toString, không cần Lombok.
 * Getter dạng name() thay vì getName() — cần chú ý khi dùng với framework cũ.
 * Jackson serialize record tốt từ Spring Boot 2.7+ (dùng Jackson 2.12+).
 */
public record ProductMatrixResponse(

        /** ID của sản phẩm gốc vừa được tạo. */
        Long id,

        /** Tên sản phẩm. */
        String name,

        /**
         * Mã sản phẩm tự sinh.
         * Ví dụ: "AO_THUN_NAM_OVERSIZE" (từ toSlug("Áo Thun Nam Oversize").toUpperCase().replace("-","_")).
         */
        String code,

        /** Mô tả chi tiết sản phẩm. */
        String description,

        /** Trạng thái sản phẩm tại thời điểm tạo (luôn là ACTIVE). */
        ProductStatus status,

        /**
         * Tổng số biến thể đã được sinh ra = colors.size() × sizes.size().
         * Client dùng để verify không có biến thể nào bị mất.
         */
        int totalVariants,

        /**
         * Danh sách màu sắc gốc từ request (phản chiếu lại để client verify).
         * Ví dụ: ["Đen", "Trắng", "Đỏ"].
         */
        List<String> colors,

        /**
         * Danh sách kích cỡ gốc từ request (phản chiếu lại để client verify).
         * Ví dụ: ["S", "M", "L", "XL"].
         */
        List<String> sizes,

        /**
         * Danh sách đầy đủ tất cả biến thể đã được tạo.
         * Mỗi phần tử là VariantResponse với SKU đã được resolve conflict.
         */
        List<VariantResponse> variants,

        /** Thời điểm sản phẩm được tạo (audit field từ BaseEntity). */
        LocalDateTime createdAt

) {

    /**
     * Static factory method: Chuyển đổi từ Entity + context sang Response DTO.
     *
     * 💡 Senior Note — Tại sao nhận List<ProductVariant> riêng thay vì product.getVariants()?
     * Sau saveAll(), danh sách variant có thể chưa được Hibernate refresh vào collection
     * của product nếu chúng được build độc lập và liên kết thủ công.
     * Truyền thẳng savedVariants từ Service vào đây đảm bảo dữ liệu luôn chính xác,
     * tránh trigger thêm một LAZY load query không cần thiết.
     *
     * @param product       Entity Product đã được persist
     * @param savedVariants Danh sách variants đã được persist (từ variantRepository.saveAll())
     * @param colors        Danh sách màu gốc từ request
     * @param sizes         Danh sách size gốc từ request
     * @return ProductMatrixResponse sẵn sàng trả về Client
     */
    public static ProductMatrixResponse fromEntity(
            Product product,
            List<ProductVariant> savedVariants,
            List<String> colors,
            List<String> sizes) {

        List<VariantResponse> variantResponses = savedVariants.stream()
                .map(VariantResponse::fromEntity)
                .toList();

        return new ProductMatrixResponse(
                product.getId(),
                product.getName(),
                product.getCode(),
                product.getDescription(),
                product.getStatus(),
                savedVariants.size(),
                colors,
                sizes,
                variantResponses,
                product.getCreatedAt()
        );
    }
}
