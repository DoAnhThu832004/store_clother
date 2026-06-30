package com.example.store_clothes.service;

import com.example.store_clothes.dto.request.CreateImportReceiptRequest;
import com.example.store_clothes.dto.response.ImportReceiptResponse;
import com.example.store_clothes.entity.*;
import com.example.store_clothes.enums.ImportReceiptStatus;
import com.example.store_clothes.enums.TransactionType;
import com.example.store_clothes.exception.BusinessException;
import com.example.store_clothes.exception.EntityNotFoundException;
import com.example.store_clothes.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ImportReceiptService - Tầng xử lý nghiệp vụ Nhập Hàng.
 *
 * ====================================================================
 * NGUYÊN TẮC THIẾT KẾ BẮT BUỘC - ĐỌC TRƯỚC KHI SỬA CODE:
 * ====================================================================
 *
 * 1. CONCURRENCY CONTROL (Kiểm soát bất đồng bộ):
 *    - Bắt buộc dùng variantRepository.findByIdForUpdate() (Pessimistic Lock)
 *      khi cộng tồn kho. Tuyệt đối không đổi sang Optimistic Lock.
 *    - Lý do: Nhiều phiếu nhập đồng thời → Race Condition → Lost Update inventory.
 *
 * 2. ATOMICITY (Tính toàn vẹn):
 *    - completeReceipt() bắt buộc có @Transactional.
 *    - KHÔNG dùng try-catch nuốt exception bên trong vòng lặp.
 *      Nếu một detail bị lỗi → toàn bộ transaction rollback → tồn kho nhất quán.
 *
 * 3. IMMUTABLE AUDIT TRAIL (Thẻ kho bất biến):
 *    - Bảng stock_history chỉ INSERT. Không bao giờ UPDATE hay DELETE.
 *    - stockHistoryRepository.saveAll() là thao tác duy nhất được phép.
 *
 * 4. CÔNG NỢ NHÀ CUNG CẤP:
 *    - Công thức: Debt_mới = Debt_cũ + TotalAmount - PaidAmount
 *    - KHÔNG sử dụng công thức khác.
 * ====================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportReceiptService {

    private final ImportReceiptRepository receiptRepository;
    private final ImportReceiptDetailRepository detailRepository;
    private final ProductVariantRepository variantRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final SupplierRepository supplierRepository;

    // Format mã phiếu nhập: PN-YYYYMMDD-XXXX
    private static final String RECEIPT_CODE_PREFIX = "PN-";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // =========================================================================
    // TẠO PHIẾU NHẬP (DRAFT)
    // =========================================================================

    /**
     * Tạo mới Phiếu Nhập Hàng ở trạng thái DRAFT.
     *
     * Quy trình:
     * 1. Validate nhà cung cấp tồn tại.
     * 2. Validate từng biến thể tồn tại và đang ACTIVE.
     * 3. Tính tổng giá trị (totalAmount).
     * 4. Validate paidAmount <= totalAmount.
     * 5. Sinh mã phiếu tự động (PN-YYYYMMDD-XXXX).
     * 6. Lưu phiếu và các dòng chi tiết.
     *
     * @Transactional: Sinh mã phiếu và lưu phiếu phải trong cùng 1 transaction
     *                 để tránh race condition tạo mã trùng.
     *
     * @param request Dữ liệu phiếu nhập từ client
     * @return Response DTO chứa thông tin phiếu vừa tạo
     */
    @Transactional
    public ImportReceiptResponse createDraftReceipt(CreateImportReceiptRequest request) {
        log.debug("Creating draft import receipt for supplierId={}", request.getSupplierId());

        // 1. Validate và load Supplier
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Nhà cung cấp không tồn tại với ID: " + request.getSupplierId()));

        // 2. Tính totalAmount và validate từng biến thể
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<ImportReceiptDetail> detailsToSave = new ArrayList<>();

        // Tạo phiếu nhập trước (cần ID để liên kết detail)
        BigDecimal paidAmount = request.getPaidAmount() != null
                ? request.getPaidAmount()
                : BigDecimal.ZERO;

        // Tính tổng trước để validate paidAmount
        for (CreateImportReceiptRequest.ImportDetailRequest item : request.getItems()) {
            // Validate biến thể tồn tại (không cần lock lúc này - chỉ đọc)
            variantRepository.findById(item.getVariantId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Biến thể sản phẩm không tồn tại với ID: " + item.getVariantId()));

            BigDecimal lineTotal = item.getImportPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()));
            totalAmount = totalAmount.add(lineTotal);
        }

        // 3. Validate paidAmount không vượt totalAmount
        if (paidAmount.compareTo(totalAmount) > 0) {
            throw new BusinessException(
                    "Số tiền thanh toán (" + paidAmount + ") không được vượt quá tổng giá trị phiếu (" + totalAmount + ")");
        }

        // 4. Sinh mã phiếu nhập
        String receiptCode = generateReceiptCode();
        log.debug("Generated receipt code: {}", receiptCode);

        // 5. Tạo và lưu phiếu nhập (DRAFT)
        ImportReceipt receipt = ImportReceipt.builder()
                .receiptCode(receiptCode)
                .supplier(supplier)
                .totalAmount(totalAmount)
                .paidAmount(paidAmount)
                .status(ImportReceiptStatus.DRAFT)
                .note(request.getNote())
                .build();

        receipt = receiptRepository.save(receipt);

        // 6. Tạo và lưu từng dòng chi tiết
        for (CreateImportReceiptRequest.ImportDetailRequest item : request.getItems()) {
            ProductVariant variant = variantRepository.findById(item.getVariantId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Biến thể sản phẩm không tồn tại với ID: " + item.getVariantId()));

            ImportReceiptDetail detail = ImportReceiptDetail.builder()
                    .receipt(receipt)
                    .variant(variant)
                    .quantity(item.getQuantity())
                    .importPrice(item.getImportPrice())
                    .build();
            detailsToSave.add(detail);
        }

        detailRepository.saveAll(detailsToSave);
        log.info("Draft import receipt created: receiptCode={}, supplierId={}, totalAmount={}",
                receiptCode, request.getSupplierId(), totalAmount);

        // Load lại phiếu kèm details để map response
        receipt = receiptRepository.findByIdWithDetails(receipt.getId())
                .orElseThrow(() -> new EntityNotFoundException("Lỗi hệ thống: Không thể load phiếu vừa tạo"));

        return mapToResponse(receipt);
    }

    // =========================================================================
    // HOÀN THÀNH PHIẾU NHẬP (DRAFT → COMPLETED)
    // =========================================================================

    /**
     * Hoàn thành phiếu nhập: cộng tồn kho, ghi thẻ kho, cập nhật công nợ NCC.
     *
     * ⚠️  CRITICAL - ĐỌC TRƯỚC KHI SỬA:
     *
     * [1] @Transactional BẮT BUỘC:
     *     Tất cả 4 bước nghiệp vụ (đổi status, cộng kho, ghi thẻ kho, cập nhật NCC)
     *     phải nằm trong 1 transaction duy nhất. Nếu bất kỳ bước nào thất bại,
     *     Spring sẽ rollback toàn bộ - đảm bảo consistency tuyệt đối.
     *
     * [2] findByIdForUpdate() - PESSIMISTIC LOCK:
     *     Khi cộng kho, phải dùng findByIdForUpdate() (SELECT ... FOR UPDATE).
     *     DB sẽ cấp exclusive lock trên row ProductVariant đó trong suốt transaction.
     *     Không được đổi sang findById() hay bất kỳ cơ chế lock khác.
     *
     * [3] KHÔNG try-catch nuốt exception trong vòng lặp:
     *     Nếu bắt exception và tiếp tục vòng lặp → dữ liệu tồn kho sẽ không nhất quán
     *     (một số biến thể được cộng kho, một số không) nhưng phiếu vẫn COMPLETED.
     *     Exception phải được throw tự nhiên để @Transactional rollback toàn bộ.
     *
     * [4] Công nợ NCC: Debt_mới = Debt_cũ + TotalAmount - PaidAmount.
     *     TotalAmount đã được tính và lưu vào phiếu khi tạo DRAFT.
     *
     * @param receiptId ID của phiếu nhập cần hoàn thành
     * @return Response DTO của phiếu đã hoàn thành
     */
    @Transactional
    public ImportReceiptResponse completeReceipt(Long receiptId) {
        log.debug("Completing import receipt: receiptId={}", receiptId);

        // Bước 0: Load phiếu nhập KÈM DETAILS (JOIN FETCH để tránh LazyInitializationException)
        ImportReceipt receipt = receiptRepository.findByIdWithDetails(receiptId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Phiếu nhập không tồn tại với ID: " + receiptId));

        // Bước 0b: Validate trạng thái - chỉ DRAFT mới được hoàn thành
        if (receipt.getStatus() != ImportReceiptStatus.DRAFT) {
            throw new BusinessException(
                    "Chỉ cho phép hoàn thành phiếu nhập đang ở trạng thái DRAFT. " +
                    "Trạng thái hiện tại: " + receipt.getStatus());
        }

        // Bước 0c: Validate phiếu có ít nhất 1 dòng chi tiết
        if (receipt.getDetails() == null || receipt.getDetails().isEmpty()) {
            throw new BusinessException(
                    "Phiếu nhập không có dòng hàng hóa nào. Không thể hoàn thành.");
        }

        // =====================================================================
        // Bước 1: Đổi trạng thái phiếu → COMPLETED
        // =====================================================================
        receipt.setStatus(ImportReceiptStatus.COMPLETED);

        // =====================================================================
        // Bước 2 & 3: Cộng tồn kho + Ghi thẻ kho cho từng dòng chi tiết
        //
        // QUAN TRỌNG:
        // - KHÔNG đặt try-catch trong vòng lặp này.
        // - Nếu một detail thất bại → exception propagate ra ngoài → @Transactional rollback.
        // - Mọi thay đổi tồn kho và thẻ kho đều được rollback → dữ liệu nhất quán.
        // =====================================================================
        List<StockHistory> histories = new ArrayList<>();

        for (ImportReceiptDetail detail : receipt.getDetails()) {
            // [PESSIMISTIC LOCK] - Bắt buộc dùng findByIdForUpdate()
            // SELECT pv FROM ProductVariant pv WHERE pv.id = :id FOR UPDATE
            // DB cấp row-level exclusive lock → thread khác phải chờ → tránh Race Condition
            ProductVariant variant = variantRepository.findByIdForUpdate(detail.getVariant().getId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Biến thể sản phẩm không tồn tại với ID: " + detail.getVariant().getId()));

            // Snapshot tồn kho TRƯỚC khi cộng (để ghi vào thẻ kho)
            int balanceBefore = variant.getInventory();
            // Tồn kho SAU khi cộng
            int balanceAfter = balanceBefore + detail.getQuantity();

            // Cập nhật tồn kho thực tế
            variant.setInventory(balanceAfter);

            // Cập nhật giá nhập mới nhất của biến thể (theo snapshot của phiếu này)
            variant.setImportPrice(detail.getImportPrice());

            // Lưu biến thể (đang giữ lock - sẽ release khi transaction commit)
            variantRepository.save(variant);

            log.debug("Updated inventory for variantId={}: {} -> {} (receiptCode={})",
                    variant.getId(), balanceBefore, balanceAfter, receipt.getReceiptCode());

            // [IMMUTABLE INSERT] - Tạo bản ghi thẻ kho bất biến
            // Chỉ được INSERT - không bao giờ UPDATE/DELETE bản ghi này
            StockHistory history = StockHistory.builder()
                    .variantId(variant.getId())
                    .changeQuantity(detail.getQuantity())    // Số dương (+) vì là NHẬP
                    .transactionType(TransactionType.IMPORT)
                    .referenceCode(receipt.getReceiptCode()) // Mã phiếu nhập
                    .balanceBefore(balanceBefore)
                    .balanceAfter(balanceAfter)
                    .createdAt(LocalDateTime.now())
                    .build();
            histories.add(history);
        }

        // Batch INSERT tất cả thẻ kho trong 1 lần (tối ưu nhờ rewriteBatchedStatements=true)
        stockHistoryRepository.saveAll(histories);
        log.debug("Saved {} stock history records for receiptCode={}", histories.size(), receipt.getReceiptCode());

        // =====================================================================
        // Bước 4: Cập nhật công nợ Nhà Cung Cấp
        //
        // CÔNG THỨC BẮT BUỘC: Debt_mới = Debt_cũ + TotalAmount - PaidAmount
        // - TotalAmount: Tổng giá trị hàng hóa trong phiếu (đã tính khi tạo DRAFT)
        // - PaidAmount:  Số tiền đã thanh toán ngay khi nhập
        // - Phần chênh lệch (TotalAmount - PaidAmount) = phần mua chịu → cộng vào nợ
        // =====================================================================
        Supplier supplier = receipt.getSupplier();
        BigDecimal debtBefore = supplier.getDebtAmount();
        BigDecimal addedDebt = receipt.getTotalAmount().subtract(receipt.getPaidAmount());
        BigDecimal debtAfter = debtBefore.add(addedDebt);

        supplier.setDebtAmount(debtAfter);
        supplierRepository.save(supplier);

        log.info("Updated supplier debt: supplierId={}, debtBefore={}, addedDebt={}, debtAfter={}",
                supplier.getId(), debtBefore, addedDebt, debtAfter);

        // Bước 5: Lưu phiếu nhập (status đã đổi sang COMPLETED)
        receipt = receiptRepository.save(receipt);

        log.info("Import receipt completed successfully: receiptCode={}, totalAmount={}, paidAmount={}",
                receipt.getReceiptCode(), receipt.getTotalAmount(), receipt.getPaidAmount());

        return mapToResponse(receipt);
    }

    // =========================================================================
    // HỦY PHIẾU NHẬP (DRAFT → CANCELLED)
    // =========================================================================

    /**
     * Hủy phiếu nhập đang ở trạng thái DRAFT.
     * Chỉ cho phép hủy DRAFT - phiếu đã COMPLETED không được hủy.
     *
     * @param receiptId ID của phiếu nhập cần hủy
     * @return Response DTO của phiếu đã hủy
     */
    @Transactional
    public ImportReceiptResponse cancelReceipt(Long receiptId) {
        log.debug("Cancelling import receipt: receiptId={}", receiptId);

        ImportReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Phiếu nhập không tồn tại với ID: " + receiptId));

        if (receipt.getStatus() != ImportReceiptStatus.DRAFT) {
            throw new BusinessException(
                    "Chỉ cho phép hủy phiếu nhập đang ở trạng thái DRAFT. " +
                    "Trạng thái hiện tại: " + receipt.getStatus());
        }

        receipt.setStatus(ImportReceiptStatus.CANCELLED);
        receipt = receiptRepository.save(receipt);

        log.info("Import receipt cancelled: receiptCode={}", receipt.getReceiptCode());
        return mapToResponse(receipt);
    }

    // =========================================================================
    // TRUY VẤN
    // =========================================================================

    /**
     * Lấy chi tiết một phiếu nhập theo ID.
     *
     * @param receiptId ID phiếu nhập
     * @return Response DTO kèm danh sách chi tiết
     */
    @Transactional(readOnly = true)
    public ImportReceiptResponse getReceiptById(Long receiptId) {
        ImportReceipt receipt = receiptRepository.findByIdWithDetails(receiptId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Phiếu nhập không tồn tại với ID: " + receiptId));
        return mapToResponse(receipt);
    }

    /**
     * Lấy danh sách phiếu nhập của một nhà cung cấp.
     *
     * @param supplierId ID nhà cung cấp
     * @return Danh sách phiếu nhập (không kèm detail)
     */
    @Transactional(readOnly = true)
    public List<ImportReceiptResponse> getReceiptsBySupplier(Long supplierId) {
        // Validate supplier tồn tại
        if (!supplierRepository.existsById(supplierId)) {
            throw new EntityNotFoundException(
                    "Nhà cung cấp không tồn tại với ID: " + supplierId);
        }
        return receiptRepository.findBySupplierIdOrderByCreatedAtDesc(supplierId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // =========================================================================
    // PRIVATE METHODS
    // =========================================================================

    /**
     * Sinh mã phiếu nhập tự động theo format: PN-YYYYMMDD-XXXX.
     *
     * Thuật toán:
     * 1. Tính prefix = "PN-" + ngày hôm nay + "-" (ví dụ: "PN-20260626-")
     * 2. Query MAX(receipt_code) LIKE prefix + '%' từ DB.
     * 3. Nếu chưa có phiếu nào hôm nay → trả về "PN-20260626-0001".
     * 4. Nếu đã có → parse số thứ tự cuối → tăng lên 1 → format lại 4 chữ số.
     *
     * 💡 Thread Safety: Method này chạy trong @Transactional → lock cùng
     *    với việc INSERT phiếu mới → tránh sinh 2 phiếu cùng mã.
     *
     * @return Mã phiếu nhập theo format PN-YYYYMMDD-XXXX
     */
    private String generateReceiptCode() {
        String today = LocalDateTime.now().format(DATE_FORMATTER);
        String prefix = RECEIPT_CODE_PREFIX + today + "-";

        return receiptRepository.findMaxReceiptCodeByPrefix(prefix)
                .map(maxCode -> {
                    // Parse phần số thứ tự (4 ký tự cuối)
                    String seqStr = maxCode.substring(prefix.length());
                    int nextSeq = Integer.parseInt(seqStr) + 1;
                    return prefix + String.format("%04d", nextSeq);
                })
                .orElse(prefix + "0001"); // Phiếu đầu tiên trong ngày
    }

    /**
     * Map ImportReceipt entity sang ImportReceiptResponse DTO.
     * Flatten quan hệ 2 chiều để tránh vòng lặp JSON serialization.
     *
     * @param receipt Entity cần map
     * @return Response DTO
     */
    private ImportReceiptResponse mapToResponse(ImportReceipt receipt) {
        // Map danh sách detail (có thể null nếu phiếu chưa load details)
        List<ImportReceiptResponse.DetailSummary> detailSummaries = new ArrayList<>();
        if (receipt.getDetails() != null) {
            for (ImportReceiptDetail detail : receipt.getDetails()) {
                BigDecimal lineTotal = detail.getImportPrice()
                        .multiply(BigDecimal.valueOf(detail.getQuantity()));

                ImportReceiptResponse.DetailSummary summary = ImportReceiptResponse.DetailSummary.builder()
                        .variantId(detail.getVariant().getId())
                        .sku(detail.getVariant().getSku())
                        .color(detail.getVariant().getColor())
                        .size(detail.getVariant().getSize())
                        .quantity(detail.getQuantity())
                        .importPrice(detail.getImportPrice())
                        .lineTotal(lineTotal)
                        .build();
                detailSummaries.add(summary);
            }
        }

        // Map Supplier
        Supplier supplier = receipt.getSupplier();
        ImportReceiptResponse.SupplierSummary supplierSummary = ImportReceiptResponse.SupplierSummary.builder()
                .id(supplier.getId())
                .name(supplier.getName())
                .phone(supplier.getPhone())
                .build();

        // Tính debtAmount = totalAmount - paidAmount (phần chưa thanh toán của phiếu này)
        BigDecimal debtAmount = receipt.getTotalAmount().subtract(receipt.getPaidAmount());

        return ImportReceiptResponse.builder()
                .id(receipt.getId())
                .receiptCode(receipt.getReceiptCode())
                .status(receipt.getStatus())
                .supplier(supplierSummary)
                .totalAmount(receipt.getTotalAmount())
                .paidAmount(receipt.getPaidAmount())
                .debtAmount(debtAmount)
                .note(receipt.getNote())
                .details(detailSummaries)
                .createdAt(receipt.getCreatedAt())
                .updatedAt(receipt.getUpdatedAt())
                .build();
    }
}
