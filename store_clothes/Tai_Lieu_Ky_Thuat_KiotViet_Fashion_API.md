# **TÀI LIỆU KỸ THUẬT HỆ THỐNG KIOTVIET FASHION CLONE BACKEND API**

Tài liệu này cung cấp cái nhìn toàn diện, chuyên sâu về mặt kiến trúc, cấu hình hệ thống, thiết kế cơ sở dữ liệu và các giải pháp tối ưu hóa hiệu năng, phòng chống Race Condition cho hệ thống POS quản lý cửa hàng thời trang KiotViet Fashion Clone.

## **1\. Tổng Quan Hệ Thống**

Hệ thống KiotViet Fashion Clone Backend API được xây dựng trên nền tảng Spring Boot 3.2.x kết hợp cùng Java 17 và hệ quản trị cơ sở dữ liệu MySQL 8\. Mục tiêu cốt lõi của hệ thống là cung cấp các dịch vụ RESTful API mạnh mẽ, có khả năng xử lý giao dịch mua hàng, nhập kho đồng thời cao, đảm bảo tính nhất quán của dữ liệu tồn kho, tích hợp sẵn các cơ chế bảo mật tiêu chuẩn và hệ thống báo cáo quản trị nâng cao.

### **Các Tính Năng Cốt Lõi**

* **Quản lý sản phẩm và ma trận biến thể:** Hỗ trợ cấu hình sản phẩm phân loại theo màu sắc, kích thước và tự động sinh mã SKU chuẩn hóa.  
* **Quản lý nhập kho:** Quy trình lập phiếu nhập kho từ nhà cung cấp, kiểm soát công nợ và cập nhật lịch sử thẻ kho.  
* **Thanh toán hóa đơn (Checkout):** Xử lý giao dịch bán hàng tại quầy tốc độ cao, áp dụng cơ chế khóa nghiêm ngặt chống âm kho và xử lý đồng thời.  
* **Báo cáo quản trị:** Hệ thống Dashboard tổng quan, báo cáo doanh thu tài chính, danh sách mặt hàng bán chạy và cảnh báo hàng tồn lâu (Dead Stock).

## **2\. Kiến Trúc và Cấu Trúc Thư Mục Dự Án**

Dự án tuân thủ nghiêm ngặt mô hình kiến trúc phân lớp chuẩn của Spring Boot, giúp phân tách rõ ràng trách nhiệm giữa các tầng giao tiếp, xử lý nghiệp vụ và quản trị dữ liệu.

### **Sơ đồ cấu trúc Package**

`com.kiotviet`  
`├── annotation/        # Custom validation (@ValidPhone, @ValidSku)`  
`├── config/            # Cấu hình ứng dụng (AppConfig, SecurityConfig)`  
`├── controller/        # REST Controllers tiếp nhận request`  
`├── dto/               # Đối tượng chuyển đổi dữ liệu`  
`│   ├── request/       # Dữ liệu đầu vào đi kèm khai báo @Valid`  
`│   └── response/      # Định dạng dữ liệu đầu ra trả về Client`  
`├── entity/            # Các thực thể JPA ánh xạ trực tiếp xuống DB kế thừa BaseEntity`  
`├── enums/             # Tập hợp các Enum dùng chung (Role, Status, PaymentMethod)`  
`├── exception/         # Xử lý ngoại lệ tập trung (GlobalExceptionHandler)`  
`├── repository/        # Lớp trừu tượng dữ liệu Spring Data JPA Repositories`  
`├── security/          # Thành phần bảo mật (JwtUtil, JwtAuthenticationFilter)`  
`├── service/           # Lớp chứa logic nghiệp vụ cốt lõi và kiểm soát @Transactional`  
`└── util/              # Các class tiện ích bổ trợ hệ thống`

### **Danh Sách Các Endpoint Hệ Thống Chính**

| Method | Endpoint | Quyền Hạn Truy Cập | Mô Tả Chức Năng   |
| :---- | :---- | :---- | :---- |
| **POST** | /api/v1/auth/login | Public | Xác thực người dùng và cấp mã JWT token. |
| **POST** | /api/v1/products/matrix | OWNER, MANAGER | Tạo sản phẩm mới cùng ma trận các biến thể liên quan. |
| **POST** | /api/v1/imports | OWNER, MANAGER, WAREHOUSE | Khởi tạo phiếu nhập kho (trạng thái DRAFT). |
| **POST** | /api/v1/imports/{id}/complete | OWNER, MANAGER, WAREHOUSE | Chốt phiếu nhập, cập nhật tồn kho gốc và thẻ kho. |
| **POST** | /api/v1/orders/checkout | OWNER, MANAGER, CASHIER | Thực hiện thanh toán đơn hàng với xử lý kiểm kho đồng thời. |
| **GET** | /api/v1/reports/financial | OWNER, MANAGER | Truy xuất dữ liệu báo cáo doanh thu, chi phí và lợi nhuận. |

## **3\. Cấu Hình Ứng Dụng & Quản Lý Thư Viện Phụ Thuộc**

### **Phân Tích File Quản Lý pom.xml**

Dự án sử dụng Spring Boot phiên bản **3.2.5** và Java phiên bản **17** làm nền tảng phát triển cốt lõi. Dưới đây là các nhóm thư viện quan trọng được tích hợp để đáp ứng chuẩn vận hành doanh nghiệp:

* **Spring Boot Starters:** Sử dụng spring-boot-starter-web để xây dựng REST API, spring-boot-starter-data-jpa để quản trị tầng dữ liệu, và spring-boot-starter-security cho việc mã hóa phân quyền bảo mật hệ thống.  
* **Cơ chế Token JWT:** Sử dụng bộ thư viện jjwt-api, jjwt-impl, và jjwt-jackson phiên bản mới nhất **0.12.5** giúp khắc phục toàn bộ lỗ hổng bảo mật và các phương thức đã bị deprecated của các bản 0.9.x cũ.  
* **Database Migration:** Thư viện flyway-mysql quản trị sự thay đổi cấu trúc bảng cơ sở dữ liệu một cách nhất quán qua từng phiên bản, loại bỏ hoàn toàn rủi ro mất mát dữ liệu do cơ chế tự động sinh của Hibernate (ddl-auto=update) gây ra.  
* **Giám sát Hệ thống:** Tích hợp spring-boot-starter-actuator hỗ trợ việc kiểm tra trạng thái sức khỏe ứng dụng (Health Check) phục vụ cho hạ tầng điều phối Docker container.

### **Kiến Trúc Lớp Cấu Hình AppConfig.java**

Lớp AppConfig.java gom toàn bộ các cấu hình nền tảng. Đặc biệt, việc đặt annotation @EnableJpaAuditing tại đây thay vì tại Main Class đem lại lợi ích kiến trúc cực kỳ lớn khi viết Unit Test, giúp Web Layer (như @WebMvcTest) khởi tạo mượt mà mà không bị lỗi thiếu cơ sở hạ tầng Auditing.  
Hệ thống cấu hình OpenAPI 3 / Swagger UI một cách tự động và đồng bộ, đính kèm cơ chế Bearer Auth Token trực tiếp lên tất cả endpoints ngoại trừ các luồng đăng nhập công khai:

`@Configuration`  
`@EnableJpaAuditing   // Bật tính năng tự động ghi nhận ngày tạo/cập nhật thực thể`  
`@EnableAsync         // Kích hoạt xử lý bất đồng bộ cho luồng Audit Log giảm block thread chính`  
`public class AppConfig {`

    `@Bean`  
    `public OpenAPI openAPI() {`  
        `return new OpenAPI()`  
                `.info(new Info()`  
                        `.title("KiotViet Fashion API")`  
                        `.version("1.0.0")`  
                        `.description("Hệ thống POS quản lý cửa hàng quần áo thời trang"))`  
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

## **4\. Thiết Kế Cơ Sở Dữ Liệu và Cơ Chế Migration (Flyway)**

Cơ sở dữ liệu được thiết kế chuẩn hóa để lưu trữ đầy đủ mọi thông tin lịch sử giao dịch và phục vụ truy vấn báo cáo đa chiều một cách nhanh chóng.

### **Chi Tiết Các Script Cấu Trúc Bảng Dữ Liệu**

* **V1\_\_create\_user\_role.sql:** Khởi tạo hệ thống quản trị định danh gồm bảng roles, users, và bảng liên kết nhiều-nhiều user\_roles. Đồng thời thiết lập bảng nhật ký hệ thống audit\_logs lưu vết IP, User Agent, hành động của người dùng hỗ trợ index tìm kiếm theo thực thể.  
* **V2\_\_create\_product\_tables.sql:** Định nghĩa danh mục sản phẩm (categories) phân cấp, bảng sản phẩm chính (products) và bảng biến thể chi tiết (product\_variants) quản lý giá nhập, giá bán lẻ và số lượng tồn kho thực tế.  
* **V3\_\_create\_inventory\_tables.sql:** Thiết lập thực thể nhà cung cấp (suppliers), chứng từ nhập kho (import\_receipts) đi kèm trạng thái xử lý dữ liệu và bảng lịch sử kho đặc thù stock\_history. Bảng này mang tính chất immutable giúp theo vết biến động kho với hai trường thông tin cực kỳ quan trọng là balance\_before và balance\_after.  
* **V4\_\_create\_order\_tables.sql:** Tạo bảng khách hàng (customers), thông tin đơn hàng tổng quan (orders) và chi tiết mặt hàng trong đơn lẻ order\_items. Tại đây, giá nhập và giá bán tại đúng thời điểm phát sinh giao dịch được chụp lại (snapshot) để đảm bảo tính chính xác cho các báo cáo tài chính về sau cho dù giá của biến thể có thay đổi.  
* **V5\_\_create\_report\_indexes.sql:** Đây là bước tối ưu hóa chuyên sâu khi chủ động bổ sung các Composite Index như (variant\_id, created\_at) trên bảng order\_items và (status, created\_at) trên bảng orders. Giúp MySQL quét dữ liệu trong phân vùng chỉ định thay vì Full Table Scan khi chạy các báo cáo doanh thu hay tìm kiếm hàng tồn lâu ngày.  
* **V6\_\_seed\_data.sql:** Cung cấp các dữ liệu thiết lập ban đầu bao gồm các tài khoản hệ thống chuẩn hóa như admin (Quyền OWNER), manager (Quyền MANAGER), và cashier (Quyền CASHIER) với mật khẩu mẫu đã qua mã hóa BCrypt cấp độ 12 bảo mật cao.

## **5\. Thành Phần Validation Tùy Biến (Custom Annotations)**

Để đảm bảo dữ liệu luôn sạch trước khi đi vào tầng xử lý nghiệp vụ, hệ thống phát triển các Custom Annotation Validation thay thế cho các Pattern Regex thô cứng thông thường, mang lại khả năng tái sử dụng cao và đồng bộ hóa thông điệp cảnh báo trên toàn hệ thống.

### **Kiểm Tra Định Dạng Số Điện Thoại Việt Nam (@ValidPhone)**

Annotation hỗ trợ chuẩn hóa chuỗi đầu vào bằng cách tự động loại bỏ các khoảng trắng dư thừa và các ký tự gạch ngang trước khi đối sánh mẫu định dạng đầu số viễn thông Việt Nam (10 chữ số, bắt đầu bằng các đầu số 03, 05, 07, 08, 09).

`public class PhoneValidator implements ConstraintValidator<ValidPhone, String> {`  
    `private static final Pattern VN_PHONE = Pattern.compile("^0[35789]\\d{8}$");`  
    `private boolean required;`

    `@Override`  
    `public void initialize(ValidPhone annotation) {`  
        `this.required = annotation.required();`  
    `}`

    `@Override`  
    `public boolean isValid(String value, ConstraintValidatorContext context) {`  
        `if (value == null || value.isBlank()) {`  
            `return !required;`  
        `}`  
        `String cleaned = value.replaceAll("[\\s-]", "");`  
        `return VN_PHONE.matcher(cleaned).matches();`  
    `}`  
`}`

### **Kiểm Tra Định Dạng Mã SKU Sản Phẩm (@ValidSku)**

Đảm bảo mã SKU tuân thủ nghiêm ngặt quy tắc cấu trúc dữ liệu của hệ thống quản trị bán hàng chuyên nghiệp: Chỉ chấp nhận chữ cái in hoa từ A-Z, các ký tự số từ 0-9 và phân tách rõ ràng bằng dấu gạch ngang (Ví dụ: ASM-DO-L, POLO-XANH-XL).

`public class SkuValidator implements ConstraintValidator<ValidSku, String> {`  
    `private static final Pattern SKU_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9-]{1,}[A-Z0-9]$");`

    `@Override`  
    `public boolean isValid(String value, ConstraintValidatorContext context) {`  
        `if (value == null || value.isBlank()) return true;`  
        `return SKU_PATTERN.matcher(value).matches();`  
    `}`  
`}`

## **6\. Checklist Sẵn Sàng Triển Khai & Các Cơ Chế Tối Ưu Hóa**

Hệ thống đạt tiêu chuẩn vận hành thực tế nhờ tích hợp đầy đủ các giải pháp kỹ thuật giải quyết bài toán tải cao và an toàn thông tin dữ liệu:

### **Bảo Mật Hệ Thống Chuyên Sâu**

* **Không mã hóa cứng thông tin nhạy cảm:** Toàn bộ mật khẩu cơ sở dữ liệu, khoá ký JWT bí mật đều được cấu hình độc lập qua biến môi trường (.env).  
* **An toàn dữ liệu người dùng:** Thuật toán băm mật khẩu BCrypt được thiết lập cấu hình Strength ở mức độ 12 nhằm ngăn chặn triệt để các cuộc tấn công brute-force giải mã thông thường.  
* **Che giấu cấu trúc lỗi ứng dụng:** Cơ chế GlobalExceptionHandler bắt trọn vẹn mọi ngoại lệ phát sinh để chuyển đổi thành cấu trúc lỗi JSON chuẩn hóa bảo mật, tuyệt đối không để lộ Stack Trace chi tiết ra môi trường Production.

### **Kiểm Soát Race Condition & Tránh Âm Kho**

* **Cơ chế Khóa Bi quan (Pessimistic Lock):** Sử dụng cú pháp SELECT FOR UPDATE tại tầng dữ liệu khi thực hiện checkout hoặc cập nhật số lượng tồn kho nhằm ngăn chặn tối đa việc nhiều luồng đồng thời làm sai lệch dữ liệu kho.  
* **Sắp xếp tài nguyên tránh Deadlock:** Hệ thống luôn tiến hành sắp xếp danh sách mã biến thể sản phẩm (variantId) theo một thứ tự cố định trước khi gọi lệnh lock, loại bỏ hoàn toàn rủi ro xảy ra hiện tượng Circular Deadlock giữa các luồng giao dịch đồng thời.  
* **Cấu hình Lock Timeout:** Thời gian chờ khóa tối đa được giới hạn nghiêm ngặt ở ngưỡng 3000ms nhằm đảm bảo giải phóng tài nguyên hệ thống, không để xảy ra tình trạng nghẽn hàng đợi request vô hạn.

### **Tối Ưu Hóa Hiệu Năng Xử Lý (Performance)**

* **Cấu hình Batch Processing chuyên sâu:** Kích hoạt cờ cấu hình rewriteBatchedStatements=true trong JDBC URL kết hợp đặt giá trị Batch Size lên mức 50 phần tử giúp gộp các lệnh insert/update của hàm saveAll() thành một câu lệnh duy nhất gửi xuống Database, tiết kiệm thời gian kết nối mạng.  
* **Xử lý bất đồng bộ (Asynchronous):** Các tác vụ phụ trợ không nằm trong luồng xử lý giao dịch chính như ghi nhật ký hệ thống (Audit Log) được chuyển hoàn toàn sang xử lý bất đồng bộ nhờ @Async và CompletableFuture.  
* **Truy vấn Dashboard song song:** Tận dụng tối đa sức mạnh của CompletableFuture.allOf() để thực thi đồng thời 4 câu lệnh SQL độc lập cho trang Dashboard, giảm tổng thời gian phản hồi API xuống mức tối thiểu bằng thời gian của câu lệnh lâu nhất.