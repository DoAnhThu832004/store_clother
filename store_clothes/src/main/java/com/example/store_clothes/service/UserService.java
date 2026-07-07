package com.example.store_clothes.service;

import com.example.store_clothes.dto.request.CreateUserRequest;
import com.example.store_clothes.dto.request.UpdateUserRequest;
import com.example.store_clothes.dto.response.UserDetailResponse;
import com.example.store_clothes.dto.response.UserResponse;
import com.example.store_clothes.entity.Role;
import com.example.store_clothes.entity.User;
import com.example.store_clothes.enums.RoleName;
import com.example.store_clothes.enums.UserStatus;
import com.example.store_clothes.exception.BusinessException;
import com.example.store_clothes.exception.EntityNotFoundException;
import com.example.store_clothes.repository.RoleRepository;
import com.example.store_clothes.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UserService — Nghiệp vụ quản lý nhân viên (5 APIs: USR-01 đến USR-05).
 *
 * ====================================================================
 * THIẾT KẾ BẢO MẬT QUAN TRỌNG — ĐỌC TRƯỚC KHI SỬA:
 * ====================================================================
 *
 * [1] KHÔNG cho OWNER tạo OWNER khác qua USR-01:
 *     Nếu muốn thêm OWNER → phải thao tác trực tiếp DB (intentional friction).
 *     Tránh privilege escalation: Cashier bị hack, tạo account OWNER mới.
 *
 * [2] KHÔNG cho tự xóa mình (USR-05):
 *     Tránh lock-out vô tình. OWNER A vô tình click "Xóa" tài khoản chính mình
 *     → không thể login lại nếu không còn OWNER nào khác.
 *
 * [3] Soft Delete với rename UNIQUE fields (USR-05):
 *     username, email append "_deleted_UNIX_TIMESTAMP" trước khi set is_deleted=true.
 *     @SQLDelete đã xử lý trong entity. Gọi repository.delete(user) là đủ.
 *
 * [4] Optimistic Lock KHÔNG áp dụng cho User:
 *     User entity không có @Version. Lý do: User ít bị sửa đồng thời.
 *     Admin (OWNER) thường là 1 người → không cần.
 *     Nếu cần → thêm @Version vào User entity tương tự Customer.
 * ====================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    // =========================================================================
    // USR-01 — TẠO NHÂN VIÊN MỚI
    // =========================================================================

    /**
     * Tạo tài khoản nhân viên mới.
     *
     * Chỉ OWNER gọi được endpoint này.
     * Không cho phép tạo OWNER khác qua đây.
     *
     * @param request     Thông tin nhân viên mới
     * @param creatorId   ID người tạo (từ SecurityContext)
     * @param creatorName Username người tạo
     * @return UserResponse của nhân viên vừa tạo
     * @throws BusinessException Nếu username đã tồn tại, email đã dùng, hoặc cố tạo OWNER
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request,
                                   Long creatorId,
                                   String creatorName) {

        // -------------------------------------------------------
        // VALIDATE 1: Không cho tạo OWNER qua API này
        // -------------------------------------------------------
        if (request.getRoleName() == RoleName.ROLE_OWNER) {
            throw new BusinessException(
                    "Không được phép tạo tài khoản OWNER qua API này. " +
                    "Liên hệ System Administrator để thực hiện trực tiếp trên DB.");
        }

        // -------------------------------------------------------
        // VALIDATE 2: Username chưa tồn tại
        // Dùng existsByUsername() (có @SQLRestriction) để check active users.
        // Username đã xóa đã được đổi tên → không block tạo mới.
        // -------------------------------------------------------
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username '" + request.getUsername() + "' đã tồn tại trong hệ thống.");
        }

        // -------------------------------------------------------
        // VALIDATE 3: Email chưa tồn tại (nếu cung cấp)
        // -------------------------------------------------------
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email '" + request.getEmail() + "' đã được sử dụng.");
        }

        // -------------------------------------------------------
        // Load Role từ DB
        // -------------------------------------------------------
        Role role = roleRepository.findByName(request.getRoleName())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Vai trò không tồn tại: " + request.getRoleName()));

        // -------------------------------------------------------
        // Tạo User entity — BCrypt strength 12
        //
        // 💡 Senior Note — Tại sao BCrypt strength 12 (không phải 10)?
        // Strength 10 (default) = ~100ms/hash trên hardware hiện đại.
        // Strength 12 = ~400ms/hash — vẫn OK cho login (user không notice)
        // nhưng khiến brute-force attack chậm hơn 4x.
        // Trade-off: Tạo user chậm hơn (chấp nhận được vì ít làm).
        // -------------------------------------------------------
        User newUser = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(role)))
                .build();

        User savedUser = userRepository.save(newUser);

        log.info("Tạo nhân viên mới: username={}, role={}, by={}", 
                savedUser.getUsername(), request.getRoleName(), creatorName);

        // Ghi AuditLog bất đồng bộ
        String details = String.format(
                "{\"newUserId\": %d, \"username\": \"%s\", \"role\": \"%s\"}",
                savedUser.getId(), savedUser.getUsername(), request.getRoleName()
        );
        auditLogService.log(creatorId, creatorName, "CREATE_USER", "USER", savedUser.getId(), details);

        return mapToUserResponse(savedUser);
    }

    // =========================================================================
    // USR-02 — DANH SÁCH NHÂN VIÊN
    // =========================================================================

    /**
     * Lấy danh sách nhân viên với filter keyword + roleName + phân trang.
     *
     * @param keyword  Tìm theo username HOẶC fullName (null = tất cả)
     * @param roleName Filter theo vai trò (null = tất cả)
     * @param pageable Phân trang
     * @return Page<UserResponse> — KHÔNG chứa password
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(String keyword, RoleName roleName, Pageable pageable) {
        // Normalize keyword: blank → null để JPQL IS NULL check hoạt động
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        return userRepository.findUsersWithFilter(normalizedKeyword, roleName, pageable)
                .map(this::mapToUserResponse);
    }

    // =========================================================================
    // USR-03 — CHI TIẾT NHÂN VIÊN
    // =========================================================================

    /**
     * Lấy chi tiết nhân viên kèm thống kê hoạt động.
     *
     * @param userId ID nhân viên cần xem
     * @return UserDetailResponse với thông tin + thống kê
     * @throws EntityNotFoundException Nếu không tìm thấy user
     */
    @Transactional(readOnly = true)
    public UserDetailResponse getUserDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy nhân viên với ID: " + userId));

        // Query thống kê từ AuditLog — 2 query riêng, nhẹ vì có index
        long totalOrdersCreated   = userRepository.countOrdersCreatedByUser(userId);
        long totalImportsCreated  = userRepository.countImportsCreatedByUser(userId);

        return UserDetailResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .roles(user.getRoles().stream()
                        .map(r -> r.getName().name())
                        .collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .totalOrdersCreated(totalOrdersCreated)
                .totalImportsCreated(totalImportsCreated)
                .build();
    }

    // =========================================================================
    // USR-04 — CẬP NHẬT THÔNG TIN & ĐỔI PHÂN QUYỀN
    // =========================================================================

    /**
     * Cập nhật thông tin và/hoặc đổi role nhân viên.
     *
     * Chỉ OWNER gọi được endpoint này.
     *
     * Logic đổi role:
     * - Xóa toàn bộ role cũ, gán role mới.
     * - KHÔNG cho đổi role của chính mình (id == currentUserId).
     * - KHÔNG cho đổi role của OWNER khác thành lower nếu chỉ còn 1 OWNER active.
     *
     * 💡 Senior Note — Tại sao KHÔNG cho tự đổi role của mình?
     * (1) OWNER A vô tình đổi bản thân thành CASHIER → mất quyền quản trị.
     * (2) Tạo lỗ hổng audit: "Ai đổi role của A?" — chính A tự đổi → khó trace.
     * (3) Security principle: Không được tự cấp/thu hồi quyền của chính mình.
     *     Phải có peer approval hoặc upper-level approval.
     *
     * @param targetUserId  ID nhân viên cần cập nhật
     * @param request       Thông tin cần cập nhật
     * @param currentUserId ID người đang thực hiện (OWNER)
     * @param currentUsername Username người đang thực hiện
     * @return UserResponse sau cập nhật
     */
    @Transactional
    public UserResponse updateUser(Long targetUserId,
                                    UpdateUserRequest request,
                                    Long currentUserId,
                                    String currentUsername) {

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy nhân viên với ID: " + targetUserId));

        // -------------------------------------------------------
        // VALIDATE: Không cho đổi role của chính mình
        // -------------------------------------------------------
        if (request.getRoleName() != null && targetUserId.equals(currentUserId)) {
            throw new BusinessException(
                    "Không được phép thay đổi vai trò của chính mình. " +
                    "Yêu cầu OWNER khác thực hiện thao tác này.");
        }

        // -------------------------------------------------------
        // CẬP NHẬT THÔNG TIN (chỉ nếu không null)
        // -------------------------------------------------------
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            targetUser.setFullName(request.getFullName());
        }

        if (request.getEmail() != null) {
            // Validate email không trùng với user KHÁC
            if (!request.getEmail().isBlank()
                    && userRepository.existsByEmailAndIdNot(request.getEmail(), targetUserId)) {
                throw new BusinessException("Email '" + request.getEmail() + "' đã được sử dụng bởi nhân viên khác.");
            }
            targetUser.setEmail(request.getEmail().isBlank() ? null : request.getEmail());
        }

        if (request.getPhone() != null) {
            targetUser.setPhone(request.getPhone().isBlank() ? null : request.getPhone());
        }

        if (request.getStatus() != null) {
            targetUser.setStatus(request.getStatus());
        }

        // -------------------------------------------------------
        // ĐỔI ROLE (nếu cung cấp)
        // -------------------------------------------------------
        String oldRole = null;
        String newRole = null;

        if (request.getRoleName() != null) {
            // Lấy role hiện tại để ghi vào AuditLog
            oldRole = targetUser.getRoles().stream()
                    .map(r -> r.getName().name())
                    .collect(Collectors.joining(", "));

            // Không cho đổi OWNER cuối cùng sang role khác
            boolean isTargetOwner = targetUser.getRoles().stream()
                    .anyMatch(r -> r.getName() == RoleName.ROLE_OWNER);
            if (isTargetOwner && request.getRoleName() != RoleName.ROLE_OWNER) {
                long activeOwnerCount = userRepository.countByRoleNameAndStatus(
                        RoleName.ROLE_OWNER, UserStatus.ACTIVE);
                if (activeOwnerCount <= 1) {
                    throw new BusinessException(
                            "Không thể hạ cấp OWNER duy nhất còn lại. " +
                            "Hệ thống cần ít nhất 1 OWNER active.");
                }
            }

            Role newRoleEntity = roleRepository.findByName(request.getRoleName())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Vai trò không tồn tại: " + request.getRoleName()));

            // Xóa toàn bộ role cũ, gán role mới
            targetUser.getRoles().clear();
            targetUser.getRoles().add(newRoleEntity);
            newRole = request.getRoleName().name();
        }

        User updatedUser = userRepository.save(targetUser);

        log.info("Cập nhật nhân viên: targetId={}, by={}", targetUserId, currentUsername);

        // Ghi AuditLog bất đồng bộ (chỉ khi đổi role)
        if (newRole != null) {
            String details = String.format(
                    "{\"targetUserId\": %d, \"oldRole\": \"%s\", \"newRole\": \"%s\"}",
                    targetUserId, oldRole, newRole
            );
            auditLogService.log(currentUserId, currentUsername,
                    "UPDATE_USER_ROLE", "USER", targetUserId, details);
        }

        return mapToUserResponse(updatedUser);
    }

    // =========================================================================
    // USR-05 — XÓA MỀM (KHÓA TÀI KHOẢN)
    // =========================================================================

    /**
     * Soft Delete tài khoản nhân viên.
     *
     * Khi gọi repository.delete(user) → Hibernate sẽ thực thi @SQLDelete:
     * UPDATE users SET is_deleted=true, status='LOCKED',
     * username=CONCAT(username,'_deleted_',UNIX_TIMESTAMP()),
     * email=CONCAT(IFNULL(email,''), '_deleted_',UNIX_TIMESTAMP()) WHERE id=?
     *
     * 💡 Senior Note — Tại sao phải kiểm tra còn OWNER cuối cùng trước khi xóa?
     * Nếu xóa OWNER cuối cùng còn lại:
     * (1) Không còn ai có quyền tạo user, đổi role, xóa user.
     * (2) Hệ thống POS bị "dead lock" hoàn toàn — không thể quản trị.
     * (3) Phục hồi đòi hỏi access trực tiếp DB (down-time, security risk).
     * Business rule: LUÔN phải có ít nhất 1 OWNER active.
     *
     * @param targetUserId  ID nhân viên cần xóa
     * @param currentUserId ID người đang thực hiện (OWNER)
     * @param currentUsername Username người đang thực hiện
     * @throws BusinessException Nếu tự xóa mình hoặc xóa OWNER cuối cùng
     */
    @Transactional
    public void deleteUser(Long targetUserId, Long currentUserId, String currentUsername) {

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy nhân viên với ID: " + targetUserId));

        // -------------------------------------------------------
        // VALIDATE 1: Không cho tự xóa chính mình
        // -------------------------------------------------------
        if (targetUserId.equals(currentUserId)) {
            throw new BusinessException("Không được phép xóa tài khoản của chính mình.");
        }

        // -------------------------------------------------------
        // VALIDATE 2: Không cho xóa OWNER cuối cùng còn lại
        //
        // 💡 Senior Note giải thích ở trên.
        // -------------------------------------------------------
        boolean isTargetOwner = targetUser.getRoles().stream()
                .anyMatch(r -> r.getName() == RoleName.ROLE_OWNER);
        if (isTargetOwner) {
            long activeOwnerCount = userRepository.countByRoleNameAndStatus(
                    RoleName.ROLE_OWNER, UserStatus.ACTIVE);
            if (activeOwnerCount <= 1) {
                throw new BusinessException(
                        "Không thể xóa OWNER duy nhất còn lại trong hệ thống. " +
                        "Phải có ít nhất 1 OWNER active để quản trị hệ thống.");
            }
        }

        // -------------------------------------------------------
        // SOFT DELETE — @SQLDelete trên User entity sẽ xử lý:
        // UPDATE users SET is_deleted=true, status='LOCKED',
        // username=CONCAT(username,'_deleted_',UNIX_TIMESTAMP()),
        // email=CONCAT(IFNULL(email,''),'_deleted_',UNIX_TIMESTAMP())
        // WHERE id=?
        // -------------------------------------------------------
        userRepository.delete(targetUser);

        log.info("Soft delete nhân viên: targetId={}, username={}, by={}",
                targetUserId, targetUser.getUsername(), currentUsername);

        // Ghi AuditLog bất đồng bộ
        String details = String.format(
                "{\"deletedUserId\": %d, \"username\": \"%s\", \"roles\": \"%s\"}",
                targetUserId,
                targetUser.getUsername(),
                targetUser.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.joining(", "))
        );
        auditLogService.log(currentUserId, currentUsername, "DELETE_USER", "USER", targetUserId, details);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Map User entity → UserResponse DTO.
     * KHÔNG include passwordHash, refreshToken.
     */
    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .roles(user.getRoles().stream()
                        .map(r -> r.getName().name())
                        .collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
