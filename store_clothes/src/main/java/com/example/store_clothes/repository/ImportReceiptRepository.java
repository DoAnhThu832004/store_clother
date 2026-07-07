package com.example.store_clothes.repository;

import com.example.store_clothes.entity.ImportReceipt;
import com.example.store_clothes.enums.ImportReceiptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository cho ImportReceipt Entity.
 *
 * THIẾT KẾ QUAN TRỌNG:
 * - ImportReceipt KHÔNG có Soft Delete (không có @SQLRestriction) vì là bằng chứng
 *   kế toán bất biến. Dữ liệu này không được xóa dưới bất kỳ hình thức nào.
 *
 * - findByIdWithDetails(): Dùng JOIN FETCH để load cả ImportReceiptDetail trong 1 query.
 *   Bắt buộc dùng method này (không phải findById()) khi cần duyệt qua details,
 *   ví dụ: trong completeReceipt(), để tránh LazyInitializationException.
 *
 * - findMaxReceiptCodeByPrefix(): Native Query sinh mã phiếu nhập tự động.
 *   Sử dụng MAX + LIKE để lấy số thứ tự lớn nhất của ngày hiện tại.
 */
public interface ImportReceiptRepository extends JpaRepository<ImportReceipt, Long> {

    /**
     * Tìm phiếu nhập theo ID và LOAD SẴN danh sách detail (EAGER fetch bằng JOIN FETCH).
     *
     * 💡 Senior Note — Tại sao cần findByIdWithDetails thay vì findById()?
     * Khi gọi findById() với FetchType.LAZY, danh sách details chưa được load.
     * Nếu Transaction đóng lại (ra khỏi scope @Transactional) mà code vẫn cố gọi
     * receipt.getDetails() → LazyInitializationException.
     *
     * Trong completeReceipt(), chúng ta cần duyệt qua details() bên trong cùng
     * Transaction → phải dùng JOIN FETCH để load sẵn trước.
     *
     * @param id ID của phiếu nhập
     * @return Optional chứa phiếu nhập kèm danh sách detail
     */
    @Query("SELECT r FROM ImportReceipt r JOIN FETCH r.details d JOIN FETCH d.variant WHERE r.id = :id")
    Optional<ImportReceipt> findByIdWithDetails(@Param("id") Long id);

    /**
     * Sinh mã phiếu nhập tự động theo ngày.
     *
     * 💡 Senior Note — Native Query với MAX + LIKE để sinh sequence theo ngày:
     * Format mã: PN-YYYYMMDD-XXXX. Ví dụ: "PN-20260626-0001".
     * Query lấy phần số thứ tự (4 chữ số cuối) lớn nhất của ngày hiện tại.
     *
     * Ví dụ: prefix = "PN-20260626-"
     *   DB có: PN-20260626-0001, PN-20260626-0002
     *   → MAX("PN-20260626-0001", "PN-20260626-0002") = "PN-20260626-0002"
     *   → Service sẽ parse lấy "0002" → parse int → +1 → "0003"
     *   → Mã mới: "PN-20260626-0003"
     *
     * @param prefix Tiền tố dạng "PN-YYYYMMDD-" (ví dụ: "PN-20260626-")
     * @return Mã phiếu nhập lớn nhất theo prefix, null nếu chưa có phiếu nào trong ngày
     */
    @Query(value = "SELECT MAX(receipt_code) FROM import_receipts WHERE receipt_code LIKE CONCAT(:prefix, '%')",
           nativeQuery = true)
    Optional<String> findMaxReceiptCodeByPrefix(@Param("prefix") String prefix);

    /**
     * Lấy danh sách phiếu nhập theo nhà cung cấp, sắp xếp mới nhất trước.
     * Dùng cho màn hình lịch sử nhập hàng của một NCC.
     */
    List<ImportReceipt> findBySupplierIdOrderByCreatedAtDesc(Long supplierId);

    /**
     * Lấy danh sách phiếu nhập theo trạng thái.
     * Ví dụ: Lấy tất cả DRAFT để nhắc nhân viên hoàn thành phiếu còn dang dở.
     */
    List<ImportReceipt> findByStatusOrderByCreatedAtDesc(ImportReceiptStatus status);

    // =========================================================================
    // IR-01: Danh sách phiếu nhập với bộ lọc đa điều kiện + phân trang
    // =========================================================================

    /**
     * Tìm kiếm phiếu nhập với nhiều bộ lọc kết hợp.
     *
     * THIẾT KẾ QUERY:
     * - KHÔNG JOIN FETCH details để tránh N+1 (danh sách chỉ cần metadata).
     * - KHÔNG JOIN FETCH supplier.* — chỉ cần supplier.name và supplier.id (done qua r.supplier).
     * - Tất cả params đều nullable: null = không filter theo điều kiện đó.
     * - Pageable: Spring Data tự inject ORDER BY và LIMIT/OFFSET.
     *
     * 💡 Senior Note — Tại sao dùng JPQL thay vì Specification (Criteria API)?
     * Specification linh hoạt hơn nhưng verbose hơn và harder to read.
     * Với số lượng filter cố định (4 điều kiện), JPQL with IS NULL check đủ tốt
     * và dễ đọc/maintain hơn. Nếu filter tăng lên >8 → migrate sang Specification.
     *
     * @param status     Filter theo trạng thái (null = không filter)
     * @param supplierId Filter theo nhà cung cấp (null = không filter)
     * @param from       Filter từ ngày tạo (null = không filter)
     * @param to         Filter đến ngày tạo (null = không filter)
     * @param pageable   Phân trang (page, size, sort)
     * @return Page<ImportReceipt> chỉ chứa metadata, KHÔNG load details
     */
    @Query("""
        SELECT r FROM ImportReceipt r
        JOIN FETCH r.supplier s
        WHERE (:status IS NULL OR r.status = :status)
          AND (:supplierId IS NULL OR s.id = :supplierId)
          AND (:from IS NULL OR r.createdAt >= :from)
          AND (:to IS NULL OR r.createdAt <= :to)
        """)
    Page<ImportReceipt> findPagedWithFilters(
        @Param("status") ImportReceiptStatus status,
        @Param("supplierId") Long supplierId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable
    );
}

