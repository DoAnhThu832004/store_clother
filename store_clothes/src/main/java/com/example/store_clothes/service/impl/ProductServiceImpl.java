package com.example.store_clothes.service.impl;

import com.example.store_clothes.dto.request.CreateProductRequest;
import com.example.store_clothes.dto.request.CreateVariantRequest;
import com.example.store_clothes.dto.request.CreateProductMatrixRequest;
import com.example.store_clothes.dto.response.ProductMatrixResponse;
import com.example.store_clothes.dto.response.ProductResponse;
import com.example.store_clothes.dto.response.VariantResponse;
import com.example.store_clothes.entity.Product;
import com.example.store_clothes.entity.ProductStatus;
import com.example.store_clothes.entity.ProductVariant;
import com.example.store_clothes.exception.BusinessException;
import com.example.store_clothes.exception.EntityNotFoundException;
import com.example.store_clothes.repository.ProductRepository;
import com.example.store_clothes.repository.ProductVariantRepository;
import com.example.store_clothes.service.ProductService;
import com.example.store_clothes.util.VietnameseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ProductServiceImpl - Triển khai đầy đủ các nghiệp vụ quản lý hàng hóa.
 *
 * ANNOTATIONS GIẢI THÍCH:
 *
 * @Slf4j: Inject logger SLF4J. Dùng log.info/warn/error để ghi nhật ký
 *         thay vì System.out.println (production-ready logging).
 *
 * @Service: Đánh dấu class là Spring Bean thuộc tầng Service.
 *            Spring sẽ tạo và quản lý lifecycle của object này.
 *
 * @RequiredArgsConstructor: Lombok tự sinh constructor với tất cả field final.
 *                            Thay thế @Autowired trên field (anti-pattern trong Spring).
 *                            Kết hợp với Spring DI để inject dependencies qua constructor.
 *
 * @Transactional(readOnly = true): Áp dụng cho toàn bộ class (mặc định cho các method đọc).
 *                                   readOnly=true: Hibernate không track dirty check,
 *                                   tối ưu hiệu năng cho các operation chỉ đọc.
 *                                   Các method ghi sẽ override bằng @Transactional riêng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    // =========================================================================
    // CREATE - Thêm mới sản phẩm
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * LUỒNG XỬ LÝ:
     * 1. Validate business rules (code trùng, SKU/Barcode trùng).
     * 2. Build Product entity từ Request DTO.
     * 3. Build ProductVariant entities từ Request, liên kết với Product.
     * 4. Save Product (cascade tự động save cả Variants).
     * 5. Load lại từ DB với JOIN FETCH để có đủ data cho response.
     * 6. Convert sang DTO và trả về.
     *
     * @Transactional (write): Override @Transactional(readOnly=true) ở class level.
     *                          Bắt buộc cho các operation INSERT/UPDATE/DELETE.
     */
    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        log.info("Bắt đầu tạo sản phẩm mới với mã: {}", request.code());

        // ── BƯỚC 1: VALIDATE UNIQUENESS ──────────────────────────────────────
        // Kiểm tra mã sản phẩm trùng lặp
        if (productRepository.existsByCode(request.code())) {
            throw new BusinessException(
                    String.format("Mã sản phẩm '%s' đã tồn tại trong hệ thống", request.code())
            );
        }

        // Kiểm tra từng SKU và Barcode trong danh sách variants
        validateVariantUniqueness(request.variants());

        // ── BƯỚC 2: BUILD PRODUCT ENTITY ─────────────────────────────────────
        Product product = Product.builder()
                .name(request.name())
                .code(request.code().toUpperCase()) // Chuẩn hóa: luôn lưu uppercase
                .description(request.description())
                .status(parseProductStatus(request.status()))
                .build();

        // ── BƯỚC 3: BUILD VÀ LIÊN KẾT VARIANTS ──────────────────────────────
        // Dùng helper method addVariant() thay vì trực tiếp thao tác list.
        // Đảm bảo bidirectional sync: variant.product được gán đúng.
        for (CreateVariantRequest variantRequest : request.variants()) {
            ProductVariant variant = buildVariantEntity(variantRequest);
            product.addVariant(variant); // Đồng thời set variant.product = product
        }

        // ── BƯỚC 4: SAVE ──────────────────────────────────────────────────────
        // CascadeType.ALL trên Product.variants → Hibernate tự động INSERT tất cả variants.
        // Hibernate cũng đủ thông minh để tận dụng batch insert (batch_size=50 trong config).
        Product savedProduct = productRepository.save(product);
        log.info("Đã tạo sản phẩm thành công: id={}, code={}", savedProduct.getId(), savedProduct.getCode());

        // ── BƯỚC 5: LOAD LẠI VỚI JOIN FETCH ──────────────────────────────────
        // Sau khi save, cần load lại để có đủ variants đã được persist.
        // findByIdWithVariants dùng JOIN FETCH → 1 query thay vì N+1.
        Product productWithVariants = productRepository.findByIdWithVariants(savedProduct.getId())
                .orElseThrow(() -> new EntityNotFoundException("Lỗi hệ thống: Không tìm thấy sản phẩm sau khi lưu"));

        // ── BƯỚC 6: CONVERT VÀ TRẢ VỀ DTO ───────────────────────────────────
        return ProductResponse.fromEntity(productWithVariants);
    }

    // =========================================================================
    // READ - Tìm kiếm
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * Sử dụng index idx_variant_sku → query cực nhanh.
     * JOIN FETCH product để có thể access productId/productName trong response.
     */
    @Override
    public VariantResponse findBySku(String sku) {
        log.debug("Tìm kiếm biến thể theo SKU: {}", sku);

        // findBySkuWithProduct: JPQL JOIN FETCH để load Product trong cùng 1 query,
        // tránh LazyInitializationException khi VariantResponse.fromEntity() truy cập variant.getProduct()
        ProductVariant variant = variantRepository.findBySkuWithProduct(sku)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("Không tìm thấy biến thể với SKU: '%s'", sku)
                ));

        log.debug("Tìm thấy biến thể: sku={}, productId={}", sku, variant.getProduct().getId());
        return VariantResponse.fromEntity(variant);
    }

    /**
     * {@inheritDoc}
     *
     * Sử dụng index idx_variant_barcode → query cực nhanh.
     * Thường được gọi khi nhân viên quét mã vạch tại quầy bán hàng.
     */
    @Override
    public VariantResponse findByBarcode(String barcode) {
        log.debug("Tìm kiếm biến thể theo Barcode: {}", barcode);

        ProductVariant variant = variantRepository.findByBarcodeWithProduct(barcode)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("Không tìm thấy biến thể với Barcode: '%s'", barcode)
                ));

        log.debug("Tìm thấy biến thể: barcode={}, productId={}", barcode, variant.getProduct().getId());
        return VariantResponse.fromEntity(variant);
    }

    /**
     * {@inheritDoc}
     *
     * Dùng JOIN FETCH để load product + variants trong 1 query.
     */
    @Override
    public ProductResponse getProductById(Long productId) {
        log.debug("Lấy chi tiết sản phẩm: id={}", productId);

        Product product = productRepository.findByIdWithVariants(productId)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("Không tìm thấy sản phẩm với ID: %d", productId)
                ));

        return ProductResponse.fromEntity(product);
    }

    // =========================================================================
    // MATRIX VARIANT SYSTEM — Tích Descartes Colors × Sizes
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * 💡 Senior Note — 8 Bước Triển Khai Chi Tiết:
     *
     * B1. Validate cross-field: salePrice >= importPrice.
     *     (Bean Validation không hỗ trợ so sánh 2 field, phải validate thủ công ở Service.)
     *
     * B2. Sinh productCode từ tên sản phẩm:
     *     VietnameseUtil.toSlug(name) → "ao-thun-nam" → .toUpperCase().replace("-","_") → "AO_THUN_NAM".
     *
     * B3. Kiểm tra mã sản phẩm trùng lặp — nhất thiết phải check trước khi save.
     *
     * B4. Build và save Product entity. Đây là thao tác sớm trước khi xử lý variants
     *     vì variants cần product_id để set FK (do GenerationType.IDENTITY, ID chỉ có sau INSERT).
     *
     * B5. Tính skuBase (prefix cho toàn bộ variants của sản phẩm này):
     *     generateSku(name, "", "") → lấy phần prefix, xóa dấu gạch cuối.
     *     Gọi findSkusWithPrefix(skuBase) 1 lần → đổ vào HashSet<String> (In-Memory O(1)).
     *
     * B6. Vòng lặp Cartesian Product (Colors × Sizes):
     *     Với mỗi cặp (color, size), sinh baseSku → resolveSkuConflict() → thêm vào Set ngay.
     *     Thao tác chỉ trong RAM, 0 query xuống DB trong vòng lặp.
     *
     * B7. Bulk save toàn bộ variants bằng variantRepository.saveAll().
     *
     *     💡 Senior Note — Cảnh báo Batch Insert với GenerationType.IDENTITY:
     *     Với MySQL + IDENTITY strategy, Hibernate tự động TẪT batch insert.
     *     Lý do: IDENTITY yêu cầu DB cấp ID sau mỗi INSERT → Hibernate không thể gom
     *     nhiều INSERT thành 1 batch mà không biết ID trước.
     *     → saveAll(N variants) = N câu INSERT riẻng lẻng, hiệu năng giảm khi matrix lớn.
     *     GIẢI PHÁP: Bật rewriteBatchedStatements=true trong JDBC URL (gom ở level network)
     *     hoặc đổi sang GenerationType.SEQUENCE cho batch thực sự ở Hibernate level.
     *
     * B8. Map sang ProductMatrixResponse và trả về.
     */
    @Override
    @Transactional
    public ProductMatrixResponse createProductMatrix(CreateProductMatrixRequest request) {
        log.info("Bắt đầu sinh ma trận biến thể: name={}, colors={}, sizes={}",
                request.name(), request.colors().size(), request.sizes().size());

        // ── BƯỜC 1: BUSINESS VALIDATION ─────────────────────────────────────────────
        // Cross-field validation: Bean Validation (@Positive) chỉ kiểm tra từng field riêng lẻ.
        // So sánh 2 field với nhau phải thực hiện thủ công tại Service.
        if (request.baseSalePrice().compareTo(request.baseImportPrice()) < 0) {
            throw new BusinessException(
                    "Giá bán (" + request.baseSalePrice() + ") không được thấp hơn giá nhập ("
                    + request.baseImportPrice() + ")");
        }

        // ── BƯỜC 2: SINH MÃ SẢN PHẨM ────────────────────────────────────────────
        // Ví dụ: "Áo Thún Nam Oversize" → "ao-thun-nam-oversize" → "AO_THUN_NAM_OVERSIZE"
        String productCode = VietnameseUtil.toSlug(request.name())
                .toUpperCase()
                .replace("-", "_");
        log.debug("Mã sản phẩm tự sinh: {}", productCode);

        // ── BƯỜC 3: KIỂM TRA TRÙNG MÃ ──────────────────────────────────────────
        if (productRepository.existsByCode(productCode)) {
            throw new BusinessException(
                    "Mã sản phẩm '" + productCode + "' đã tồn tại trong hệ thống. "
                    + "Hãy đặt tên sản phẩm khác để tạo mã duy nhất."
            );
        }

        // ── BƯỜC 4: BUILD VÀ SAVE PRODUCT ───────────────────────────────────────
        // Save Product trước để có ID (IDENTITY) làm FK cho tất cả variants.
        Product product = Product.builder()
                .name(request.name())
                .code(productCode)
                .description(request.description())
                .status(ProductStatus.ACTIVE)
                .build();
        product = productRepository.save(product);
        final Product savedProduct = product; // effectively final để dùng trong lambda nếu cần
        log.info("Đã tạo sản phẩm gốc: id={}, code={}", product.getId(), product.getCode());

        // ── BƯỜC 5: NẠP SKU PREFIX VÀO IN-MEMORY SET ───────────────────────────
        // generateSku với color="" và size="" → "ATN--" → xóa dấu gạch cuối → "ATN"
        String skuBase = VietnameseUtil.generateSku(request.name(), "", "")
                .replaceAll("-+$", ""); // xóa mọi dấu "-" thừa ở cuối chuỗi
        log.debug("SKU prefix cho ma trận: {}", skuBase);

        // 1 query duy nhất xuống DB → đổ vào HashSet cho O(1) lookup
        List<String> existingSkuList = variantRepository.findSkusWithPrefix(skuBase);
        Set<String> existingSkuSet = new HashSet<>(existingSkuList);
        log.debug("Tìm thấy {} SKU đã tồn tại với prefix '{}'", existingSkuList.size(), skuBase);

        // ── BƯỜC 6: CARTESIAN PRODUCT LOOP (Colors × Sizes) ─────────────────────
        // Toàn bộ xử lý trong RAM — không có query xuống DB trong vòng lặp này.
        List<ProductVariant> variants = new ArrayList<>();
        for (String color : request.colors()) {
            for (String size : request.sizes()) {
                // Sinh base SKU cho cặp (color, size) này
                String baseSku = VietnameseUtil.generateSku(request.name(), color, size);

                // Resolve xung đột hoàn toàn In-Memory — O(1) per lookup
                String finalSku = resolveSkuConflict(baseSku, existingSkuSet);

                // Thêm SKU mới vào Set ngay lập tức để các vòng sau có thể detect trùng
                existingSkuSet.add(finalSku);

                ProductVariant variant = ProductVariant.builder()
                        .sku(finalSku)
                        .color(color)
                        .size(size)
                        .importPrice(request.baseImportPrice())
                        .salePrice(request.baseSalePrice())
                        .inventory(0)
                        .status(ProductStatus.ACTIVE)
                        .product(savedProduct) // gọn FK trực tiếp, không qua addVariant()
                        .build();
                variants.add(variant);

                log.debug("Sinh biến thể: color={}, size={}, sku={}", color, size, finalSku);
            }
        }

        // ── BƯỜC 7: BULK SAVE ───────────────────────────────────────────────────
        // ⚠️ SENIOR WARNING — GenerationType.IDENTITY tắt Hibernate batch:
        // saveAll() dưới MySQL + IDENTITY = N câu INSERT riêng lẻ, không gom batch.
        // GIẢI PHÁP nếu matrix lớn (>100 variants):
        //   1. Thêm "rewriteBatchedStatements=true" vào JDBC URL (gom ở tầng network).
        //   2. Chuyển sang GenerationType.SEQUENCE để Hibernate thực sự batch ở JPA level.
        List<ProductVariant> savedVariants = variantRepository.saveAll(variants);
        log.info("Đã lưu {} biến thể cho sản phẩm id={}", savedVariants.size(), savedProduct.getId());

        // ── BƯỜC 8: MAP SANG RESPONSE VÀ TRẢ VỀ ──────────────────────────────────
        return ProductMatrixResponse.fromEntity(
                savedProduct,
                savedVariants,
                request.colors(),
                request.sizes()
        );
    }

    // =========================================================================
    // DELETE - Xóa mềm (Soft Delete với Cascade)
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * CASCADE SOFT DELETE STRATEGY:
     *
     * Tại sao không dùng CascadeType.REMOVE (delete vật lý)?
     * → Mất dữ liệu lịch sử, không thể khôi phục, vi phạm yêu cầu nghiệp vụ.
     *
     * Tại sao @SQLDelete trên entity không tự cascade?
     * → @SQLDelete chỉ intercept DELETE statement của entity đơn lẻ (DELETE WHERE id=?).
     *   Nó không tự động trigger soft delete cho các entity con.
     *   Kể cả CascadeType.REMOVE có được cấu hình, nó vẫn chỉ gọi @SQLDelete
     *   cho từng variant một (N queries) thay vì bulk update.
     *
     * GIẢI PHÁP TỐI ƯU:
     * 1. Bulk soft delete variants: 1 native SQL UPDATE WHERE product_id = ?
     *    → O(1) query bất kể số lượng variants.
     * 2. Soft delete product: productRepository.deleteById() → trigger @SQLDelete
     *    → Hibernate convert thành UPDATE products SET is_deleted=true WHERE id=?
     *
     * Tổng cộng: 2 queries cho mọi trường hợp, hiệu năng tối ưu.
     *
     * @Transactional: Đảm bảo atomicity.
     *                  Nếu bước 2 lỗi, bước 1 sẽ rollback → dữ liệu không bị inconsistent.
     */
    @Override
    @Transactional
    public void deleteProduct(Long productId) {
        log.info("Bắt đầu xóa mềm sản phẩm: id={}", productId);

        // ── BƯỚC 1: KIỂM TRA SẢN PHẨM TỒN TẠI ──────────────────────────────
        // Không cần load variants ở đây (chỉ cần biết product tồn tại).
        // Không dùng findByIdWithVariants để tránh load không cần thiết.
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("Không tìm thấy sản phẩm với ID: %d", productId)
                ));

        // ── BƯỚC 2: CASCADE SOFT DELETE - XÓA VARIANTS CON ──────────────────
        // Native SQL bulk UPDATE → Tuyệt đối không dùng deleteByProductId()
        // (xem giải thích chi tiết trong ProductVariantRepository)
        int deletedVariantsCount = variantRepository.softDeleteAllByProductId(productId);
        log.info("Đã xóa mềm {} biến thể của sản phẩm id={}", deletedVariantsCount, productId);

        // ── BƯỚC 3: XÓA MỀM SẢN PHẨM CHA ────────────────────────────────────
        // productRepository.delete(product) → Hibernate gọi @SQLDelete:
        // "UPDATE products SET is_deleted = true WHERE id = ?"
        // Không phải DELETE vật lý!
        productRepository.delete(product);

        log.info("Đã xóa mềm thành công sản phẩm: id={}, code={}", productId, product.getCode());
    }

    // =========================================================================
    // PRIVATE HELPER METHODS
    // =========================================================================

    /**
     * Validate tính duy nhất của SKU và Barcode trước khi tạo.
     *
     * Thực hiện validate tất cả trong 1 vòng lặp để phát hiện hết lỗi cùng lúc.
     * (Không dừng ở lỗi đầu tiên, trả về lỗi của phần tử đầu tiên vi phạm.)
     *
     * @param variants Danh sách CreateVariantRequest cần validate
     * @throws BusinessException nếu bất kỳ SKU hoặc Barcode nào đã tồn tại
     */
    private void validateVariantUniqueness(List<CreateVariantRequest> variants) {
        for (CreateVariantRequest variantReq : variants) {
            // Kiểm tra SKU trùng
            if (variantRepository.existsBySku(variantReq.sku())) {
                throw new BusinessException(
                        String.format("SKU '%s' đã tồn tại trong hệ thống", variantReq.sku())
                );
            }
            // Kiểm tra Barcode trùng (chỉ khi barcode được cung cấp)
            if (variantReq.barcode() != null && !variantReq.barcode().isBlank()
                    && variantRepository.existsByBarcode(variantReq.barcode())) {
                throw new BusinessException(
                        String.format("Barcode '%s' đã tồn tại trong hệ thống", variantReq.barcode())
                );
            }
        }
    }

    /**
     * Build ProductVariant entity từ DTO request.
     *
     * Tách thành private method để giữ createProduct() gọn gàng (Single Responsibility).
     * Inventory mặc định = 0 nếu request không cung cấp (null-safe).
     *
     * @param request DTO chứa dữ liệu biến thể
     * @return ProductVariant entity (chưa có product reference, sẽ được set qua addVariant())
     */
    private ProductVariant buildVariantEntity(CreateVariantRequest request) {
        return ProductVariant.builder()
                .sku(request.sku().toUpperCase())            // Chuẩn hóa SKU uppercase
                .barcode(request.barcode())
                .color(request.color())
                .size(request.size())
                .importPrice(request.importPrice())
                .salePrice(request.salePrice())
                // Nếu client không gửi inventory, mặc định = 0
                .inventory(request.inventory() != null ? request.inventory() : 0)
                .status(parseProductStatus(request.status()))
                .build();
    }

    /**
     * Parse String status sang ProductStatus enum.
     *
     * Xử lý an toàn khi client gửi status không hợp lệ (ví dụ: "active" thay vì "ACTIVE").
     * Ném BusinessException thay để GlobalExceptionHandler xử lý → HTTP 400.
     *
     * @param status String status từ request
     * @return ProductStatus enum value
     * @throws BusinessException nếu status không hợp lệ
     */
    private ProductStatus parseProductStatus(String status) {
        try {
            return ProductStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    String.format("Trạng thái '%s' không hợp lệ. Chỉ chấp nhận: ACTIVE, INACTIVE", status)
            );
        }
    }

    /**
     * Giải quyết xung đột SKU bằng cách gắn suffix số tăng dần (-1, -2, -3...).
     *
     * 💡 Senior Note — Thuật toán O(1) per lookup:
     * Tất cả lookup đều thực hiện trên HashSet existingSkus → O(1) mỗi lần check.
     * Không có bất kỳ query nào xuống DB trong method này.
     *
     * Ví dụ với baseSku = "ATN-D-L":
     * - existingSkus không chứa "ATN-D-L"     → trả về "ATN-D-L" ngay.
     * - existingSkus chứa "ATN-D-L"           → thử "ATN-D-L-1".
     * - existingSkus chứa "ATN-D-L-1"         → thử "ATN-D-L-2".
     * - existingSkus không chứa "ATN-D-L-2"   → trả về "ATN-D-L-2".
     *
     * 💡 Senior Note — Tại sao while(true) an toàn ở đây?
     * Vòng lặp BẮT BUỘC phải kết thúc vì suffix tăng vô hạn (1, 2, 3...) trong khi
     * số lượng SKU trong Set là hữu hạn. Không có nguy cơ infinite loop thực sự.
     *
     * @param baseSku      SKU gốc được sinh bởi VietnameseUtil.generateSku()
     * @param existingSkus Set chứa tất cả SKU đã tồn tại (cả trong DB lẫn vừa sinh trong vòng lặp)
     * @return SKU cuối cùng không trùng với bất kỳ SKU nào trong existingSkus
     */
    private String resolveSkuConflict(String baseSku, Set<String> existingSkus) {
        if (!existingSkus.contains(baseSku)) {
            return baseSku; // Happy path — không cần suffix, O(1) return
        }
        int suffix = 1;
        while (true) {
            String candidate = baseSku + "-" + suffix;
            if (!existingSkus.contains(candidate)) {
                return candidate;
            }
            suffix++;
        }
    }
}

