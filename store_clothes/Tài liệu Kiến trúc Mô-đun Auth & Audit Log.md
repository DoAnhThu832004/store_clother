# **BÁO CÁO PHÂN TÍCH KIẾN TRÚC VÀ THIẾT KẾ HỆ THỐNG MÔ-ĐUN AUTHENTICATION VÀ AUDIT LOG**

Tài liệu này cung cấp cái nhìn toàn diện và sâu sắc về cấu trúc thiết kế, các quyết định kiến trúc cốt lõi, cùng chi tiết triển khai mã nguồn của hai phân hệ quan trọng bậc nhất trong hệ thống quản lý kinh doanh (KiotViet): Mô-đun Xác thực & Phân quyền (Authentication & Authorization) và Mô-đun Nhật ký Hệ thống (Audit Logging).

## **1\. Tổng Quan Hệ Thống (System Overview)**

Hệ thống được xây dựng trên nền tảng Spring Boot, tích hợp sâu với Spring Security để đảm bảo an toàn thông tin và kiểm soát truy cập nghiêm ngặt. Để đáp ứng nhu cầu vận hành với tần suất giao dịch cao và tính ổn định tuyệt đối, hệ thống tách biệt luồng xử lý nghiệp vụ chính và luồng ghi nhật ký hệ thống thông qua mô hình xử lý bất đồng bộ (Asynchronous Architecture). Điều này giúp tối ưu hóa thời gian phản hồi (Response Time) của người dùng cuối, đồng thời cô lập các rủi ro có thể xảy ra trong quá trình ghi nhận log, không làm ảnh hưởng đến tính toàn vẹn của các giao dịch kinh doanh lõi.

## **2\. Phân Hệ Xác Thực và Phân Quyền (Authentication & Authorization Module)**

Phân hệ Xác thực và Phân quyền áp dụng cơ chế bảo mật dựa trên Token (Token-Based Authentication) sử dụng JSON Web Token (JWT). Kiến trúc phân quyền áp dụng mô hình Kiểm soát truy cập dựa trên vai trò (Role-Based Access Control \- RBAC) với cấu trúc phân cấp chức năng rõ ràng.

### **2.1. Thiết Kế Thực Thể và Mô Hình Dữ Liệu (Data Models & Entities)**

Mô hình dữ liệu bao gồm hai thực thể chính là User và Role, liên kết với nhau qua mối quan hệ Nhiều-Nhiều (Many-to-Many). Dưới đây là bảng đặc tả chi tiết cấu trúc dữ liệu của các thực thể:

| Thực Thể | Trường Dữ Liệu | Kiểu Dữ Liệu / Ràng Buộc | Mô Tả Chức Năng |
| :---- | :---- | :---- | :---- |
| **User** (Người dùng) | username | String (50), Unique, Not Null | Tên đăng nhập duy nhất của người dùng trong hệ thống. |
|  | passwordHash | String, Not Null | Chuỗi mật khẩu đã được mã hóa bằng thuật toán BCrypt. |
|  | fullName | String (200), Not Null | Họ và tên đầy đủ của người dùng. |
|  | email / phone | String (100) / String (20) | Thông tin liên hệ (Email và số điện thoại). |
|  | status | UserStatus (Enum), Not Null | Trạng thái tài khoản: ACTIVE, INACTIVE, LOCKED. |
|  | roles | Set\<Role\>, FetchType.EAGER | Danh sách các vai trò được gán cho người dùng. |
| **Role** (Vai trò) | name | RoleName (Enum), Unique, Not Null | Tên vai trò cố định: ROLE\_OWNER, ROLE\_MANAGER, ROLE\_CASHIER, ROLE\_WAREHOUSE\_STAFF. |
|  | description | String (200) | Mô tả chi tiết về phạm vi quyền hạn của vai trò. |

*\* Ghi chú của Senior Kiến trúc sư: Thực thể User áp dụng cơ chế Soft Delete (Xóa mềm) thông qua các annotation @SQLDelete và @SQLRestriction nhằm bảo toàn tính toàn vẹn dữ liệu lịch sử trong các bảng hóa đơn, nhập kho khi một nhân sự nghỉ việc. Đồng thời, cấu hình FetchType.EAGER cho mối quan hệ gán vai trò (roles) là một ngoại lệ hợp lý có chủ đích. Do Spring Security yêu cầu nạp toàn bộ danh sách quyền (Authorities) ngay tại bộ lọc (Filter context) nằm ngoài phạm vi transaction của database, việc nạp EAGER giúp ngăn ngừa triệt để lỗi LazyInitializationException khét tiếng.*

### **2.2. Phân Tích Luồng Nghiệp Vụ Xác Thực (Authentication Workflow)**

Quy trình xác thực người dùng diễn ra thông qua bốn bước xử lý nghiêm ngặt:

1. **Tiếp nhận yêu cầu:** Người dùng gửi thông tin đăng nhập (username, password) qua endpoint POST /api/v1/auth/login. Dữ liệu được đóng gói vào đối tượng LoginRequest và kiểm tra tính hợp lệ bằng @Valid.  
2. **Ủy nhiệm xác thực:** AuthService chuyển giao thông tin cho AuthenticationManager.authenticate() của Spring Security. Tại đây, hệ thống tự động kích hoạt UserDetailsServiceImpl.loadUserByUsername() để truy vấn thông tin từ UserRepository.  
3. **Kiểm tra bảo mật chuyên sâu:** Spring Security tự động đối sánh chuỗi mật khẩu thô nhận được với chuỗi băm lưu trong database thông qua PasswordEncoder (BCrypt). Đồng thời, hệ thống kiểm tra các trạng thái an toàn của tài khoản: tài khoản có bị khóa không (isAccountNonLocked dựa trên trạng thái LOCKED), tài khoản có đang hoạt động không (isEnabled dựa trên trạng thái ACTIVE). Nếu bất kỳ kiểm tra nào thất bại, một ngoại lệ BadCredentialsException hoặc loại tương đương sẽ được ném ra và xử lý tập trung để trả về mã lỗi 401 Unauthorized.  
4. **Cấp phát mã Token bảo mật:** Sau khi xác thực thành công, thông tin người dùng được trích xuất. Hệ thống đưa các thông tin định danh cốt lõi bao gồm userId, fullName, và danh sách roles vào phần Extra Claims của JWT Access Token. Quyết định kiến trúc này giúp các microservice hoặc các bộ lọc tiếp theo có thể giải mã và sử dụng trực tiếp thông tin quyền hạn từ mã Token mà không cần phải thực hiện thêm bất kỳ truy vấn I/O nào vào cơ sở dữ liệu, giúp tối ưu hóa đáng kể hiệu năng hệ thống.

### **2.3. Chi Tiết Triển Khai Mã Nguồn (Source Code Implementation)**

**File: RoleName.java**

`package com.kiotviet.enums;`

`public enum RoleName {`  
    `ROLE_OWNER,`  
    `ROLE_MANAGER,`  
    `ROLE_CASHIER,`  
    `ROLE_WAREHOUSE_STAFF`  
`}`

**File: UserStatus.java**

`package com.kiotviet.enums;`

`public enum UserStatus {`  
    `ACTIVE,`  
    `INACTIVE,`  
    `LOCKED`  
`}`

**File: Role.java**

`package com.kiotviet.entity;`

`import com.kiotviet.enums.RoleName;`  
`import jakarta.persistence.*;`  
`import lombok.*;`

`@Entity`  
`@Table(name = "roles")`  
`@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`  
`public class Role {`

    `@Id`  
    `@GeneratedValue(strategy = GenerationType.IDENTITY)`  
    `private Long id;`

    `@Enumerated(EnumType.STRING)`  
    `@Column(name = "name", nullable = false, unique = true, length = 30)`  
    `private RoleName name;`

    `@Column(name = "description", length = 200)`  
    `private String description;`  
`}`

**File: User.java**

`package com.kiotviet.entity;`

`import com.kiotviet.enums.UserStatus;`  
`import jakarta.persistence.*;`  
`import lombok.*;`  
`import org.hibernate.annotations.SQLDelete;`  
`import org.hibernate.annotations.SQLRestriction;`  
`import org.springframework.security.core.GrantedAuthority;`  
`import org.springframework.security.core.authority.SimpleGrantedAuthority;`  
`import org.springframework.security.core.userdetails.UserDetails;`

`import java.util.Collection;`  
`import java.util.HashSet;`  
`import java.util.Set;`  
`import java.util.stream.Collectors;`

`@Entity`  
`@Table(name = "users", indexes = {`  
        `@Index(name = "idx_user_username", columnList = "username")`  
`})`  
`@SQLDelete(sql = "UPDATE users SET is_deleted = true WHERE id = ?")`  
`@SQLRestriction("is_deleted = false")`  
`@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`  
`public class User extends BaseEntity implements UserDetails {`

    `@Column(name = "username", nullable = false, unique = true, length = 50)`  
    `private String username;`

    `@Column(name = "password_hash", nullable = false)`  
    `private String passwordHash;`

    `@Column(name = "full_name", nullable = false, length = 200)`  
    `private String fullName;`

    `@Column(name = "email", length = 100)`  
    `private String email;`

    `@Column(name = "phone", length = 20)`  
    `private String phone;`

    `@Enumerated(EnumType.STRING)`  
    `@Column(name = "status", nullable = false, length = 20)`  
    `@Builder.Default`  
    `private UserStatus status = UserStatus.ACTIVE;`

    `@ManyToMany(fetch = FetchType.EAGER)`  
    `@JoinTable(`  
            `name = "user_roles",`  
            `joinColumns = @JoinColumn(name = "user_id"),`  
            `inverseJoinColumns = @JoinColumn(name = "role_id")`  
    `)`  
    `@Builder.Default`  
    `private Set<Role> roles = new HashSet<>();`

    `@Override`  
    `public Collection<? extends GrantedAuthority> getAuthorities() {`  
        `return roles.stream()`  
                `.map(role -> new SimpleGrantedAuthority(role.getName().name()))`  
                `.collect(Collectors.toSet());`  
    `}`

    `@Override`  
    `public String getPassword() {`  
        `return passwordHash;`  
    `}`

    `@Override`  
    `public boolean isAccountNonExpired() {`  
        `return true;`  
    `}`

    `@Override`  
    `public boolean isAccountNonLocked() {`  
        `return status != UserStatus.LOCKED;`  
    `}`

    `@Override`  
    `public boolean isCredentialsNonExpired() {`  
        `return true;`  
    `}`

    `@Override`  
    `public boolean isEnabled() {`  
        `return status == UserStatus.ACTIVE;`  
    `}`  
`}`

**File: UserRepository.java**

`package com.kiotviet.repository;`

`import com.kiotviet.entity.User;`  
`import org.springframework.data.jpa.repository.JpaRepository;`  
`import org.springframework.stereotype.Repository;`

`import java.util.Optional;`

`@Repository`  
`public interface UserRepository extends JpaRepository<User, Long> {`  
    `Optional<User> findByUsername(String username);`  
    `boolean existsByUsername(String username);`  
    `boolean existsByEmail(String email);`  
`}`

**File: RoleRepository.java**

`package com.kiotviet.repository;`

`import com.kiotviet.entity.Role;`  
`import com.kiotviet.enums.RoleName;`  
`import org.springframework.data.jpa.repository.JpaRepository;`  
`import org.springframework.stereotype.Repository;`

`import java.util.Optional;`

`@Repository`  
`public interface RoleRepository extends JpaRepository<Role, Long> {`  
    `Optional<Role> findByName(RoleName name);`  
`}`

**File: UserDetailsServiceImpl.java**

`package com.kiotviet.security;`

`import com.kiotviet.repository.UserRepository;`  
`import lombok.RequiredArgsConstructor;`  
`import org.springframework.security.core.userdetails.UserDetails;`  
`import org.springframework.security.core.userdetails.UserDetailsService;`  
`import org.springframework.security.core.userdetails.UsernameNotFoundException;`  
`import org.springframework.stereotype.Service;`  
`import org.springframework.transaction.annotation.Transactional;`

`@Service`  
`@RequiredArgsConstructor`  
`public class UserDetailsServiceImpl implements UserDetailsService {`

    `private final UserRepository userRepository;`

    `@Override`  
    `@Transactional(readOnly = true)`  
    `public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {`  
        `return userRepository.findByUsername(username)`  
                `.orElseThrow(() -> new UsernameNotFoundException(`  
                        `"Không tìm thấy người dùng: " + username));`  
    `}`  
`}`

**File: LoginRequest.java**

`package com.kiotviet.dto.request;`

`import jakarta.validation.constraints.NotBlank;`  
`import lombok.Data;`

`@Data`  
`public class LoginRequest {`  
    `@NotBlank(message = "Tên đăng nhập không được để trống")`  
    `private String username;`

    `@NotBlank(message = "Mật khẩu không được để trống")`  
    `private String password;`  
`}`

**File: ChangePasswordRequest.java**

`package com.kiotviet.dto.request;`

`import jakarta.validation.constraints.NotBlank;`  
`import jakarta.validation.constraints.Size;`  
`import lombok.Data;`

`@Data`  
`public class ChangePasswordRequest {`

    `@NotBlank(message = "Mật khẩu cũ không được để trống")`  
    `private String oldPassword;`

    `@NotBlank(message = "Mật khẩu mới không được để trống")`  
    `@Size(min = 8, message = "Mật khẩu mới phải có ít nhất 8 ký tự")`  
    `private String newPassword;`  
`}`

**File: AuthResponse.java**

`package com.kiotviet.dto.response;`

`import lombok.Builder;`  
`import lombok.Data;`

`import java.util.Set;`

`@Data`  
`@Builder`  
`public class AuthResponse {`  
    `private String accessToken;`  
    `private String refreshToken;`  
    `private long expiresIn;       // seconds`  
    `private UserInfo userInfo;`

    `@Data`  
    `@Builder`  
    `public static class UserInfo {`  
        `private Long id;`  
        `private String username;`  
        `private String fullName;`  
        `private String email;`  
        `private Set<String> roles;`  
    `}`  
`}`

**File: AuthService.java**

`package com.kiotviet.service;`

`import com.kiotviet.dto.request.ChangePasswordRequest;`  
`import com.kiotviet.dto.request.LoginRequest;`  
`import com.kiotviet.dto.response.AuthResponse;`  
`import com.kiotviet.entity.User;`  
`import com.kiotviet.exception.BusinessException;`  
`import com.kiotviet.repository.UserRepository;`  
`import com.kiotviet.security.JwtUtil;`  
`import lombok.RequiredArgsConstructor;`  
`import lombok.extern.slf4j.Slf4j;`  
`import org.springframework.security.authentication.AuthenticationManager;`  
`import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;`  
`import org.springframework.security.core.Authentication;`  
`import org.springframework.security.core.context.SecurityContextHolder;`  
`import org.springframework.security.crypto.password.PasswordEncoder;`  
`import org.springframework.stereotype.Service;`  
`import org.springframework.transaction.annotation.Transactional;`

`import java.util.Map;`  
`import java.util.stream.Collectors;`

`@Slf4j`  
`@Service`  
`@RequiredArgsConstructor`  
`public class AuthService {`

    `private final AuthenticationManager authenticationManager;`  
    `private final UserRepository userRepository;`  
    `private final JwtUtil jwtUtil;`  
    `private final PasswordEncoder passwordEncoder;`

    `@Transactional(readOnly = true)`  
    `public AuthResponse login(LoginRequest request) {`  
        `Authentication auth = authenticationManager.authenticate(`  
                `new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())`  
        `);`

        `User user = (User) auth.getPrincipal();`

        `Map<String, Object> extraClaims = Map.of(`  
                `"userId", user.getId(),`  
                `"fullName", user.getFullName(),`  
                `"roles", user.getAuthorities().stream()`  
                        `.map(a -> a.getAuthority())`  
                        `.collect(Collectors.toList())`  
        `);`

        `String accessToken = jwtUtil.generateToken(user, extraClaims);`  
        `String refreshToken = jwtUtil.generateRefreshToken(user);`

        `log.info("User logged in: {}", user.getUsername());`

        `return AuthResponse.builder()`  
                `.accessToken(accessToken)`  
                `.refreshToken(refreshToken)`  
                `.expiresIn(86400L) // 24h`  
                `.userInfo(AuthResponse.UserInfo.builder()`  
                        `.id(user.getId())`  
                        `.username(user.getUsername())`  
                        `.fullName(user.getFullName())`  
                        `.email(user.getEmail())`  
                        `.roles(user.getAuthorities().stream()`  
                                `.map(a -> a.getAuthority())`  
                                `.collect(Collectors.toSet()))`  
                        `.build())`  
                `.build();`  
    `}`

    `@Transactional(readOnly = true)`  
    `public AuthResponse refresh(String refreshToken) {`  
        `String username = jwtUtil.extractUsername(refreshToken);`  
        `User user = userRepository.findByUsername(username)`  
                `.orElseThrow(() -> new BusinessException("Token không hợp lệ"));`

        `if (!jwtUtil.validateToken(refreshToken, user)) {`  
            `throw new BusinessException("Refresh token đã hết hạn hoặc không hợp lệ");`  
        `}`

        `Map<String, Object> extraClaims = Map.of(`  
                `"userId", user.getId(),`  
                `"fullName", user.getFullName()`  
        `);`  
        `String newAccessToken = jwtUtil.generateToken(user, extraClaims);`

        `return AuthResponse.builder()`  
                `.accessToken(newAccessToken)`  
                `.refreshToken(refreshToken)`  
                `.expiresIn(86400L)`  
                `.build();`  
    `}`

    `@Transactional`  
    `public void changePassword(ChangePasswordRequest request) {`  
        `String username = SecurityContextHolder.getContext().getAuthentication().getName();`  
        `User user = userRepository.findByUsername(username)`  
                `.orElseThrow(() -> new BusinessException("Người dùng không tồn tại"));`

        `if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {`  
            `throw new BusinessException("Mật khẩu cũ không đúng");`  
        `}`  
        `if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {`  
            `throw new BusinessException("Mật khẩu mới phải khác mật khẩu cũ");`  
        `}`

        `user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));`  
        `userRepository.save(user);`  
        `log.info("Password changed for user: {}", username);`  
    `}`

    `@Transactional(readOnly = true)`  
    `public AuthResponse.UserInfo getCurrentUser() {`  
        `String username = SecurityContextHolder.getContext().getAuthentication().getName();`  
        `User user = userRepository.findByUsername(username)`  
                `.orElseThrow(() -> new BusinessException("Người dùng không tồn tại"));`

        `return AuthResponse.UserInfo.builder()`  
                `.id(user.getId())`  
                `.username(user.getUsername())`  
                `.fullName(user.getFullName())`  
                `.email(user.getEmail())`  
                `.roles(user.getAuthorities().stream()`  
                        `.map(a -> a.getAuthority())`  
                        `.collect(Collectors.toSet()))`  
                `.build();`  
    `}`  
`}`

**File: AuthController.java**

`package com.kiotviet.controller;`

`import com.kiotviet.dto.request.ChangePasswordRequest;`  
`import com.kiotviet.dto.request.LoginRequest;`  
`import com.kiotviet.dto.response.ApiResponse;`  
`import com.kiotviet.dto.response.AuthResponse;`  
`import com.kiotviet.service.AuthService;`  
`import io.swagger.v3.oas.annotations.Operation;`  
`import io.swagger.v3.oas.annotations.security.SecurityRequirements;`  
`import io.swagger.v3.oas.annotations.tags.Tag;`  
`import jakarta.validation.Valid;`  
`import lombok.RequiredArgsConstructor;`  
`import org.springframework.http.ResponseEntity;`  
`import org.springframework.web.bind.annotation.*;`

`@RestController`  
`@RequestMapping("/api/v1/auth")`  
`@RequiredArgsConstructor`  
`@Tag(name = "Auth", description = "Xác thực và phân quyền")`  
`public class AuthController {`

    `private final AuthService authService;`

    `@PostMapping("/login")`  
    `@SecurityRequirements`  
    `@Operation(summary = "Đăng nhập — trả về JWT access token và refresh token")`  
    `public ResponseEntity<ApiResponse<AuthResponse>> login(`  
            `@Valid @RequestBody LoginRequest request) {`  
        `return ResponseEntity.ok(ApiResponse.success(authService.login(request)));`  
    `}`

    `@PostMapping("/refresh")`  
    `@SecurityRequirements`  
    `@Operation(summary = "Lấy access token mới từ refresh token")`  
    `public ResponseEntity<ApiResponse<AuthResponse>> refresh(`  
            `@RequestParam String refreshToken) {`  
        `return ResponseEntity.ok(ApiResponse.success(authService.refresh(refreshToken)));`  
    `}`

    `@GetMapping("/me")`  
    `@Operation(summary = "Thông tin người dùng hiện tại")`  
    `public ResponseEntity<ApiResponse<AuthResponse.UserInfo>> getCurrentUser() {`  
        `return ResponseEntity.ok(ApiResponse.success(authService.getCurrentUser()));`  
    `}`

    `@PutMapping("/me/password")`  
    `@Operation(summary = "Đổi mật khẩu")`  
    `public ResponseEntity<ApiResponse<Void>> changePassword(`  
            `@Valid @RequestBody ChangePasswordRequest request) {`  
        `authService.changePassword(request);`  
        `return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công"));`  
    `}`  
`}`

## **3\. Phân Hệ Nhật Ký Hệ Thống (Audit Logging Module)**

Phân hệ Audit Log chịu trách nhiệm lưu trữ lịch sử biến động dữ liệu và hành động của nhân viên để phục vụ công tác hậu kiểm, điều tra sự cố rủi ro và tuân thủ bảo mật.

### **3.1. Phân Tích Kiến Trúc Bất Đồng Bộ (Asynchronous Architecture)**

Trong các hệ thống Enterprise lớn, việc ghi nhận log nếu gộp chung vào Transaction nghiệp vụ chính sẽ phát sinh những hệ lụy nghiêm trọng về mặt hiệu năng và độ tin cậy. Dưới đây là bảng so sánh phân tích hai hướng tiếp cận chính được cân nhắc khi thiết kế hệ thống:

| Tiêu Chí So Sánh | Cách 1: Đồng Bộ (Trong cùng Transaction chính) | Cách 2: Bất Đồng Bộ (Thread riêng, sau khi Commit) |
| :---- | :---- | :---- |
| **Luồng Xử Lý (Workflow)** | Nghiệp vụ chính (Trừ kho, Tạo đơn) → Ghi Audit Log → Commit toàn bộ. | Nghiệp vụ chính kết thúc và Commit → Kích hoạt Thread độc lập để ghi Audit Log. |
| **Hiệu Năng Phản Hồi** | Chậm. Luồng chính bị nghẽn (Block) bởi các thao tác I/O bổ sung của Audit Log. | Rất nhanh. Phản hồi cho client ngay sau khi xong nghiệp vụ, không đợi ghi log. |
| **Cô Lập Lỗi (Fault Isolation)** | Kém. Nếu Audit Log lỗi (đầy ổ đĩa, nghẽn DB log), toàn bộ đơn hàng bị Rollback → Mất doanh thu. | Tuyệt đối. Audit log lỗi chỉ rollback chính nó, nghiệp vụ lõi đã thành công hoàn toàn. |
| **Giải Pháp Triển Khai** | Mặc định gọi tuần tự. | Sử dụng kết hợp annotation @Async và chiến lược lan truyền giao dịch Propagation.REQUIRES\_NEW. |

**Bản Chất Kỹ Thuật của Sự Kết Hợp @Async và Propagation.REQUIRES\_NEW:**

* @Async ra lệnh cho Spring Framework đẩy tác vụ này sang một Thread riêng biệt thuộc Thread Pool quản lý, giải phóng tức thì luồng chính của client.  
* Propagation.REQUIRES\_NEW thiết lập một ranh giới Transaction hoàn toàn độc lập với Transaction gọi nó. Điều này tạo một kết nối cơ sở dữ liệu mới mở riêng cho tác vụ ghi log, đảm bảo tính biệt lập tuyệt đối.  
* **An toàn tối đa trong khối catch:** Lỗi khi ghi nhận nhật ký hệ thống được bọc kín trong khối try-catch và chỉ ghi nhận vào tệp log của ứng dụng (log.error), tuyệt đối không ném ngược ngoại lệ ra ngoài, loại bỏ hoàn toàn nguy cơ làm gián đoạn Business Flow nghiệp vụ.

### **3.2. Cấu Trúc Dữ Liệu Nhật Ký (Audit Log Schema)**

Thực thể AuditLog được thiết kế theo nguyên tắc bất biến (Immutable) — chỉ cho phép thêm mới (Append-Only), nghiêm cấm cập nhật. Để đảm bảo tốc độ truy vấn phục vụ việc hiển thị lịch sử trên giao diện quản trị, ba chỉ mục chiến lược (Indexes) đã được cấu hình:

* idx\_audit\_user (trên cột user\_id): Phục vụ tra cứu nhanh các hành động do một nhân viên cụ thể thực hiện.  
* idx\_audit\_created\_at (trên cột created\_at): Phục vụ tối ưu việc lọc log theo khoảng thời gian.  
* idx\_audit\_resource (chỉ mục tổng hợp trên hai cột resource\_type, resource\_id): Hỗ trợ truy vết lịch sử biến động của một đối tượng cụ thể (Ví dụ: xem lại toàn bộ các lần thay đổi của một Đơn hàng hay Sản phẩm).

### **3.3. Chi Tiết Triển Khai Mã Nguồn (Source Code Implementation)**

**File: AuditLog.java**

`package com.kiotviet.entity;`

`import jakarta.persistence.*;`  
`import lombok.*;`  
`import org.springframework.data.annotation.CreatedDate;`  
`import org.springframework.data.jpa.domain.support.AuditingEntityListener;`

`import java.time.LocalDateTime;`

`@Entity`  
`@Table(name = "audit_logs", indexes = {`  
        `@Index(name = "idx_audit_user", columnList = "user_id"),`  
        `@Index(name = "idx_audit_created_at", columnList = "created_at"),`  
        `@Index(name = "idx_audit_resource", columnList = "resource_type,resource_id")`  
`})`  
`@EntityListeners(AuditingEntityListener.class)`  
`@Getter`  
`@NoArgsConstructor`  
`@AllArgsConstructor`  
`@Builder`  
`public class AuditLog {`

    `@Id`  
    `@GeneratedValue(strategy = GenerationType.IDENTITY)`  
    `private Long id;`

    `@Column(name = "user_id")`  
    `private Long userId;`

    `@Column(name = "username", length = 100)`  
    `private String username;`

    `@Column(name = "action", nullable = false, length = 50)`  
    `private String action; // CHECKOUT, COMPLETE_IMPORT, CANCEL_IMPORT, UPDATE_PRICE...`

    `@Column(name = "resource_type", length = 50)`  
    `private String resourceType; // ORDER, IMPORT_RECEIPT, PRODUCT...`

    `@Column(name = "resource_id")`  
    `private Long resourceId;`

    `@Column(name = "details", columnDefinition = "TEXT")`  
    `private String details; // JSON: {"before": {...}, "after": {...}}`

    `@Column(name = "ip_address", length = 50)`  
    `private String ipAddress;`

    `@Column(name = "user_agent", length = 500)`  
    `private String userAgent;`

    `@CreatedDate`  
    `@Column(name = "created_at", nullable = false, updatable = false)`  
    `private LocalDateTime createdAt;`  
`}`

**File: AuditLogRepository.java**

`package com.kiotviet.repository;`

`import com.kiotviet.entity.AuditLog;`  
`import org.springframework.data.jpa.repository.JpaRepository;`  
`import org.springframework.stereotype.Repository;`

`@Repository`  
`public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}`

**File: AuditLogService.java**

`package com.kiotviet.service;`

`import com.kiotviet.entity.AuditLog;`  
`import com.kiotviet.repository.AuditLogRepository;`  
`import lombok.RequiredArgsConstructor;`  
`import lombok.extern.slf4j.Slf4j;`  
`import org.springframework.scheduling.annotation.Async;`  
`import org.springframework.stereotype.Service;`  
`import org.springframework.transaction.annotation.Propagation;`  
`import org.springframework.transaction.annotation.Transactional;`

`@Slf4j`  
`@Service`  
`@RequiredArgsConstructor`  
`public class AuditLogService {`

    `private final AuditLogRepository auditLogRepository;`

    `@Async`  
    `@Transactional(propagation = Propagation.REQUIRES_NEW)`  
    `public void log(Long userId,`  
                    `String username,`  
                    `String action,`  
                    `String resourceType,`  
                    `Long resourceId,`  
                    `String details) {`  
        `try {`  
            `AuditLog auditLog = AuditLog.builder()`  
                    `.userId(userId)`  
                    `.username(username)`  
                    `.action(action)`  
                    `.resourceType(resourceType)`  
                    `.resourceId(resourceId)`  
                    `.details(details)`  
                    `.build();`  
            `auditLogRepository.save(auditLog);`  
        `} catch (Exception e) {`  
            `log.error("Failed to write audit log: action={}, resource={}/{}. Error: {}",`  
                    `action, resourceType, resourceId, e.getMessage());`  
        `}`  
    `}`  
`}`

## **4\. Tổng Hợp Quy Tắc Phát Triển Hệ Thống (Senior Developer Practices)**

Nhằm duy trì tính đồng nhất và chất lượng mã nguồn ở mức cao nhất, đội ngũ phát triển cần tuân thủ nghiêm ngặt các chỉ dẫn kỹ thuật sau:

* **Tối ưu hóa các thao tác chỉ đọc:** Sử dụng @Transactional(readOnly \= true) cho tất cả các phương thức truy vấn thông tin. Việc này giúp tắt cơ chế kiểm tra biến động thực thể (Dirty Checking) của Hibernate, tiết kiệm tài nguyên bộ nhớ và tăng tốc độ thực thi.  
* **Sử dụng mẫu kiến trúc lưu trữ JSON:** Trường details trong bảng AuditLog được cấu hình kiểu dữ liệu TEXT để lưu trữ linh hoạt chuỗi JSON chứa trạng thái đối tượng trước và sau khi thay đổi ({"before": {...}, "after": {...}}). Cách thiết kế schema này giúp hệ thống dễ dàng thích ứng với sự thay đổi cấu trúc của các thực thể nghiệp vụ mà không cần chỉnh sửa bảng nhật ký.  
* **Xử lý đồng bộ hóa giao dịch nâng cao:** Đối với các hành động đòi hỏi độ chính xác tuyệt đối, khuyến khích sử dụng mẫu thiết kế @TransactionalEventListener(phase=TransactionPhase.AFTER\_COMMIT) để đảm bảo chỉ khi luồng giao dịch nghiệp vụ chính commit thành công thì sự kiện ghi nhận nhật ký hệ thống mới được kích hoạt.