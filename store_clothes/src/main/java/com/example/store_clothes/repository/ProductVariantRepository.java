package com.example.store_clothes.repository;

import com.example.store_clothes.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho ProductVariant Entity.
 *
 * QUAN TRỌNG - @SQLRestriction("is_deleted = false") tự động áp dụng cho:
 * - findBySku(), findByBarcode(): tìm theo SKU/Barcode index.
 * - findByProductId(): lấy variants của product.
 * → Variant đã xóa mềm sẽ không bao giờ xuất hiện trong kết quả query thông thường.
 *
 * CAS ĐẶC BIỆT - Cascade Soft Delete:
 * Phương thức softDeleteAllByProductId() dùng @Query Native để bypass
 * @SQLRestriction và update thẳng xuống DB, đây là ngoại lệ có chủ đích.
 */
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    /**
     * Tìm biến thể theo SKU - sử dụng index idx_variant_sku.
     * SKU là duy nhất toàn hệ thống, nên Optional là kiểu trả về phù hợp.
     *
     * Performance: Query này sử dụng index trực tiếp → O(log n) thay vì O(n).
     */
    Optional<ProductVariant> findBySku(String sku);

    /**
     * Tìm biến thể theo Barcode - sử dụng index idx_variant_barcode.
     * Barcode không bắt buộc unique (cùng 1 barcode nhà sản xuất có thể
     * ứng với nhiều variant), nhưng thực tế hiếm gặp trường hợp này.
     * Trả về Optional để xử lý trường hợp không tìm thấy.
     */
    Optional<ProductVariant> findByBarcode(String barcode);

    /**
     * Kiểm tra SKU đã tồn tại chưa (validate trước khi tạo mới).
     */
    boolean existsBySku(String sku);

    /**
     * Kiểm tra Barcode đã tồn tại chưa.
     */
    boolean existsByBarcode(String barcode);

    /**
     * Lấy tất cả variants của một Product (dùng khi load chi tiết Product).
     * Kết hợp với JOIN FETCH trong ProductRepository để tránh N+1.
     */
    List<ProductVariant> findByProductId(Long productId);

    /**
     * CASCADE SOFT DELETE - Xóa mềm tất cả Variant của một Product.
     *
     * ĐÂY LÀ TRƯỜNG HỢP ĐẶC BIỆT - PHẢI DÙNG NATIVE QUERY:
     *
     * 1. Tại sao không dùng @SQLDelete trên entity?
     *    @SQLDelete chỉ hoạt động khi DELETE một entity đơn lẻ theo ID.
     *    Không hỗ trợ bulk update theo điều kiện (WHERE product_id = ?).
     *
     * 2. Tại sao không dùng JPQL (deleteByProductId)?
     *    JPQL DELETE query sẽ bị chặn bởi @SQLDelete trên entity,
     *    dẫn đến phát sinh N câu UPDATE riêng lẻ thay vì 1 câu bulk update.
     *    Với 100 variants → 100 queries riêng lẻ, rất kém hiệu năng.
     *
     * 3. Native SQL bulk update BYPASS @SQLRestriction:
     *    Đây là hành vi có chủ đích (intentional bypass) - chúng ta cần
     *    update ngay cả các record đã bị xóa mềm để đảm bảo nhất quán dữ liệu.
     *    (Thực tế, variant chưa xóa của product đang xóa mới cần update.)
     *
     * 💡 Senior Note — Return type int (không phải void):
     * @Modifying + @Query DML statement có thể trả về int/Integer = số rows bị ảnh hưởng.
     * Luôn dùng int thay vì void để Service có thể log số lượng variant đã xóa,
     * hỗ trợ audit, monitoring và debug hiệu quả hơn.
     *
     * @Modifying: Báo Spring đây là query thay đổi dữ liệu (INSERT/UPDATE/DELETE).
     *             Bắt buộc phải có khi dùng @Query với DML statement.
     */
    @Modifying
    @Query(value = "UPDATE product_variants SET is_deleted = true WHERE product_id = :productId",
           nativeQuery = true)
    int softDeleteAllByProductId(@Param("productId") Long productId);


    /**
     * Tìm biến thể theo SKU, kèm thông tin Product (để hiển thị productId, productName).
     * JOIN FETCH để tránh LAZY load gây thêm query khi truy cập variant.getProduct().
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT v FROM ProductVariant v JOIN FETCH v.product WHERE v.sku = :sku")
    Optional<ProductVariant> findBySkuWithProduct(@Param("sku") String sku);

    /**
     * Tìm biến thể theo Barcode, kèm thông tin Product.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT v FROM ProductVariant v JOIN FETCH v.product WHERE v.barcode = :barcode")
    Optional<ProductVariant> findByBarcodeWithProduct(@Param("barcode") String barcode);

    // =========================================================================
    // MATRIX VARIANT SYSTEM — In-Memory SKU Conflict Resolution
    // =========================================================================

    /**
     * Tải toàn bộ SKU có cùng prefix về bộ nhớ để xử lý xung đột trong vòng lặp.
     *
     * 💡 Senior Note — Chiến lược In-Memory O(1) Lookup:
     * KHÔNG bao giờ gọi existsBySku() trong vòng lặp Colors × Sizes.
     * Ví dụ: 5 màu × 10 size = 50 biến thể → 50 queries xuống DB nếu dùng existsBySku().
     *
     * Thay vào đó:
     * 1. Gọi findSkusWithPrefix(prefix) 1 lần duy nhất → load toàn bộ SKU có prefix vào List.
     * 2. Đổ vào HashSet<String> → lookup O(1) thay vì O(n).
     * 3. Vòng lặp Cartesian Product chỉ check Set trong RAM → 0 query thêm.
     *
     * 💡 Senior Note — JPQL SELECT scalar (pv.sku) thay vì SELECT entity:
     * Chỉ lấy cột sku (String), không load toàn bộ ProductVariant object.
     * Tiết kiệm memory và network I/O đáng kể khi có hàng nghìn SKU cùng prefix.
     *
     * @param prefix Tiền tố SKU (ví dụ: "ATN" cho "Áo Thun Nam")
     * @return Danh sách SKU String bắt đầu bằng prefix
     */
    @Query("SELECT pv.sku FROM ProductVariant pv WHERE pv.sku LIKE CONCAT(:prefix, '%')")
    List<String> findSkusWithPrefix(@Param("prefix") String prefix);

    /**
     * Tìm biến thể theo ID với Pessimistic Write Lock (khóa bi quan ghi).
     *
     * 💡 Senior Note — Pessimistic Locking cho Inventory Updates:
     * Khi cập nhật tồn kho (inventory) trong môi trường concurrent:
     * Thread A đọc inventory = 5, Thread B đọc inventory = 5.
     * Cả hai cùng trừ 1 → cả hai ghi lại 4 → Lost Update! Inventory thực tế phải là 3.
     *
     * PESSIMISTIC_WRITE lock buộc DB cấp row-level exclusive lock:
     * - Thread B phải chờ Thread A release lock mới đọc được.
     * - Đảm bảo tính nhất quán tuyệt đối (Serializable isolation cho row này).
     *
     * 💡 Senior Note — Timeout 3000ms:
     * jakarta.persistence.lock.timeout = 3000ms: Nếu không acquire được lock sau 3 giây,
     * ném LockTimeoutException → không để thread block vô hạn (gây deadlock chain).
     * Giá trị 3000ms là trade-off: đủ chờ cho giao dịch bình thường, đủ nhanh để fail-fast.
     *
     * ⚠️ WARNING: Chỉ dùng method này khi thực sự cần update inventory.
     * Không dùng cho các read operation thông thường — sẽ làm giảm throughput đáng kể.
     *
     * @param id ID của ProductVariant cần lock
     * @return Optional<ProductVariant> với row đang bị lock ở DB level
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT pv FROM ProductVariant pv WHERE pv.id = :id")
    Optional<ProductVariant> findByIdForUpdate(@Param("id") Long id);

    // =========================================================================
    // STOCK ADJUSTMENT — Atomic Inventory Update (TICKET PV-02b)
    // =========================================================================

    /**
     * Cập nhật tồn kho bằng Atomic SQL UPDATE, KHÔNG qua setter.
     *
     * 💡 Senior Note — Tại sao dùng @Modifying @Query thay vì setter?
     *
     * VẤN ĐỀ nếu dùng setter (variant.setInventory(newQty)):
     *   1. Dirty check conflict với @Version (Optimistic Lock):
     *      Hibernate sẽ bao gồm WHERE version = ? trong UPDATE statement.
     *      Trong stock adjustment ta đã dùng Pessimistic Lock nên version check
     *      là thừa, nhưng nếu version không khớp → OptimisticLockException sai lệch.
     *   2. Race condition trong cùng transaction:
     *      Pessimistic lock đã cấm thread khác đọc/ghi row này,
     *      nhưng Hibernate flush dirty-checked entities theo thứ tự riêng.
     *      Atomic SQL UPDATE bypass flush scheduling → guaranteed atomic.
     *   3. Không triggering extra SELECT:
     *      Setter yêu cầu entity phải loaded trước. @Modifying query
     *      chạy trực tiếp trên DB không cần entity trong 1st-level cache.
     *
     * clearAutomatically = true: Xóa 1st-level cache (EntityManager) sau khi
     * execute, buộc query sau đó load lại entity mới từ DB.
     * Không có clearAutomatically, EntityManager có thể trả về entity cũ với
     * inventory cũ → wrong balanceAfter trong StockHistory.
     *
     * @param id     ID của ProductVariant cần cập nhật
     * @param newQty Số lượng tồn kho mới (>= 0, đã validate trước khi gọi)
     * @return Số row bị ảnh hưởng (phải = 1)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductVariant pv SET pv.inventory = :newQty WHERE pv.id = :id")
    int atomicUpdateInventory(@Param("id") Long id, @Param("newQty") Integer newQty);

    // =========================================================================
    // SOFT DELETE GUARD — DRAFT Receipt Check (TICKET PV-03)
    // =========================================================================

    /**
     * Kiểm tra biến thể có đang nằm trong phiếu nhập DRAFT nào không.
     *
     * 💡 Senior Note — Tại sao phải check DRAFT trước khi xóa mềm variant?
     * Nếu variant đang trong DRAFT receipt và bị xóa mềm:
     *   - @SQLRestriction sẽ ẩn variant khỏi mọi query thông thường.
     *   - Khi nhân viên kho completeReceipt(), hệ thống không tìm thấy variant
     *     → NullPointerException hoặc EntityNotFoundException trong luồng hoàn phiếu.
     *   - Dữ liệu inconsistent: receipt còn trỏ đến variant đã "biến mất".
     *
     * Chỉ chặn DRAFT (chưa hoàn thành), không chặn COMPLETED/CANCELLED.
     * COMPLETED = đã nhập kho xong, variant ID trong receipt chỉ còn là tham chiếu lịch sử.
     *
     * @param variantId ID biến thể cần kiểm tra
     * @return true nếu variant đang được dùng trong ít nhất 1 phiếu DRAFT
     */
    @Query("SELECT COUNT(d) > 0 FROM ImportReceiptDetail d " +
           "JOIN d.receipt r " +
           "WHERE d.variant.id = :variantId " +
           "AND r.status = com.example.store_clothes.enums.ImportReceiptStatus.DRAFT")
    boolean existsByIdInDraftImportReceipt(@Param("variantId") Long variantId);
}
