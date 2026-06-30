# **TÀI LIỆU KỸ THUẬT: HỆ THỐNG SINH MA TRẬN BIẾN THỂ SẢN PHẨM & XỬ LÝ TRÙNG SKU**

Tài liệu tổng hợp kiến trúc nguồn, giải pháp thuật toán tích Descartes (Colors × Sizes) và cơ chế xử lý xung đột SKU hiệu năng cao trên nền tảng Spring Boot 3.x và Hibernate 6.x.

## **1\. Tổng Quan Kiến Trúc Hệ Thống**

Hệ thống quản lý sản phẩm được thiết kế theo mô hình 3 lớp tiêu chuẩn (Controller \- Service \- Repository) kết hợp với các mẫu thiết kế tối ưu hóa DB I/O (In-Memory Lookup, Batching Execution) nhằm giải quyết bài toán bùng nổ số lượng biến thể sản phẩm trong mô hình Retail/E-commerce.

| Thành phần | Nhiệm vụ chính |
| :---- | :---- |
| VietnameseUtil | Xử lý chuẩn hóa chuỗi tiếng Việt, sinh mã SKU viết tắt tự động theo quy tắc chữ cái đầu. |
| ProductService | Thực hiện thuật toán tích Descartes, xử lý tranh chấp mã SKU trong bộ nhớ và quản lý Single Transaction. |
| ProductVariantRepository | Cung cấp cơ chế tìm kiếm tiền tố (Prefix Lookup) và cơ chế khóa bi quan (Pessimistic Write Lock) ngăn ngừa Race Condition. |

## **2\. Khối Entity & Enums (Domain Layer)**

### **ProductStatus.java**

`package com.kiotviet.enums;`

`public enum ProductStatus {`  
    `ACTIVE,`  
    `INACTIVE`  
`}`

### **Category.java**

`package com.kiotviet.entity;`

`import com.kiotviet.enums.ProductStatus;`  
`import jakarta.persistence.*;`  
`import lombok.*;`  
`import org.hibernate.annotations.SQLDelete;`  
`import org.hibernate.annotations.SQLRestriction;`

`@Entity`  
`@Table(name = "categories", indexes = {`  
        `@Index(name = "idx_category_slug", columnList = "slug")`  
`})`  
`@SQLDelete(sql = "UPDATE categories SET is_deleted = true WHERE id = ?")`  
`@SQLRestriction("is_deleted = false")`  
`@Getter`  
`@Setter`  
`@NoArgsConstructor`  
`@AllArgsConstructor`  
`@Builder`  
`public class Category extends BaseEntity {`

    `@Column(name = "name", nullable = false, length = 100)`  
    `private String name;`

    `@Column(name = "slug", nullable = false, unique = true, length = 120)`  
    `private String slug;`

    `@Column(name = "parent_id")`  
    `private Long parentId;`

    `@Enumerated(EnumType.STRING)`  
    `@Column(name = "status", nullable = false, length = 20)`  
    `@Builder.Default`  
    `private ProductStatus status = ProductStatus.ACTIVE;`  
`}`

### **Product.java**

`package com.kiotviet.entity;`

`import com.kiotviet.enums.ProductStatus;`  
`import jakarta.persistence.*;`  
`import lombok.*;`  
`import org.hibernate.annotations.SQLDelete;`  
`import org.hibernate.annotations.SQLRestriction;`  
`import java.util.ArrayList;`  
`import java.util.List;`

`@Entity`  
`@Table(name = "products", indexes = {`  
        `@Index(name = "idx_product_code", columnList = "code")`  
`})`  
`@SQLDelete(sql = "UPDATE products SET is_deleted = true WHERE id = ?")`  
`@SQLRestriction("is_deleted = false")`  
`@Getter`  
`@Setter`  
`@NoArgsConstructor`  
`@AllArgsConstructor`  
`@Builder`  
`public class Product extends BaseEntity {`

    `@Column(name = "name", nullable = false, length = 200)`  
    `private String name;`

    `@Column(name = "code", nullable = false, unique = true, length = 50)`  
    `private String code;`

    `@Column(name = "description", columnDefinition = "TEXT")`  
    `private String description;`

    `@ManyToOne(fetch = FetchType.LAZY)`  
    `@JoinColumn(name = "category_id")`  
    `private Category category;`

    `@Enumerated(EnumType.STRING)`  
    `@Column(name = "status", nullable = false, length = 20)`  
    `@Builder.Default`  
    `private ProductStatus status = ProductStatus.ACTIVE;`

    `@OneToMany(`  
            `mappedBy = "product",`  
            `cascade = CascadeType.ALL,`  
            `orphanRemoval = true,`  
            `fetch = FetchType.LAZY`  
    `)`  
    `@Builder.Default`  
    `private List variants = new ArrayList<>();`

    `public void addVariant(ProductVariant variant) {`  
        `variants.add(variant);`  
        `variant.setProduct(this);`  
    `}`  
`}`

### **ProductVariant.java**

`package com.kiotviet.entity;`

`import com.kiotviet.enums.ProductStatus;`  
`import jakarta.persistence.*;`  
`import lombok.*;`  
`import org.hibernate.annotations.SQLDelete;`  
`import org.hibernate.annotations.SQLRestriction;`  
`import java.math.BigDecimal;`

`@Entity`  
`@Table(name = "product_variants", indexes = {`  
        `@Index(name = "idx_variant_sku", columnList = "sku"),`  
        `@Index(name = "idx_variant_barcode", columnList = "barcode"),`  
        `@Index(name = "idx_variant_product_id", columnList = "product_id")`  
`})`  
`@SQLDelete(sql = "UPDATE product_variants SET is_deleted = true WHERE id = ?")`  
`@SQLRestriction("is_deleted = false")`  
`@Getter`  
`@Setter`  
`@NoArgsConstructor`  
`@AllArgsConstructor`  
`@Builder`  
`public class ProductVariant extends BaseEntity {`

    `@Column(name = "sku", nullable = false, unique = true, length = 100)`  
    `private String sku;`

    `@Column(name = "barcode", length = 50)`  
    `private String barcode;`

    `@Column(name = "color", length = 50)`  
    `private String color;`

    `@Column(name = "size", length = 20)`  
    `private String size;`

    `@Column(name = "import_price", nullable = false, precision = 15, scale = 2)`  
    `private BigDecimal importPrice;`

    `@Column(name = "sale_price", nullable = false, precision = 15, scale = 2)`  
    `private BigDecimal salePrice;`

    `@Column(name = "inventory", nullable = false)`  
    `@Builder.Default`  
    `private Integer inventory = 0;`

    `@Enumerated(EnumType.STRING)`  
    `@Column(name = "status", nullable = false, length = 20)`  
    `@Builder.Default`  
    `private ProductStatus status = ProductStatus.ACTIVE;`

    `@ManyToOne(fetch = FetchType.LAZY)`  
    `@JoinColumn(name = "product_id", nullable = false)`  
    `private Product product;`  
`}`

## **3\. Khối Data Access (Repository Layer)**

### **CategoryRepository.java**

`package com.kiotviet.repository;`

`import com.kiotviet.entity.Category;`  
`import org.springframework.data.jpa.repository.JpaRepository;`  
`import org.springframework.stereotype.Repository;`  
`import java.util.Optional;`

`@Repository`  
`public interface CategoryRepository extends JpaRepository {`  
    `Optional findBySlug(String slug);`  
    `boolean existsBySlug(String slug);`  
`}`

### **ProductRepository.java**

`package com.kiotviet.repository;`

`import com.kiotviet.entity.Product;`  
`import com.kiotviet.enums.ProductStatus;`  
`import org.springframework.data.domain.Page;`  
`import org.springframework.data.domain.Pageable;`  
`import org.springframework.data.jpa.repository.JpaRepository;`  
`import org.springframework.data.jpa.repository.Query;`  
`import org.springframework.data.repository.query.Param;`  
`import org.springframework.stereotype.Repository;`  
`import java.util.Optional;`

`@Repository`  
`public interface ProductRepository extends JpaRepository {`  
    `boolean existsByCode(String code);`  
    `Optional findByCode(String code);`

    `@Query("""`  
            `SELECT p FROM Product p`  
            `WHERE (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))`  
                   `OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')))`  
            `AND (:categoryId IS NULL OR p.category.id = :categoryId)`  
            `AND (:status IS NULL OR p.status = :status)`  
            `""")`  
    `Page searchProducts(`  
            `@Param("keyword") String keyword,`  
            `@Param("categoryId") Long categoryId,`  
            `@Param("status") ProductStatus status,`  
            `Pageable pageable`  
    `);`

    `@Query("SELECT p FROM Product p LEFT JOIN FETCH p.variants v WHERE p.id = :id AND v.isDeleted = false")`  
    `Optional findByIdWithVariants(@Param("id") Long id);`  
`}`

### **ProductVariantRepository.java**

`package com.kiotviet.repository;`

`import com.kiotviet.entity.ProductVariant;`  
`import jakarta.persistence.LockModeType;`  
`import jakarta.persistence.QueryHint;`  
`import org.springframework.data.jpa.repository.JpaRepository;`  
`import org.springframework.data.jpa.repository.Lock;`  
`import org.springframework.data.jpa.repository.Query;`  
`import org.springframework.data.jpa.repository.QueryHints;`  
`import org.springframework.data.repository.query.Param;`  
`import org.springframework.stereotype.Repository;`  
`import java.util.List;`  
`import java.util.Optional;`

`@Repository`  
`public interface ProductVariantRepository extends JpaRepository {`  
    `List findByProductId(Long productId);`  
    `boolean existsBySku(String sku);`

    `@Query("SELECT pv.sku FROM ProductVariant pv WHERE pv.sku LIKE CONCAT(:prefix, '%')")`  
    `List findSkusWithPrefix(@Param("prefix") String prefix);`

    `@Lock(LockModeType.PESSIMISTIC_WRITE)`  
    `@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))`  
    `@Query("SELECT pv FROM ProductVariant pv WHERE pv.id = :id")`  
    `Optional findByIdForUpdate(@Param("id") Long id);`  
`}`

## **4\. Khối Giao Tiếp & Dữ Liệu (DTO & Controller Layer)**

### **CreateProductMatrixRequest.java**

`package com.kiotviet.dto.request;`

`import jakarta.validation.constraints.*;`  
`import lombok.Data;`  
`import java.math.BigDecimal;`  
`import java.util.List;`

`@Data`  
`public class CreateProductMatrixRequest {`  
    `@NotBlank(message = "Tên sản phẩm không được để trống")`  
    `@Size(max = 200, message = "Tên sản phẩm tối đa 200 ký tự")`  
    `private String name;`

    `@NotBlank(message = "Mã danh mục không được để trống")`  
    `private String categoryCode;`

    `private String description;`

    `@NotEmpty(message = "Danh sách màu sắc không được rỗng")`  
    `private List<@NotBlank(message = "Tên màu không được để trống") String> colors;`

    `@NotEmpty(message = "Danh sách kích cỡ không được rỗng")`  
    `private List<@NotBlank(message = "Kích cỡ không được để trống") String> sizes;`

    `@NotNull(message = "Giá nhập không được để trống")`  
    `@Positive(message = "Giá nhập phải lớn hơn 0")`  
    `private BigDecimal baseImportPrice;`

    `@NotNull(message = "Giá bán không được để trống")`  
    `@Positive(message = "Giá bán phải lớn hơn 0")`  
    `private BigDecimal baseSalePrice;`  
`}`

### **ProductController.java**

`package com.kiotviet.controller;`

`import com.kiotviet.dto.request.CreateProductMatrixRequest;`  
`import com.kiotviet.dto.response.ApiResponse;`  
`import com.kiotviet.dto.response.ProductMatrixResponse;`  
`import com.kiotviet.enums.ProductStatus;`  
`import com.kiotviet.service.ProductService;`  
`import jakarta.validation.Valid;`  
`import lombok.RequiredArgsConstructor;`  
`import org.springframework.data.domain.Page;`  
`import org.springframework.data.domain.PageRequest;`  
`import org.springframework.data.domain.Sort;`  
`import org.springframework.http.ResponseEntity;`  
`import org.springframework.web.bind.annotation.*;`

`@RestController`  
`@RequestMapping("/api/v1/products")`  
`@RequiredArgsConstructor`  
`public class ProductController {`

    `private final ProductService productService;`

    `@PostMapping("/matrix")`  
    `public ResponseEntity> createProductMatrix(`  
            `@Valid @RequestBody CreateProductMatrixRequest request) {`  
        `ProductMatrixResponse response = productService.createProductMatrix(request);`  
        `return ResponseEntity.status(201).body(ApiResponse.created(response));`  
    `}`  
`}`

## **5\. Khối Xử Lý Nghiệp Vụ & Thuật Toán (Service & Utility)**

### **VietnameseUtil.java**

`package com.kiotviet.util;`

`import java.text.Normalizer;`  
`import java.util.Arrays;`  
`import java.util.regex.Pattern;`  
`import java.util.stream.Collectors;`

`public class VietnameseUtil {`  
    `private static final Pattern NON_ASCII = Pattern.compile("[^\\p{ASCII}]");`  
    `private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9\\s-]");`  
    `private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");`

    `private VietnameseUtil() {}`

    `public static String removeDiacritics(String input) {`  
        `if (input == null || input.isBlank()) return "";`  
        `String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);`  
        `return NON_ASCII.matcher(normalized).replaceAll("");`  
    `}`

    `public static String toSlug(String input) {`  
        `String noDiacritics = removeDiacritics(input);`  
        `String cleaned = NON_ALPHANUMERIC.matcher(noDiacritics).replaceAll("");`  
        `String trimmed = MULTIPLE_SPACES.matcher(cleaned.trim()).replaceAll(" ");`  
        `return trimmed.toLowerCase().replace(" ", "-");`  
    `}`

    `public static String generateSku(String productName, String color, String size) {`  
        `String productAbbr = getAbbreviation(productName);`  
        `String colorAbbr = getAbbreviation(color);`  
        `String sizeClean = removeDiacritics(size).replaceAll("[^a-zA-Z0-9]", "").toUpperCase();`  
        `return productAbbr + "-" + colorAbbr + "-" + sizeClean;`  
    `}`

    `private static String getAbbreviation(String text) {`  
        `if (text == null || text.isBlank()) return "";`  
        `String noDiacritics = removeDiacritics(text).trim();`  
        `return Arrays.stream(noDiacritics.split("\\s+"))`  
                `.filter(word -> !word.isEmpty())`  
                `.map(word -> String.valueOf(word.charAt(0)))`  
                `.collect(Collectors.joining())`  
                `.toUpperCase();`  
    `}`  
`}`

### **ProductService.java**

`package com.kiotviet.service;`

`import com.kiotviet.dto.request.CreateProductMatrixRequest;`  
`import com.kiotviet.dto.response.ProductMatrixResponse;`  
`import com.kiotviet.entity.Category;`  
`import com.kiotviet.entity.Product;`  
`import com.kiotviet.entity.ProductVariant;`  
`import com.kiotviet.enums.ProductStatus;`  
`import com.kiotviet.exception.BusinessException;`  
`import com.kiotviet.exception.EntityNotFoundException;`  
`import com.kiotviet.repository.CategoryRepository;`  
`import com.kiotviet.repository.ProductRepository;`  
`import com.kiotviet.repository.ProductVariantRepository;`  
`import com.kiotviet.util.VietnameseUtil;`  
`import lombok.RequiredArgsConstructor;`  
`import lombok.extern.slf4j.Slf4j;`  
`import org.springframework.stereotype.Service;`  
`import org.springframework.transaction.annotation.Transactional;`  
`import java.util.*;`  
`import java.util.stream.Collectors;`

`@Slf4j`  
`@Service`  
`@RequiredArgsConstructor`  
`public class ProductService {`

    `private final ProductRepository productRepository;`  
    `private final ProductVariantRepository variantRepository;`  
    `private final CategoryRepository categoryRepository;`

    `@Transactional`  
    `public ProductMatrixResponse createProductMatrix(CreateProductMatrixRequest request) {`  
        `if (request.getBaseSalePrice().compareTo(request.getBaseImportPrice()) < 0) {`  
            `throw new BusinessException("Giá bán không được thấp hơn giá nhập");`  
        `}`

        `Category category = categoryRepository.findBySlug(request.getCategoryCode())`  
                `.orElseThrow(() -> new EntityNotFoundException("Danh mục không tồn tại"));`

        `String productCode = VietnameseUtil.toSlug(request.getName()).toUpperCase().replace("-", "_");`  
        `if (productRepository.existsByCode(productCode)) {`  
            `throw new BusinessException("Mã sản phẩm đã tồn tại: " + productCode);`  
        `}`

        `Product product = Product.builder()`  
                `.name(request.getName())`  
                `.code(productCode)`  
                `.description(request.getDescription())`  
                `.category(category)`  
                `.status(ProductStatus.ACTIVE)`  
                `.build();`  
        `product = productRepository.save(product);`

        `String skuBase = VietnameseUtil.generateSku(request.getName(), "", "").replaceAll("-+$", "");`  
        `List existingSkus = variantRepository.findSkusWithPrefix(skuBase);`  
        `Set existingSkuSet = new HashSet<>(existingSkus);`

        `List variants = new ArrayList<>();`  
        `for (String color : request.getColors()) {`  
            `for (String size : request.getSizes()) {`  
                `String baseSku = VietnameseUtil.generateSku(request.getName(), color, size);`  
                `String finalSku = resolveSkuConflict(baseSku, existingSkuSet);`  
                `existingSkuSet.add(finalSku);`

                `ProductVariant variant = ProductVariant.builder()`  
                        `.sku(finalSku)`  
                        `.color(color)`  
                        `.size(size)`  
                        `.importPrice(request.getBaseImportPrice())`  
                        `.salePrice(request.getBaseSalePrice())`  
                        `.inventory(0)`  
                        `.status(ProductStatus.ACTIVE)`  
                        `.product(product)`  
                        `.build();`  
                `variants.add(variant);`  
            `}`  
        `}`

        `List savedVariants = variantRepository.saveAll(variants);`  
        `return toMatrixResponse(product, savedVariants);`  
    `}`

    `private String resolveSkuConflict(String baseSku, Set existingSkus) {`  
        `if (!existingSkus.contains(baseSku)) {`  
            `return baseSku;`  
        `}`  
        `int suffix = 1;`  
        `while (true) {`  
            `String candidate = baseSku + "-" + suffix;`  
            `if (!existingSkus.contains(candidate)) {`  
                `return candidate;`  
            `}`  
            `suffix++;`  
        `}`  
    `}`

    `private ProductMatrixResponse toMatrixResponse(Product product, List variants) {`  
        `// Map logic...`  
        `return null;`   
    `}`  
`}`

## **6\. Đánh Giá Kỹ Thuật & Gợi Ý Tối Ưu Từ Senior**

* **Tối ưu bộ nhớ lookup:** Thuật toán sử dụng Set In-Memory lookup đạt độ phức tạp O(1) thay vì lặp qua DB giảm tải I/O tuyệt đối từ N query xuống còn 1 query đơn lẻ bằng kỹ thuật findSkusWithPrefix.  
* **Cảnh báo Batch Insert với GenerationType.IDENTITY:** Hãy chú ý khi sử dụng thực tế MySQL \+ Hibernate IDENTITY ID, quá trình saveAll() sẽ bị tắt tính năng JDBC batch ngầm định. Cần đổi sang Sequence hoặc sử dụng JdbcTemplate batchUpdate khi ma trận thuộc tính tăng vọt lên hàng nghìn bản ghi.  
* **Tối ưu hóa kết nối URL:** Hãy kích hoạt tham số kết nối rewriteBatchedStatements=true trong MySQL JDBC Connection String để máy chủ gom cụm nhiều lệnh insert thành một gói round-trip mạng duy nhất.