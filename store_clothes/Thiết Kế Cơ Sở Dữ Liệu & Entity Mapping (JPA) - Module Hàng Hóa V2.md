# **TÀI LIỆU TOÀN DIỆN: THIẾT KẾ CƠ SỞ DỮ LIỆU, ENTITY MAPPING (JPA) VÀ CẤU HÌNH HỆ THỐNG KIOTVIET FASHION (V2)**

Tài liệu này tổng hợp toàn bộ nhận xét kiến trúc từ Senior Developer, mã nguồn triển khai tối ưu cho các Entity mới (Product, ProductVariant), cùng toàn bộ các file mã nguồn nền tảng, cấu hình, bảo mật và xử lý ngoại lệ mà bạn đã cung cấp cho dự án KiotViet Fashion POS.

## **1\. Nhận xét Kiến trúc & Lưu ý Kỹ thuật Cốt lõi (Senior Notes)**

Để đảm bảo hệ thống vận hành ổn định, dễ mở rộng và tối ưu hiệu năng khi scale-up, đội ngũ phát triển cần tuân thủ nghiêm ngặt các nguyên tắc kiến trúc sau:

* **Tuyệt đối KHÔNG dùng @Data của Lombok cho JPA Entity:** Annotation này tự động sinh các hàm toString(), equals(), và hashCode(). Đối với các quan hệ song phương (Bidirectional) như One-to-Many và Many-to-One, hàm toString() của bảng này sẽ gọi bảng kia, tạo thành một vòng lặp vô hạn dẫn đến lỗi StackOverflowError nghiêm trọng.  
* **Thay thế @Where do đã bị Deprecated trong Hibernate 6:** Kể từ Hibernate 6.x (đi kèm Spring Boot 3), annotation @Where(clause \= "...") đã bị đánh dấu lỗi thời vì nó truyền câu lệnh SQL thuần túy, dễ gây lỗi khi thay đổi database dialect. Hãy chuyển sang sử dụng @SQLRestriction("is\_deleted \= false").  
* **Xử lý Cascade Soft Delete trên quan hệ Cha \- Con:** Khi sử dụng @SQLDelete để cập nhật trạng thái xóa mềm cho bản ghi cha (Product), các bản ghi con (ProductVariant) trong DB vẫn giữ nguyên trạng thái chưa xóa. Do đó, tầng Service cần chủ động cập nhật trạng thái cho các bản ghi con để bảo toàn tính toàn vẹn dữ liệu logic.  
* **Luôn cấu hình FetchType.LAZY cho @ManyToOne:** Mặc định quan hệ @ManyToOne được cấu hình là FetchType.EAGER. Điều này sẽ dẫn đến lỗi kinh điển N+1 Query khi thực hiện câu lệnh truy vấn danh sách biến thể, làm suy giảm nghiêm trọng hiệu năng của hệ thống.

## **2\. Triển khai Mã nguồn Entity Mới (Module Quản lý Hàng hóa)**

### **2.1. ProductStatus.java (Enum Trạng thái)**

`package com.kiotviet.entity;`

`public enum ProductStatus {`  
    `ACTIVE,`  
    `INACTIVE`  
`}`

### **2.2. Product.java (Sản phẩm gốc)**

`package com.kiotviet.entity;`

`import jakarta.persistence.*;`  
`import lombok.*;`  
`import org.hibernate.annotations.SQLDelete;`  
`import org.hibernate.annotations.SQLRestriction;`

`import java.util.ArrayList;`  
`import java.util.List;`

`@Entity`  
`@Table(name = "products")`  
`@Getter`  
`@Setter`  
`@NoArgsConstructor`  
`@AllArgsConstructor`  
`@Builder`  
`@SQLDelete(sql = "UPDATE products SET is_deleted = true WHERE id = ?")`  
`@SQLRestriction("is_deleted = false")`  
`public class Product extends BaseEntity {`

    `@Column(nullable = false, length = 255)`  
    `private String name;`

    `@Column(nullable = false, unique = true, length = 50)`  
    `private String code; // Mã nhóm hàng (ví dụ: SP0001)`

    `@Column(columnDefinition = "TEXT")`  
    `private String description;`

    `@Enumerated(EnumType.STRING)`  
    `@Column(nullable = false, length = 20)`  
    `private ProductStatus status;`

    `@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)`  
    `@Builder.Default`  
    `private List<ProductVariant> variants = new ArrayList<>();`

    `/**`  
     `* Helper method để đồng bộ hai chiều (Bidirectional Synchronization)`  
     `* Tránh lỗi quên gán Product cho Variant khi lưu dữ liệu.`  
     `*/`  
    `public void addVariant(ProductVariant variant) {`  
        `variants.add(variant);`  
        `variant.setProduct(this);`  
    `}`

    `public void removeVariant(ProductVariant variant) {`  
        `variants.remove(variant);`  
        `variant.setProduct(null);`  
    `}`  
`}`

### **2.3. ProductVariant.java (Biến thể chi tiết)**

`package com.kiotviet.entity;`

`import jakarta.persistence.*;`  
`import lombok.*;`  
`import org.hibernate.annotations.SQLDelete;`  
`import org.hibernate.annotations.SQLRestriction;`

`import java.math.BigDecimal;`

`@Entity`  
`@Table(`  
    `name = "product_variants",`  
    `indexes = {`  
        `@Index(name = "idx_variant_sku", columnList = "sku"),`  
        `@Index(name = "idx_variant_barcode", columnList = "barcode")`  
    `}`  
`)`  
`@Getter`  
`@Setter`  
`@NoArgsConstructor`  
`@AllArgsConstructor`  
`@Builder`  
`@SQLDelete(sql = "UPDATE product_variants SET is_deleted = true WHERE id = ?")`  
`@SQLRestriction("is_deleted = false")`  
`public class ProductVariant extends BaseEntity {`

    `@Column(nullable = false, unique = true, length = 50)`  
    `private String sku;`

    `@Column(length = 50)`  
    `private String barcode;`

    `@Column(length = 50)`  
    `private String color;`

    `@Column(length = 50)`  
    `private String size;`

    `@Column(name = "import_price", precision = 12, scale = 2)`  
    `private BigDecimal importPrice;`

    `@Column(name = "sale_price", precision = 12, scale = 2)`  
    `private BigDecimal salePrice;`

    `@Column(nullable = false)`  
    `@Builder.Default`  
    `private Integer inventory = 0;`

    `@Enumerated(EnumType.STRING)`  
    `@Column(nullable = false, length = 20)`  
    `private ProductStatus status;`

    `@ManyToOne(fetch = FetchType.LAZY)`  
    `@JoinColumn(name = "product_id", nullable = false)`  
    `private Product product;`  
`}`

## **3\. Toàn bộ Mã nguồn Nền tảng Đã cung cấp (User Codebase)**

### **3.1. BaseEntity.java**

`package com.kiotviet.entity;`

`import jakarta.persistence.*;`  
`import lombok.Getter;`  
`import lombok.Setter;`  
`import org.springframework.data.annotation.CreatedDate;`  
`import org.springframework.data.annotation.LastModifiedDate;`  
`import org.springframework.data.jpa.domain.support.AuditingEntityListener;`

`import java.time.LocalDateTime;`

`@Getter`  
`@Setter`  
`@MappedSuperclass`  
`@EntityListeners(AuditingEntityListener.class)`  
`public abstract class BaseEntity {`

    `@Id`  
    `@GeneratedValue(strategy = GenerationType.IDENTITY)`  
    `private Long id;`

    `@CreatedDate`  
    `@Column(name = "created_at", nullable = false, updatable = false)`  
    `private LocalDateTime createdAt;`

    `@LastModifiedDate`  
    `@Column(name = "updated_at", nullable = false)`  
    `private LocalDateTime updatedAt;`

    `@Column(name = "is_deleted", nullable = false)`  
    `private Boolean isDeleted = false;`  
`}`

### **3.2. AppConfig.java**

`package com.kiotviet.config;`

`import io.swagger.v3.oas.models.Components;`  
`import io.swagger.v3.oas.models.OpenAPI;`  
`import io.swagger.v3.oas.models.info.Contact;`  
`import io.swagger.v3.oas.models.info.Info;`  
`import io.swagger.v3.oas.models.security.SecurityRequirement;`  
`import io.swagger.v3.oas.models.security.SecurityScheme;`  
`import org.springframework.context.annotation.Bean;`  
`import org.springframework.context.annotation.Configuration;`  
`import org.springframework.data.jpa.repository.config.EnableJpaAuditing;`

`@Configuration`  
`@EnableJpaAuditing`  
`public class AppConfig {`

    `@Bean`  
    `public OpenAPI openAPI() {`  
        `return new OpenAPI()`  
                `.info(new Info()`  
                        `.title("KiotViet Fashion API")`  
                        `.version("1.0.0")`  
                        `.description("Hệ thống POS quản lý cửa hàng quần áo thời trang")`  
                        `.contact(new Contact()`  
                                `.name("Dev Team")`  
                                `.email("dev@kiotviet-fashion.com")))`  
                `.components(new Components()`  
                        `.addSecuritySchemes("bearerAuth",`  
                                `new SecurityScheme()`  
                                        `.type(SecurityScheme.Type.HTTP)`  
                                        `.scheme("bearer")`  
                                        `.bearerFormat("JWT")`  
                                        `.description("Nhập JWT token (không cần tiền tố 'Bearer')")))`  
                `.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));`  
    `}`  
`}`

### **3.3. application.yml**

`spring:`  
  `datasource:`  
    `url: ${DB_URL:jdbc:mysql://localhost:3306/kiotviet_fashion?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true}`  
    `username: ${DB_USERNAME:root}`  
    `password: ${DB_PASSWORD:root}`  
    `hikari:`  
      `maximum-pool-size: 10`  
      `connection-timeout: 30000`  
      `idle-timeout: 600000`  
      `max-lifetime: 1800000`

  `jpa:`  
    `hibernate:`  
      `ddl-auto: update`  
    `show-sql: false`  
    `properties:`  
      `hibernate:`  
        `dialect: org.hibernate.dialect.MySQLDialect`  
        `format_sql: true`  
        `jdbc:`  
          `batch_size: 50`  
          `batch_versioned_data: true`  
        `order_inserts: true`  
        `order_updates: true`

  `jackson:`  
    `date-format: yyyy-MM-dd HH:mm:ss`  
    `time-zone: Asia/Ho_Chi_Minh`  
    `serialization:`  
      `write-dates-as-timestamps: false`

`jwt:`  
  `secret: ${JWT_SECRET:kiotviet-super-secret-key-must-be-at-least-32-chars-long-for-hs256}`  
  `expiration: 86400000`  
  `refresh-expiration: 604800000`

`springdoc:`  
  `api-docs:`  
    `path: /v3/api-docs`  
  `swagger-ui:`  
    `path: /swagger-ui/index.html`  
    `tags-sorter: alpha`  
    `operations-sorter: alpha`

`logging:`  
  `level:`  
    `com.kiotviet: DEBUG`  
    `org.springframework.security: DEBUG`  
    `org.hibernate.SQL: DEBUG`  
    `org.hibernate.type.descriptor.sql: TRACE`  
  `pattern:`  
    `console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"`

### **3.4. ApiResponse.java**

`package com.kiotviet.dto.response;`

`import com.fasterxml.jackson.annotation.JsonInclude;`  
`import lombok.Builder;`  
`import lombok.Getter;`

`import java.time.LocalDateTime;`

`@Getter`  
`@Builder`  
`@JsonInclude(JsonInclude.Include.NON_NULL)`  
`public class ApiResponse<T> {`

    `private int status;`  
    `private String message;`  
    `private T data;`

    `@Builder.Default`  
    `private LocalDateTime timestamp = LocalDateTime.now();`

    `public static <T> ApiResponse<T> success(T data) {`  
        `return ApiResponse.<T>builder()`  
                `.status(200)`  
                `.message("Thành công")`  
                `.data(data)`  
                `.build();`  
    `}`

    `public static <T> ApiResponse<T> success(String message) {`  
        `return ApiResponse.<T>builder()`  
                `.status(200)`  
                `.message(message)`  
                `.build();`  
    `}`

    `public static <T> ApiResponse<T> created(T data) {`  
        `return ApiResponse.<T>builder()`  
                `.status(201)`  
                `.message("Tạo mới thành công")`  
                `.data(data)`  
                `.build();`  
    `}`

    `public static <T> ApiResponse<T> error(int status, String message) {`  
        `return ApiResponse.<T>builder()`  
                `.status(status)`  
                `.message(message)`  
                `.build();`  
    `}`  
`}`

### **3.5. GlobalExceptionHandler.java**

`package com.kiotviet.exception;`

`import com.kiotviet.dto.response.ApiResponse;`  
`import lombok.extern.slf4j.Slf4j;`  
`import org.springframework.http.HttpStatus;`  
`import org.springframework.http.ResponseEntity;`  
`import org.springframework.security.access.AccessDeniedException;`  
`import org.springframework.security.authentication.BadCredentialsException;`  
`import org.springframework.validation.FieldError;`  
`import org.springframework.web.bind.MethodArgumentNotValidException;`  
`import org.springframework.web.bind.annotation.ExceptionHandler;`  
`import org.springframework.web.bind.annotation.RestControllerAdvice;`

`import java.util.HashMap;`  
`import java.util.Map;`

`@Slf4j`  
`@RestControllerAdvice`  
`public class GlobalExceptionHandler {`

    `@ExceptionHandler(EntityNotFoundException.class)`  
    `public ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException ex) {`  
        `log.warn("Entity not found: {}", ex.getMessage());`  
        `return ResponseEntity`  
                `.status(HttpStatus.NOT_FOUND)`  
                `.body(ApiResponse.error(404, ex.getMessage()));`  
    `}`

    `@ExceptionHandler(MethodArgumentNotValidException.class)`  
    `public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(`  
            `MethodArgumentNotValidException ex) {`

        `Map<String, String> errors = new HashMap<>();`  
        `ex.getBindingResult().getAllErrors().forEach(error -> {`  
            `String field = ((FieldError) error).getField();`  
            `String message = error.getDefaultMessage();`  
            `errors.put(field, message);`  
        `});`

        `log.warn("Validation failed: {}", errors);`  
        `return ResponseEntity`  
                `.status(HttpStatus.BAD_REQUEST)`  
                `.body(ApiResponse.<Map<String, String>>builder()`  
                        `.status(400)`  
                        `.message("Dữ liệu đầu vào không hợp lệ")`  
                        `.data(errors)`  
                        `.build());`  
    `}`

    `@ExceptionHandler(BusinessException.class)`  
    `public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {`  
        `log.warn("Business error: {}", ex.getMessage());`  
        `return ResponseEntity`  
                `.status(HttpStatus.BAD_REQUEST)`  
                `.body(ApiResponse.error(400, ex.getMessage()));`  
    `}`

    `@ExceptionHandler(InsufficientStockException.class)`  
    `public ResponseEntity<ApiResponse<Map<String, Object>>> handleInsufficientStock(`  
            `InsufficientStockException ex) {`

        `log.warn("Insufficient stock: sku={}, requested={}, available={}",`  
                `ex.getSku(), ex.getRequested(), ex.getAvailable());`

        `Map<String, Object> detail = Map.of(`  
                `"sku", ex.getSku(),`  
                `"requested", ex.getRequested(),`  
                `"available", ex.getAvailable()`  
        `);`

        `return ResponseEntity`  
                `.status(HttpStatus.CONFLICT)`  
                `.body(ApiResponse.<Map<String, Object>>builder()`  
                        `.status(409)`  
                        `.message(ex.getMessage())`  
                        `.data(detail)`  
                        `.build());`  
    `}`

    `@ExceptionHandler(BadCredentialsException.class)`  
    `public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {`  
        `log.warn("Bad credentials attempt");`  
        `return ResponseEntity`  
                `.status(HttpStatus.UNAUTHORIZED)`  
                `.body(ApiResponse.error(401, "Tên đăng nhập hoặc mật khẩu không đúng"));`  
    `}`

    `@ExceptionHandler(AccessDeniedException.class)`  
    `public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {`  
        `log.warn("Access denied: {}", ex.getMessage());`  
        `return ResponseEntity`  
                `.status(HttpStatus.FORBIDDEN)`  
                `.body(ApiResponse.error(403, "Bạn không có quyền thực hiện thao tác này"));`  
    `}`

    `@ExceptionHandler(Exception.class)`  
    `public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {`  
        `log.error("Unexpected error: {}", ex.getMessage(), ex);`  
        `return ResponseEntity`  
                `.status(HttpStatus.INTERNAL_SERVER_ERROR)`  
                `.body(ApiResponse.error(500, "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau"));`  
    `}`  
`}`

### **3.6. InsufficientStockException.java**

`package com.kiotviet.exception;`

`import lombok.Getter;`  
`import org.springframework.http.HttpStatus;`  
`import org.springframework.web.bind.annotation.ResponseStatus;`

`@Getter`  
`@ResponseStatus(HttpStatus.CONFLICT)`  
`public class InsufficientStockException extends RuntimeException {`

    `private final String sku;`  
    `private final int requested;`  
    `private final int available;`

    `public InsufficientStockException(String sku, int requested, int available) {`  
        `super(String.format("Hàng [%s] không đủ số lượng. Yêu cầu: %d, tồn kho: %d",`  
                `sku, requested, available));`  
        `this.sku = sku;`  
        `this.requested = requested;`  
        `this.available = available;`  
    `}`  
`}`

### **3.7. JwtAuthenticationFilter.java**

`package com.kiotviet.security;`

`import jakarta.servlet.FilterChain;`  
`import jakarta.servlet.ServletException;`  
`import jakarta.servlet.http.HttpServletRequest;`  
`import jakarta.servlet.http.HttpServletResponse;`  
`import lombok.RequiredArgsConstructor;`  
`import lombok.extern.slf4j.Slf4j;`  
`import org.springframework.lang.NonNull;`  
`import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;`  
`import org.springframework.security.core.context.SecurityContextHolder;`  
`import org.springframework.security.core.userdetails.UserDetails;`  
`import org.springframework.security.core.userdetails.UserDetailsService;`  
`import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;`  
`import org.springframework.stereotype.Component;`  
`import org.springframework.util.StringUtils;`  
`import org.springframework.web.filter.OncePerRequestFilter;`

`import java.io.IOException;`

`@Slf4log`  
`@Component`  
`@RequiredArgsConstructor`  
`public class JwtAuthenticationFilter extends OncePerRequestFilter {`

    `private final JwtUtil jwtUtil;`  
    `private final UserDetailsService userDetailsService;`

    `@Override`  
    `protected void doFilterInternal(`  
            `@NonNull HttpServletRequest request,`  
            `@NonNull HttpServletResponse response,`  
            `@NonNull FilterChain filterChain`  
    `) throws ServletException, IOException {`

        `final String authHeader = request.getHeader("Authorization");`

        `if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {`  
            `filterChain.doFilter(request, response);`  
            `return;`  
        `}`

        `final String jwt = authHeader.substring(7);`

        `try {`  
            `final String username = jwtUtil.extractUsername(jwt);`

            `if (StringUtils.hasText(username)`  
                    `&& SecurityContextHolder.getContext().getAuthentication() == null) {`

                `UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);`

                `if (jwtUtil.validateToken(jwt, userDetails)) {`  
                    `UsernamePasswordAuthenticationToken authToken =`  
                            `new UsernamePasswordAuthenticationToken(`  
                                    `userDetails,`  
                                    `null,`  
                                    `userDetails.getAuthorities()`  
                            `);`  
                    `authToken.setDetails(`  
                            `new WebAuthenticationDetailsSource().buildDetails(request)`  
                    `);`  
                    `SecurityContextHolder.getContext().setAuthentication(authToken);`  
                    `log.debug("Authenticated user: {}, URI: {}", username, request.getRequestURI());`  
                `}`  
            `}`  
        `} catch (Exception e) {`  
            `log.warn("JWT authentication failed for URI {}: {}", request.getRequestURI(), e.getMessage());`  
        `}`

        `filterChain.doFilter(request, response);`  
    `}`  
`}`

### **3.8. JwtUtil.java**

`package com.kiotviet.security;`

`import io.jsonwebtoken.Claims;`  
`import io.jsonwebtoken.ExpiredJwtException;`  
`import io.jsonwebtoken.Jwts;`  
`import io.jsonwebtoken.MalformedJwtException;`  
`import io.jsonwebtoken.security.Keys;`  
`import io.jsonwebtoken.security.SignatureException;`  
`import lombok.extern.slf4j.Slf4j;`  
`import org.springframework.beans.factory.annotation.Value;`  
`import org.springframework.security.core.userdetails.UserDetails;`  
`import org.springframework.stereotype.Component;`

`import javax.crypto.SecretKey;`  
`import java.nio.charset.StandardCharsets;`  
`import java.util.Date;`  
`import java.util.Map;`  
`import java.util.function.Function;`

`@Slf4j`  
`@Component`  
`public class JwtUtil {`

    `@Value("${jwt.secret}")`  
    `private String secret;`

    `@Value("${jwt.expiration}")`  
    `private long expiration;`

    `@Value("${jwt.refresh-expiration}")`  
    `private long refreshExpiration;`

    `private SecretKey getSigningKey() {`  
        `return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));`  
    `}`

    `public String generateToken(UserDetails userDetails, Map<String, Object> extraClaims) {`  
        `return buildToken(userDetails, extraClaims, expiration);`  
    `}`

    `public String generateRefreshToken(UserDetails userDetails) {`  
        `return buildToken(userDetails, Map.of(), refreshExpiration);`  
    `}`

    `private String buildToken(UserDetails userDetails,`  
                               `Map<String, Object> extraClaims,`  
                               `long expirationMs) {`  
        `Date now = new Date();`  
        `Date expiryDate = new Date(now.getTime() + expirationMs);`

        `return Jwts.builder()`  
                `.claims(extraClaims)`  
                `.subject(userDetails.getUsername())`  
                `.issuedAt(now)`  
                `.expiration(expiryDate)`  
                `.signWith(getSigningKey())`  
                `.compact();`  
    `}`

    `public boolean validateToken(String token, UserDetails userDetails) {`  
        `try {`  
            `final String username = extractUsername(token);`  
            `return username.equals(userDetails.getUsername()) && !isTokenExpired(token);`  
        `} catch (ExpiredJwtException e) {`  
            `log.warn("JWT token expired: {}", e.getMessage());`  
            `return false;`  
        `} catch (SignatureException e) {`  
            `log.warn("JWT signature invalid: {}", e.getMessage());`  
            `return false;`  
        `} catch (MalformedJwtException e) {`  
            `log.warn("JWT token malformed: {}", e.getMessage());`  
            `return false;`  
        `} catch (Exception e) {`  
            `log.warn("JWT validation error: {}", e.getMessage());`  
            `return false;`  
        `}`  
    `}`

    `public String extractUsername(String token) {`  
        `return extractClaim(token, Claims::getSubject);`  
    `}`

    `public Date extractExpiration(String token) {`  
        `return extractClaim(token, Claims::getExpiration);`  
    `}`

    `public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {`  
        `final Claims claims = extractAllClaims(token);`  
        `return claimsResolver.apply(claims);`  
    `}`

    `private Claims extractAllClaims(String token) {`  
        `return Jwts.parser()`  
                `.verifyWith(getSigningKey())`  
                `.build()`  
                `.parseSignedClaims(token)`  
                `.getPayload();`  
    `}`

    `private boolean isTokenExpired(String token) {`  
        `return extractExpiration(token).before(new Date());`  
    `}`  
`}`

### **3.9. KiotVietApplication.java**

`package com.kiotviet;`

`import org.springframework.boot.SpringApplication;`  
`import org.springframework.boot.autoconfigure.SpringBootApplication;`

`@SpringBootApplication`  
`public class KiotVietApplication {`

    `public static void main(String[] args) {`  
        `SpringApplication.run(KiotVietApplication.class, args);`  
    `}`  
`}`

### **3.10. pom.xml**

`<?xml version="1.0" encoding="UTF-8"?>`  
`<project xmlns="http://maven.apache.org/POM/4.0.0"`  
         `xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"`  
         `xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">`  
    `<modelVersion>4.0.0</modelVersion>`

    `<parent>`  
        `<groupId>org.springframework.boot</groupId>`  
        `<artifactId>spring-boot-starter-parent</artifactId>`  
        `<version>3.2.5</version>`  
        `<relativePath/>`  
    `</parent>`

    `<groupId>com.kiotviet</groupId>`  
    `<artifactId>kiotviet-fashion</artifactId>`  
    `<version>1.0.0</version>`  
    `<name>KiotViet Fashion Clone</name>`  
    `<description>POS system for fashion retail</description>`

    `<properties>`  
        `<java.version>17</java.version>`  
        `<jjwt.version>0.12.5</jjwt.version>`  
        `<springdoc.version>2.5.0</springdoc.version>`  
    `</properties>`

    `<dependencies>`  
        `<dependency>`  
            `<groupId>org.springframework.boot</groupId>`  
            `<artifactId>spring-boot-starter-web</artifactId>`  
        `</dependency>`  
        `<dependency>`  
            `<groupId>org.springframework.boot</groupId>`  
            `<artifactId>spring-boot-starter-data-jpa</artifactId>`  
        `</dependency>`  
        `<dependency>`  
            `<groupId>org.springframework.boot</groupId>`  
            `<artifactId>spring-boot-starter-security</artifactId>`  
        `</dependency>`  
        `<dependency>`  
            `<groupId>org.springframework.boot</groupId>`  
            `<artifactId>spring-boot-starter-validation</artifactId>`  
        `</dependency>`  
        `<dependency>`  
            `<groupId>com.mysql</groupId>`  
            `<artifactId>mysql-connector-j</artifactId>`  
            `<version>8.3.0</version>`  
            `<scope>runtime</scope>`  
        `</dependency>`  
        `<dependency>`  
            `<groupId>io.jsonwebtoken</groupId>`  
            `<artifactId>jjwt-api</artifactId>`  
            `<version>${jjwt.version}</version>`  
          
        `<dependency>`  
            `<groupId>io.jsonwebtoken</groupId>`  
            `<artifactId>jjwt-impl</artifactId>`  
            `<version>${jjwt.version}</version>`  
            `<scope>runtime</scope>`  
        `</dependency>`  
        `<dependency>`  
            `<groupId>io.jsonwebtoken</groupId>`  
            `<artifactId>jjwt-jackson</artifactId>`  
            `<version>${jjwt.version}</version>`  
            `<scope>runtime</scope>`  
        `</dependency>`  
        `<dependency>`  
            `<groupId>org.springdoc</groupId>`  
            `<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>`  
            `<version>${springdoc.version}</version>`  
        `</dependency>`  
        `<dependency>`  
            `<groupId>org.projectlombok</groupId>`  
            `<artifactId>lombok</artifactId>`  
            `<optional>true</optional>`  
        `</dependency>`  
        `<dependency>`  
            `<groupId>org.springframework.boot</groupId>`  
            `<artifactId>spring-boot-starter-test</artifactId>`  
            `<scope>test</scope>`  
        `</dependency>`  
        `<dependency>`  
            `<groupId>org.springframework.security</groupId>`  
            `<artifactId>spring-security-test</artifactId>`  
            `<scope>test</scope>`  
        `</dependency>`  
    

    `<build>`  
        `<plugins>`  
            `<plugin>`  
                `<groupId>org.springframework.boot</groupId>`  
                `<artifactId>spring-boot-maven-plugin</artifactId>`  
                `<configuration>`  
                    `<excludes>`  
                        `<exclude>`  
                            `<groupId>org.projectlombok</groupId>`  
                            `<artifactId>lombok</artifactId>`  
                        `</exclude>`  
                      
                  
              
          
