package com.example.store_clothes.service;

import com.example.store_clothes.dto.request.TenantRegisterRequest;
import com.example.store_clothes.dto.response.TenantRegisterResponse;
import com.example.store_clothes.entity.Category;
import com.example.store_clothes.entity.Role;
import com.example.store_clothes.entity.Tenant;
import com.example.store_clothes.entity.User;
import com.example.store_clothes.entity.CategoryStatus;
import com.example.store_clothes.enums.RoleName;
import com.example.store_clothes.enums.TenantStatus;
import com.example.store_clothes.exception.BusinessException;
import com.example.store_clothes.multitenancy.TenantContextHolder;
import com.example.store_clothes.repository.CategoryRepository;
import com.example.store_clothes.repository.RoleRepository;
import com.example.store_clothes.repository.TenantRepository;
import com.example.store_clothes.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;

/**
 * TenantOnboardingService — Xử lý toàn bộ luồng đăng ký cửa hàng mới.
 *
 * SỬ DỤNG PROGRAMMATIC TRANSACTION (@Transactional bị gỡ bỏ):
 * Trong Hibernate 6 với Discriminator Multi-Tenancy, Tenant Identifier 
 * được lấy và cache tại thời điểm mở Session (đầu Transaction).
 * Nếu gom chung việc tạo Tenant và tạo User vào 1 Transaction, 
 * Hibernate sẽ cache tenant_id = 0 (vì lúc mở transaction chưa có tenant).
 * Do đó, quá trình tạo được chia làm 2 Transaction độc lập qua TransactionTemplate:
 * 1. Transaction 1: Tạo và lưu Tenant.
 * 2. Set TenantContextHolder (để session thứ 2 lấy đúng tenant_id).
 * 3. Transaction 2: Tạo User và Category mặc định.
 * 4. Nếu Transaction 2 lỗi, thực hiện Compensating Transaction (Xóa Tenant) 
 *    để đảm bảo tính nguyên vẹn dữ liệu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantOnboardingService {

    private final TenantRepository  tenantRepository;
    private final UserRepository     userRepository;
    private final CategoryRepository categoryRepository;
    private final RoleRepository     roleRepository;
    private final PasswordEncoder    passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    /**
     * Đăng ký cửa hàng mới: Tenant + OWNER + Default Category.
     *
     * @param request Thông tin đăng ký từ client
     * @return Response chứa thông tin cửa hàng vừa tạo
     * @throws BusinessException Nếu storeCode hoặc ownerUsername đã tồn tại
     */
    public TenantRegisterResponse register(TenantRegisterRequest request) {

        // =====================================================================
        // STEP 1 — Validate: Kiểm tra trùng lặp trước khi tạo bất cứ thứ gì
        // =====================================================================
        if (tenantRepository.existsByCode(request.getStoreCode())) {
            throw new BusinessException(
                "Mã cửa hàng '" + request.getStoreCode() + "' đã được sử dụng. Vui lòng chọn mã khác."
            );
        }

        // =====================================================================
        // STEP 2 — Tạo Tenant (Transaction 1)
        // =====================================================================
        Tenant tenant = transactionTemplate.execute(status -> {
            Tenant t = Tenant.builder()
                    .name(request.getStoreName())
                    .code(request.getStoreCode())
                    .status(TenantStatus.ACTIVE)
                    .contactEmail(request.getContactEmail())
                    .contactPhone(request.getContactPhone())
                    .subscriptionExpiredAt(null)
                    .build();
            return tenantRepository.save(t);
        });

        final Long tenantId = tenant.getId();
        log.info("Tenant created: id={}, code={}", tenantId, tenant.getCode());

        // =====================================================================
        // STEP 3 — Set TenantContextHolder trước khi mở Transaction 2
        // Khi mở Transaction 2, Hibernate sẽ gọi Resolver và nhận được tenantId này.
        // =====================================================================
        TenantContextHolder.setTenantId(tenantId);
        log.debug("TenantContext set for onboarding: tenantId={}", tenantId);

        try {
            // =================================================================
            // STEP 4 & 5 — Tạo User OWNER và Category (Transaction 2)
            // =================================================================
            return transactionTemplate.execute(status -> {
                Role ownerRole = roleRepository.findByName(RoleName.ROLE_OWNER)
                        .orElseThrow(() -> new IllegalStateException(
                            "ROLE_OWNER không tồn tại trong DB. Kiểm tra DataInitializer."
                        ));

                User owner = User.builder()
                        .username(request.getOwnerUsername())
                        .passwordHash(passwordEncoder.encode(request.getOwnerPassword()))
                        .fullName(request.getOwnerFullName())
                        .email(request.getOwnerEmail())
                        .phone(request.getOwnerPhone())
                        .roles(Set.of(ownerRole))
                        .build();

                userRepository.save(owner);
                log.info("OWNER user created: username={}, tenantId={}", owner.getUsername(), tenantId);

                Category defaultCategory = Category.builder()
                        .name("Chưa phân loại")
                        .slug("chua-phan-loai")
                        .status(CategoryStatus.ACTIVE)
                        .parent(null)
                        .build();

                categoryRepository.save(defaultCategory);
                log.info("Default category seeded for tenantId={}", tenantId);

                return TenantRegisterResponse.builder()
                        .storeName(tenant.getName())
                        .storeCode(tenant.getCode())
                        .ownerUsername(owner.getUsername())
                        .message(
                            "Cửa hàng \"" + tenant.getName() + "\" đã được tạo thành công! " +
                            "Đăng nhập ngay với username: " + owner.getUsername()
                        )
                        .build();
            });

        } catch (Exception e) {
            // =================================================================
            // COMPENSATING ACTION — Nếu tạo User/Category lỗi, xóa Tenant đã tạo
            // =================================================================
            log.error("Error occurred while creating User/Category for tenant {}. Rolling back Tenant creation.", tenantId, e);
            TenantContextHolder.clear(); // Clear context để xóa tenant với quyền global
            transactionTemplate.execute(status -> {
                tenantRepository.deleteById(tenantId);
                return null;
            });
            throw e;
        } finally {
            // =================================================================
            // CLEANUP — LUÔN LUÔN clear TenantContextHolder trong finally
            // =================================================================
            TenantContextHolder.clear();
            log.debug("TenantContext cleared after onboarding for tenantId={}", tenantId);
        }
    }
}
