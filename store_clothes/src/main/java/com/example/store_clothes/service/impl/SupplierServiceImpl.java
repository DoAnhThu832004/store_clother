package com.example.store_clothes.service.impl;

import com.example.store_clothes.dto.request.CreateSupplierRequest;
import com.example.store_clothes.dto.request.UpdateSupplierRequest;
import com.example.store_clothes.dto.response.SupplierResponse;
import com.example.store_clothes.entity.ImportReceipt;
import com.example.store_clothes.entity.Supplier;
import com.example.store_clothes.enums.ImportReceiptStatus;
import com.example.store_clothes.exception.DomainException;
import com.example.store_clothes.exception.EntityNotFoundException;
import com.example.store_clothes.exception.ErrorCode;
import com.example.store_clothes.repository.SupplierRepository;
import com.example.store_clothes.service.AuditLogService;
import com.example.store_clothes.service.SupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SupplierServiceImpl — Triển khai toàn bộ nghiệp vụ Nhà Cung Cấp.
 *
 * NGUYÊN TẮC:
 * 1. @Transactional đặt ở Service, không ở Controller.
 * 2. Trả về DTO, không trả Entity.
 * 3. debtAmount chỉ được hệ thống cập nhật qua ImportReceipt flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final AuditLogService auditLogService;

    // =========================================================================
    // SUP-01 — Tạo nhà cung cấp mới
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * Flow:
     * 1. Validate phone chưa được dùng bởi NCC khác (active).
     * 2. Persist với debtAmount = 0 mặc định.
     * 3. Trả về DTO.
     *
     * 💡 Senior Note — Tại sao validate phone ở Service (không chỉ @Valid)?
     * @Pattern ở DTO chỉ kiểm tra format (regex).
     * Uniqueness cần query DB → phải làm ở Service.
     * Nếu dùng @UniqueConstraint ở DB, DB sẽ ném DataIntegrityViolationException
     * → message lỗi xấu, khó map ra thông báo thân thiện.
     * → Pre-check ở Service tốt hơn: kiểm soát được message lỗi + HTTP status.
     */
    @Override
    @Transactional
    public SupplierResponse createSupplier(CreateSupplierRequest request) {
        log.info("Tạo nhà cung cấp mới: name='{}', phone='{}'", request.name(), request.phone());

        // Validate phone unique
        supplierRepository.findByPhone(request.phone()).ifPresent(existing -> {
            throw new DomainException(ErrorCode.SUPPLIER_PHONE_DUPLICATED,
                    "Số điện thoại '" + request.phone() + "' đã được sử dụng bởi nhà cung cấp khác",
                    Map.of("phone", request.phone()));
        });

        Supplier supplier = Supplier.builder()
                .name(request.name())
                .phone(request.phone())
                .email(request.email())
                .address(request.address())
                .taxCode(request.taxCode())
                .debtAmount(BigDecimal.ZERO)
                .build();

        supplier = supplierRepository.save(supplier);
        log.info("Đã tạo nhà cung cấp: id={}, name='{}'", supplier.getId(), supplier.getName());

        return SupplierResponse.from(supplier);
    }

    // =========================================================================
    // SUP-02 — Danh sách NCC phân trang
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * Query động:
     * - keyword null → không filter tên.
     * - hasDebt = true → WHERE debt_amount > 0.
     * - hasDebt = false/null → không filter theo debt.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<SupplierResponse> getSuppliers(String keyword, Boolean hasDebt, Pageable pageable) {
        log.debug("Danh sách NCC: keyword='{}', hasDebt={}, page={}", keyword, hasDebt, pageable.getPageNumber());

        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return supplierRepository.searchSuppliers(kw, hasDebt, pageable)
                .map(SupplierResponse::from);
    }

    // =========================================================================
    // SUP-03 — Chi tiết NCC kèm 5 phiếu nhập gần nhất
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * 💡 Senior Note — Tại sao 5 phiếu nhập gần nhất, không phải tất cả?
     * (1) UX: Màn hình chi tiết NCC không cần toàn bộ lịch sử (có thể hàng nghìn phiếu).
     * (2) Performance: Top-5 query với LIMIT rất nhanh, tránh load quá nhiều data.
     * (3) Nếu cần xem toàn bộ → có API riêng cho ImportReceipt với filter supplierId.
     */
    @Override
    @Transactional(readOnly = true)
    public SupplierResponse getSupplierById(Long id) {
        log.debug("Lấy chi tiết NCC id={}", id);

        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy nhà cung cấp với id: " + id));

        List<ImportReceipt> recentReceipts = supplierRepository.findTop5RecentImportReceipts(id);

        List<SupplierResponse.ImportReceiptSummary> summaries = recentReceipts.stream()
                .map(r -> SupplierResponse.ImportReceiptSummary.builder()
                        .id(r.getId())
                        .receiptCode(r.getReceiptCode())
                        .totalAmount(r.getTotalAmount())
                        .paidAmount(r.getPaidAmount())
                        .status(r.getStatus().name())
                        .createdAt(r.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return SupplierResponse.from(supplier, summaries);
    }

    // =========================================================================
    // SUP-04 — Cập nhật NCC (Optimistic Lock)
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * 💡 Senior Note — Optimistic Lock (@Version) ở Supplier:
     * Khi 2 Manager cùng cập nhật thông tin NCC:
     *   Manager A: GET supplier (version=2) → PUT (version=2) → OK → version=3
     *   Manager B: GET supplier (version=2) → PUT (version=2) → Hibernate detect 2≠3
     *   → ObjectOptimisticLockingFailureException → HTTP 409
     * Client B phải GET lại supplier (version=3) rồi mới có thể PUT.
     *
     * Không cho phép sửa debtAmount qua đây:
     * debtAmount thay đổi theo quy trình kế toán (ImportReceipt payment).
     * Nếu cho sửa tự do → mất kiểm soát kế toán, vi phạm nguyên tắc audit trail.
     */
    @Override
    @Transactional
    public SupplierResponse updateSupplier(Long id, UpdateSupplierRequest request) {
        log.info("Cập nhật NCC id={}, name='{}'", id, request.name());

        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy nhà cung cấp với id: " + id));

        // Validate phone unique (ngoại trừ chính NCC này)
        if (!request.phone().equals(supplier.getPhone())) {
            supplierRepository.findByPhoneAndIdNot(request.phone(), id).ifPresent(existing -> {
                throw new DomainException(ErrorCode.SUPPLIER_PHONE_DUPLICATED,
                        "Số điện thoại '" + request.phone() + "' đã được sử dụng bởi nhà cung cấp khác",
                        Map.of("phone", request.phone()));
            });
        }

        supplier.setName(request.name());
        supplier.setPhone(request.phone());
        supplier.setEmail(request.email());
        supplier.setAddress(request.address());
        supplier.setTaxCode(request.taxCode());
        // KHÔNG cập nhật debtAmount — chỉ hệ thống tự cập nhật qua ImportReceipt

        supplier = supplierRepository.save(supplier);
        log.info("Đã cập nhật NCC id={}", id);

        return SupplierResponse.from(supplier);
    }

    // =========================================================================
    // SUP-05 — Xóa mềm NCC
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * 💡 Senior Note — Tại sao không cho xóa NCC khi còn công nợ, dù là soft delete?
     * (1) Nghiệp vụ kế toán: debtAmount > 0 = hệ thống đang "nợ" NCC tiền.
     *     Nếu NCC bị ẩn đi (soft delete) trong khi vẫn còn khoản nợ chưa giải quyết,
     *     kế toán sẽ không thấy khoản nợ này trong báo cáo công nợ → sai số liệu.
     * (2) Kiểm toán (Audit): Kiểm toán viên cần NCC còn visible để đối chiếu bút toán.
     * (3) Quy trình đúng: Thanh toán hết nợ → debtAmount = 0 → xóa mềm.
     *     Nguyên tắc: "Xóa sau khi giải quyết xong" thay vì "xóa trước, giải quyết sau".
     *
     * Thứ tự kiểm tra:
     * 1. debtAmount = 0 (quan trọng nhất).
     * 2. Không có phiếu nhập DRAFT (tránh mất ngữ cảnh).
     * 3. Soft delete + rename phone.
     * 4. AuditLog @Async.
     */
    @Override
    @Transactional
    public void deleteSupplier(Long id) {
        log.info("Yêu cầu xóa mềm NCC id={}", id);

        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy nhà cung cấp với id: " + id));

        // Kiểm tra công nợ
        if (supplier.getDebtAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new DomainException(
                    ErrorCode.SUPPLIER_OUTSTANDING_DEBT,
                    String.format("Nhà cung cấp '%s' đang có công nợ %.2f VNĐ chưa thanh toán",
                            supplier.getName(), supplier.getDebtAmount()),
                    Map.of("debtAmount", supplier.getDebtAmount(),
                           "supplierName", supplier.getName())
            );
        }

        // Kiểm tra phiếu nhập DRAFT
        boolean hasDraftReceipts = supplierRepository.existsDraftImportReceipt(id, ImportReceiptStatus.DRAFT);
        if (hasDraftReceipts) {
            throw new DomainException(
                    ErrorCode.SUPPLIER_HAS_DRAFT_RECEIPTS,
                    String.format("Nhà cung cấp '%s' đang có phiếu nhập chưa hoàn thành (DRAFT). " +
                            "Vui lòng hủy hoặc hoàn thành phiếu nhập trước khi xóa.", supplier.getName())
            );
        }

        // Snapshot để ghi AuditLog
        String supplierSnapshot = String.format(
                "{\"id\":%d,\"name\":\"%s\",\"phone\":\"%s\",\"debtAmount\":%.2f}",
                supplier.getId(), supplier.getName(), supplier.getPhone(),
                supplier.getDebtAmount()
        );

        // Soft delete — @SQLDelete tự rename phone + set is_deleted=true
        supplierRepository.delete(supplier);

        log.info("Đã xóa mềm NCC id={}, name='{}'", id, supplier.getName());

        // Ghi AuditLog bất đồng bộ — Propagation.REQUIRES_NEW trong AuditLogService
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null) ? auth.getName() : "SYSTEM";
        auditLogService.log(null, username, "DELETE_SUPPLIER", "SUPPLIER", id,
                "{\"action\":\"SOFT_DELETE\",\"before\":" + supplierSnapshot + "}");
    }
}
