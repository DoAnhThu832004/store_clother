package com.example.store_clothes.service.impl;

import com.example.store_clothes.dto.request.CreateProductRequest;
import com.example.store_clothes.dto.request.CreateVariantRequest;
import com.example.store_clothes.dto.request.CreateProductMatrixRequest;
import com.example.store_clothes.dto.request.UpdateProductRequest;
import com.example.store_clothes.dto.request.AddVariantRequest;
import com.example.store_clothes.dto.request.UpdateVariantRequest;
import com.example.store_clothes.dto.request.StockAdjustmentRequest;
import com.example.store_clothes.dto.response.ProductMatrixResponse;
import com.example.store_clothes.dto.response.ProductResponse;
import com.example.store_clothes.dto.response.VariantResponse;
import com.example.store_clothes.entity.Product;
import com.example.store_clothes.entity.ProductStatus;
import com.example.store_clothes.entity.ProductVariant;
import com.example.store_clothes.entity.StockHistory;
import com.example.store_clothes.enums.TransactionType;
import com.example.store_clothes.exception.BusinessException;
import com.example.store_clothes.exception.EntityNotFoundException;
import com.example.store_clothes.repository.ProductRepository;
import com.example.store_clothes.repository.ProductVariantRepository;
import com.example.store_clothes.repository.StockHistoryRepository;
import com.example.store_clothes.service.AuditLogService;
import com.example.store_clothes.service.ProductService;
import com.example.store_clothes.util.VietnameseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final StockHistoryRepository stockHistoryRepository;
    private final AuditLogService auditLogService;

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

    /**
     * Lấy username của người dùng hiện tại từ Spring Security Context.
     * Dùng để ghi AuditLog với thông tin "ai đã thực hiện".
     */
    private String getCurrentUsername() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "SYSTEM";
        }
    }

    // =========================================================================
    // TICKET P-01 — Cập nhật thông tin sản phẩm gốc
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * 💡 Senior Note — Tại sao code sản phẩm không được phép thay đổi sau khi tạo?
     * 1. OrderItem Snapshot Integrity: Khi tạo đơn hàng, hệ thống lưu snapshot sản phẩm
     *    (tên, code, giá) vào OrderItem để bảo toàn dữ liệu lịch sử. Nếu code thay đổi,
     *    các báo cáo tra cứu theo code từ OrderItem cũ sẽ không tìm được sản phẩm hiện tại.
     * 2. External System Integration: Code là "Natural Key" trong hệ thống ERP, POS,
     *    kế toán bên ngoài. Đổi code = toàn bộ mapping trong các hệ thống tích hợp bị sai.
     * 3. Idempotent Retry Safety: Trong distributed system, client có thể retry request.
     *    Nếu code thay đổi được, hai lần retry có thể tạo ra hai trạng thái không nhất quán.
     * → GIẢI PHÁP: Code là trường bất biến (immutable field), chỉ set một lần khi tạo.
     */
    @Override
    @Transactional
    public ProductResponse updateProduct(Long productId, UpdateProductRequest request) {
        log.info("Bắt đầu cập nhật sản phẩm id={}", productId);

        // ── BƯỚC 1: LOAD PRODUCT ──────────────────────────────────────────────
        Product product = productRepository.findByIdWithVariants(productId)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("Không tìm thấy sản phẩm với ID: %d", productId)
                ));

        // Snapshot trước khi sửa để ghi vào AuditLog
        String beforeJson = String.format("{\"name\":\"%s\",\"description\":\"%s\"}",
                product.getName(), product.getDescription());

        // ── BƯỚC 2: CẬP NHẬT CÁC TRƯỜNG ĐƯỢC PHÉP ───────────────────────────
        // KHÔNG cập nhật product.code — đây là khóa nghiệp vụ bất biến.
        product.setName(request.name());
        product.setDescription(request.description());

        // ── BƯỚC 3: LƯU VÀ RELOAD ─────────────────────────────────────────────
        // save() trigger dirty-check và UPDATE trong transaction hiện tại.
        // Sau đó load lại bằng findByIdWithVariants để có đủ variants cho response.
        productRepository.save(product);
        log.info("Đã cập nhật sản phẩm thành công: id={}, name={}", productId, request.name());

        // ── BƯỚC 4: GHI AUDIT LOG BẤT ĐỒNG BỘ ───────────────────────────────
        // @Async + Propagation.REQUIRES_NEW: chạy trên thread riêng, transaction riêng.
        // Nếu ghi log lỗi → không rollback transaction cập nhật sản phẩm.
        String afterJson = String.format("{\"name\":\"%s\",\"description\":\"%s\"}",
                request.name(), request.description());
        String details = String.format("{\"before\":%s,\"after\":%s}", beforeJson, afterJson);
        auditLogService.log(null, getCurrentUsername(), "UPDATE_PRODUCT", "PRODUCT", productId, details);

        return ProductResponse.fromEntity(product);
    }

    // =========================================================================
    // TICKET PV-01 — Thêm biến thể đơn lẻ vào sản phẩm
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * 💡 Senior Note — Tại sao phải ghi StockHistory ngay cả khi khởi tạo kho ban đầu?
     * 1. Audit Trail Completeness: Mọi sự thay đổi tồn kho phải có bằng chứng nguồn gốc.
     *    Nếu biến thể được tạo với inventory=10 mà không có StockHistory, kiểm toán viên
     *    sẽ hỏi "10 cái này từ đâu ra?" → Không có câu trả lời → vi phạm audit.
     * 2. balanceBefore = 0: Xác nhận rõ ràng đây là lần đầu tiên hàng vào kho.
     *    referenceCode = "INIT": Phân biệt với nhập hàng thông thường.
     * 3. Consistency Check: Hệ thống báo cáo có thể tính tổng từ StockHistory records.
     *    Nếu thiếu bản ghi INIT, tổng sẽ sai. Công thức: Σ(changeQuantity) = currentInventory.
     */
    @Override
    @Transactional
    public VariantResponse addVariant(Long productId, AddVariantRequest request) {
        log.info("Bắt đầu thêm biến thể cho sản phẩm id={}", productId);

        // ── BƯỚC 1: KIỂM TRA PRODUCT TỒN TẠI VÀ ACTIVE ─────────────────────
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("Không tìm thấy sản phẩm với ID: %d", productId)
                ));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException(
                    String.format("Sản phẩm '%s' (id=%d) đang INACTIVE, không thể thêm biến thể.",
                            product.getName(), productId)
            );
        }

        // ── BƯỚC 2: XỬ LÝ SKU ────────────────────────────────────────────────
        String finalSku;
        if (request.sku() != null && !request.sku().isBlank()) {
            // SKU được truyền vào → validate trùng
            if (variantRepository.existsBySku(request.sku().toUpperCase())) {
                throw new BusinessException(
                        String.format("SKU '%s' đã tồn tại trong hệ thống.", request.sku())
                );
            }
            finalSku = request.sku().toUpperCase();
        } else {
            // SKU null → tự sinh bằng VietnameseUtil + resolveSkuConflict
            String baseSku = VietnameseUtil.generateSku(product.getName(), request.color(), request.size());
            String skuPrefix = baseSku.replaceAll("-+$", "");
            List<String> existingSkuList = variantRepository.findSkusWithPrefix(skuPrefix);
            Set<String> existingSkuSet = new HashSet<>(existingSkuList);
            finalSku = resolveSkuConflict(baseSku, existingSkuSet);
            log.debug("SKU tự sinh: baseSku={}, finalSku={}", baseSku, finalSku);
        }

        // ── BƯỚC 3: KIỂM TRA BARCODE TRÙNG ──────────────────────────────────
        if (request.barcode() != null && !request.barcode().isBlank()
                && variantRepository.existsByBarcode(request.barcode())) {
            throw new BusinessException(
                    String.format("Barcode '%s' đã tồn tại trong hệ thống.", request.barcode())
            );
        }

        // ── BƯỚC 4: TẠO VÀ LƯU VARIANT ──────────────────────────────────────
        ProductVariant variant = ProductVariant.builder()
                .sku(finalSku)
                .barcode(request.barcode())
                .color(request.color())
                .size(request.size())
                .importPrice(request.importPrice())
                .salePrice(request.salePrice())
                .inventory(request.initialInventory())
                .status(ProductStatus.ACTIVE)
                .product(product)
                .build();

        ProductVariant saved = variantRepository.save(variant);
        log.info("Đã tạo biến thể mới: id={}, sku={}, inventory={}", saved.getId(), finalSku, request.initialInventory());

        // ── BƯỚC 5: GHI STOCKHISTORY NẾU initialInventory > 0 ────────────────
        // 💡 Mọi thay đổi tồn kho — kể cả khởi tạo — đều phải có StockHistory.
        if (request.initialInventory() > 0) {
            StockHistory initHistory = StockHistory.builder()
                    .variantId(saved.getId())
                    .changeQuantity(request.initialInventory())  // delta = initialInventory - 0
                    .transactionType(TransactionType.ADJUSTMENT)
                    .referenceCode("INIT")
                    .balanceBefore(0)
                    .balanceAfter(request.initialInventory())
                    .createdAt(LocalDateTime.now())
                    .build();
            stockHistoryRepository.save(initHistory);
            log.info("Ghi StockHistory INIT cho variant id={}, qty={}", saved.getId(), request.initialInventory());
        }

        return VariantResponse.fromEntity(saved);
    }

    // =========================================================================
    // TICKET PV-02 — Cập nhật giá và thuộc tính biến thể
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * 💡 Senior Note — Optimistic Lock vs Pessimistic Lock cho update giá:
     * - UPDATE GIÁ (Optimistic): Xung đột rất hiếm (2 manager cùng đổi giá lúc 1 giây).
     *   Không lock row DB → throughput cao. Khi conflict → báo lỗi 409 → user retry.
     *   Chi phí conflict: user retry 1 lần. Chi phí lock nếu không conflict: 0.
     *
     * - UPDATE INVENTORY (Pessimistic): Xung đột thường xuyên (nhiều đơn hàng song song).
     *   Phải lock row để tránh Lost Update (kho âm). Chi phí: hàng đợi ngắn.
     *   Nếu không lock: Lost Update → kho âm → nghiêm trọng về nghiệp vụ.
     *
     * Quy tắc chọn lock: "Tần suất xung đột × Mức độ nghiêm trọng khi sai"
     * → Giá: hiếm × không nghiêm trọng = Optimistic
     * → Kho: thường × nghiêm trọng = Pessimistic
     */
    @Override
    @Transactional
    public VariantResponse updateVariant(Long variantId, UpdateVariantRequest request) {
        log.info("Cập nhật biến thể id={}", variantId);

        // ── BƯỚC 1: LOAD VARIANT (Optimistic Lock tự kích hoạt khi có @Version) ──
        // findById() load entity bình thường, @Version field cũng được load.
        // Khi transaction commit, Hibernate sẽ thực hiện:
        //   UPDATE product_variants SET ..., version = oldVersion + 1
        //   WHERE id = ? AND version = oldVersion
        // Nếu version đã thay đổi bởi thread khác → 0 rows updated → OptimisticLockException.
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("Không tìm thấy biến thể với ID: %d", variantId)
                ));

        // Snapshot trước khi sửa
        String beforeJson = String.format("{\"salePrice\":\"%s\",\"importPrice\":\"%s\",\"status\":\"%s\"}",
                variant.getSalePrice(), variant.getImportPrice(), variant.getStatus());

        // ── BƯỚC 2: CẬP NHẬT CÁC TRƯỜNG KHÔNG NULL (patch semantics) ─────────
        if (request.salePrice() != null) {
            variant.setSalePrice(request.salePrice());
        }
        if (request.importPrice() != null) {
            variant.setImportPrice(request.importPrice());
        }
        if (request.status() != null) {
            variant.setStatus(request.status());
        }

        // ── BƯỚC 3: SAVE — @Version tự động increment và validate ─────────────
        // OptimisticLockException được GlobalExceptionHandler bắt → HTTP 409.
        ProductVariant saved = variantRepository.save(variant);
        log.info("Đã cập nhật biến thể id={}", variantId);

        // ── BƯỚC 4: GHI AUDIT LOG BẤT ĐỒNG BỘ ───────────────────────────────
        String afterJson = String.format("{\"salePrice\":\"%s\",\"importPrice\":\"%s\",\"status\":\"%s\"}",
                saved.getSalePrice(), saved.getImportPrice(), saved.getStatus());
        String details = String.format("{\"before\":%s,\"after\":%s}", beforeJson, afterJson);
        auditLogService.log(null, getCurrentUsername(), "UPDATE_VARIANT_PRICE", "PRODUCT_VARIANT", variantId, details);

        return VariantResponse.fromEntity(saved);
    }

    // =========================================================================
    // TICKET PV-02b — Điều chỉnh tồn kho thủ công (Stock Adjustment)
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * 💡 Senior Note — Tại sao dùng Atomic SQL Update thay vì setter cho inventory?
     *
     * KỊCH BẢN RACE CONDITION nếu dùng setter:
     *   T1: Owner A mở trang điều chỉnh kho → thấy inventory = 50 → nhập newQty = 45
     *   T2: Owner B mở trang đồng thời → thấy inventory = 50 → nhập newQty = 40
     *   T1 commit: variant.setInventory(45) → UPDATE ... SET inventory=45 WHERE id=? AND version=0
     *   T2 commit: variant.setInventory(40) → UPDATE ... SET inventory=40 WHERE id=? AND version=0
     *   → T2 thắng (last-write-wins) → inventory = 40, KHÔNG phải 45
     *   → Stock adjustment của Owner A bị mất hoàn toàn (Lost Update)!
     *
     * VỚI PESSIMISTIC LOCK + ATOMIC SQL UPDATE:
     *   T1: SELECT FOR UPDATE → acquire row lock
     *   T2: SELECT FOR UPDATE → BLOCK và chờ (tối đa 3000ms)
     *   T1: atomicUpdateInventory(id, 45) → Commit → Release lock
     *   T2: Lock acquired → đọc inventory mới nhất = 45 → Update về 40
     *   → Cả 2 adjustments đều được áp dụng đúng thứ tự.
     *
     * 💡 Senior Note — Tại sao chỉ OWNER mới được điều chỉnh kho thủ công?
     * Stock adjustment là thao tác có thể che giấu gian lận kho (inventory manipulation).
     * Manager có thể bị áp lực từ nhân viên để "fix" số liệu kho.
     * Chỉ Owner (không bị cấp trên áp lực) mới có quyền này, và mọi thao tác đều được
     * ghi AuditLog với reason bắt buộc → Owner chịu trách nhiệm pháp lý.
     */
    @Override
    @Transactional
    public void adjustStock(Long variantId, StockAdjustmentRequest request) {
        log.info("Điều chỉnh tồn kho thủ công: variantId={}, newQty={}", variantId, request.newQuantity());

        // ── BƯỚC 1: LOAD VỚI PESSIMISTIC LOCK ────────────────────────────────
        // findByIdForUpdate(): SELECT ... FOR UPDATE → DB-level row lock.
        // Thread khác gọi findByIdForUpdate() cùng ID sẽ BLOCK đến khi transaction này commit.
        ProductVariant variant = variantRepository.findByIdForUpdate(variantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("Không tìm thấy biến thể với ID: %d", variantId)
                ));

        // ── BƯỚC 2: TÍNH DELTA ────────────────────────────────────────────────
        int oldQty = variant.getInventory();
        int newQty = request.newQuantity();
        int delta = newQty - oldQty;

        // ── BƯỚC 3: GUARD — KHÔNG CÓ THAY ĐỔI ───────────────────────────────
        if (delta == 0) {
            throw new BusinessException(
                    "Số lượng mới bằng số lượng hiện tại (" + oldQty + "), không có thay đổi."
            );
        }

        // ── BƯỚC 4: ATOMIC SQL UPDATE ─────────────────────────────────────────
        // KHÔNG dùng variant.setInventory(newQty) — xem Senior Note ở trên.
        int updatedRows = variantRepository.atomicUpdateInventory(variantId, newQty);
        if (updatedRows != 1) {
            throw new BusinessException("Lỗi hệ thống: Không thể cập nhật tồn kho cho biến thể id=" + variantId);
        }
        log.info("Đã cập nhật inventory: variantId={}, {} → {}", variantId, oldQty, newQty);

        // ── BƯỚC 5: GHI STOCKHISTORY ─────────────────────────────────────────
        // referenceCode = "ADJ-" + timestamp: unique per adjustment, traceable.
        String referenceCode = "ADJ-" + System.currentTimeMillis();
        StockHistory history = StockHistory.builder()
                .variantId(variantId)
                .changeQuantity(delta)          // delta có thể âm (giảm kho)
                .transactionType(TransactionType.ADJUSTMENT)
                .referenceCode(referenceCode)
                .balanceBefore(oldQty)
                .balanceAfter(newQty)
                .createdAt(LocalDateTime.now())
                .build();
        stockHistoryRepository.save(history);
        log.info("Đã ghi StockHistory: ref={}, delta={}, before={}, after={}", referenceCode, delta, oldQty, newQty);

        // ── BƯỚC 6: GHI AUDIT LOG BẤT ĐỒNG BỘ (@ASYNC) ──────────────────────
        // Chạy trên thread riêng với Propagation.REQUIRES_NEW.
        // Nếu ghi log lỗi → chỉ rollback transaction log, không ảnh hưởng stock adjustment.
        String details = String.format(
                "{\"variantId\":%d,\"oldQty\":%d,\"newQty\":%d,\"delta\":%d,\"reason\":\"%s\",\"referenceCode\":\"%s\"}",
                variantId, oldQty, newQty, delta, request.reason(), referenceCode
        );
        auditLogService.log(null, getCurrentUsername(), "STOCK_ADJUSTMENT", "PRODUCT_VARIANT", variantId, details);
    }

    // =========================================================================
    // TICKET PV-03 — Xóa mềm biến thể
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * 💡 Senior Note — Tại sao phải rename SKU khi soft delete?
     * Vấn đề: SKU có UNIQUE constraint. Sau khi soft delete (is_deleted=true),
     * record vẫn còn trong DB nhưng bị ẩn bởi @SQLRestriction.
     * Hệ quả: Không thể tạo biến thể mới với cùng SKU đó (UNIQUE vi phạm ở DB level),
     * mặc dù về mặt nghiệp vụ, SKU này đã "không còn dùng nữa".
     *
     * VÍ DỤ:
     * - Biến thể "ATN-D-L" bị xóa mềm → row vẫn có sku="ATN-D-L" trong DB.
     * - Nhân viên muốn tạo lại biến thể "Áo Thun Nam - Đen - L" → SKU tự sinh = "ATN-D-L".
     * - DB: INSERT INTO product_variants (sku, ...) VALUES ('ATN-D-L', ...) → DUPLICATE KEY ERROR!
     *
     * GIẢI PHÁP: @SQLDelete rename SKU = CONCAT(sku, '_deleted_', UNIX_TIMESTAMP())
     * → Sku cũ: "ATN-D-L_deleted_1719849600"
     * → UNIQUE constraint được giải phóng → SKU "ATN-D-L" có thể dùng lại.
     * → UNIX_TIMESTAMP() đảm bảo suffix unique ngay cả khi cùng SKU bị xóa nhiều lần.
     */
    @Override
    @Transactional
    public void deleteVariant(Long variantId) {
        log.info("Bắt đầu xóa mềm biến thể id={}", variantId);

        // ── BƯỚC 1: LOAD VARIANT ──────────────────────────────────────────────
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("Không tìm thấy biến thể với ID: %d", variantId)
                ));

        // ── BƯỚC 2: GUARD — KIỂM TRA INVENTORY = 0 ───────────────────────────
        // Không xóa khi còn hàng: sẽ gây lỗi không xóa được tồn kho khỏi sổ sách.
        // Yêu cầu nhân viên xuất hàng hoặc điều chỉnh về 0 trước.
        if (variant.getInventory() != null && variant.getInventory() > 0) {
            throw new BusinessException(
                    String.format("Không thể xóa biến thể '%s' khi còn %d sản phẩm trong kho. " +
                            "Vui lòng xuất kho hoặc điều chỉnh về 0 trước.",
                            variant.getSku(), variant.getInventory())
            );
        }

        // ── BƯỚC 3: GUARD — KIỂM TRA ĐANG TRONG DRAFT RECEIPT ───────────────
        if (variantRepository.existsByIdInDraftImportReceipt(variantId)) {
            throw new BusinessException(
                    String.format("Biến thể '%s' đang được sử dụng trong phiếu nhập DRAFT. " +
                            "Vui lòng hoàn thành hoặc hủy phiếu nhập trước khi xóa.",
                            variant.getSku())
            );
        }

        // ── BƯỚC 4: SOFT DELETE ───────────────────────────────────────────────
        // variantRepository.delete() → Hibernate intercept → thực thi @SQLDelete:
        // UPDATE product_variants
        //   SET is_deleted = true,
        //       sku = CONCAT(sku, '_deleted_', UNIX_TIMESTAMP())
        // WHERE id = ?
        // SKU cũ được giải phóng khỏi UNIQUE constraint → có thể tái sử dụng.
        variantRepository.delete(variant);
        log.info("Đã xóa mềm biến thể id={}, sku={} (SKU đã được rename)", variantId, variant.getSku());
    }
}
