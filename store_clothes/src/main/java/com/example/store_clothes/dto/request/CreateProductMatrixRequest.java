package com.example.store_clothes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * CreateProductMatrixRequest - DTO nhận dữ liệu để sinh Ma Trận Biến Thể sản phẩm.
 *
 * 💡 Senior Note — Matrix Request vs. Manual Request:
 * Đây là DTO cho flow tự động (Matrix), khác với CreateProductRequest (flow thủ công).
 * Thay vì nhập từng SKU thủ công, client chỉ cần cung cấp danh sách màu và size.
 * Service sẽ tự động thực hiện tích Descartes (Colors × Sizes) và sinh SKU theo quy tắc.
 *
 * 💡 Senior Note — Nhất quán với codebase (Java Record):
 * Dự án đang dùng Java Record (Java 16+) cho toàn bộ Request DTO.
 * Record tự sinh: constructor, accessor (name(), colors()...), equals, hashCode, toString.
 * Accessor của Record là name() không phải getName() — lưu ý khi gọi từ Service.
 * Immutable theo mặc định — phù hợp với Request DTO (không bao giờ cần mutate).
 *
 * Business constraints:
 * - Phải có ít nhất 1 màu và 1 size → sinh ra ít nhất 1 biến thể.
 * - baseSalePrice và baseImportPrice được áp dụng đồng nhất cho tất cả variants.
 * - Validation giá bán >= giá nhập được thực hiện ở tầng Service (cross-field validation).
 */
public record CreateProductMatrixRequest(

        /**
         * Tên sản phẩm. Sẽ được dùng để tự động sinh:
         * - product.code = toSlug(name).toUpperCase().replace("-", "_")
         * - prefix SKU = getAbbreviation(name) (VietnameseUtil)
         */
        @NotBlank(message = "Tên sản phẩm không được để trống")
        @Size(max = 200, message = "Tên sản phẩm tối đa 200 ký tự")
        String name,

        /** Mô tả chi tiết sản phẩm (không bắt buộc). */
        String description,

        /**
         * Danh sách màu sắc cho ma trận biến thể.
         * Ví dụ: ["Đen", "Trắng", "Đỏ"]
         *
         * 💡 Senior Note — Validation lồng nhau trên generic type:
         * @NotEmpty đảm bảo list không rỗng và không null.
         * @NotBlank trên generic type parameter đảm bảo mỗi phần tử không rỗng.
         * Spring validation tự động unwrap và validate từng String trong list.
         */
        @NotEmpty(message = "Danh sách màu sắc không được rỗng")
        List<@NotBlank(message = "Tên màu không được để trống") String> colors,

        /**
         * Danh sách kích cỡ cho ma trận biến thể.
         * Ví dụ: ["S", "M", "L", "XL"] hoặc ["30", "31", "32"]
         */
        @NotEmpty(message = "Danh sách kích cỡ không được rỗng")
        List<@NotBlank(message = "Kích cỡ không được để trống") String> sizes,

        /**
         * Giá nhập cơ sở — áp dụng đồng nhất cho tất cả biến thể trong ma trận.
         * Nếu cần giá nhập khác nhau cho từng biến thể, dùng flow CreateProductRequest thủ công.
         */
        @NotNull(message = "Giá nhập không được để trống")
        @Positive(message = "Giá nhập phải lớn hơn 0")
        BigDecimal baseImportPrice,

        /**
         * Giá bán cơ sở — áp dụng đồng nhất cho tất cả biến thể trong ma trận.
         * Service sẽ validate: baseSalePrice >= baseImportPrice.
         */
        @NotNull(message = "Giá bán không được để trống")
        @Positive(message = "Giá bán phải lớn hơn 0")
        BigDecimal baseSalePrice

) {}
