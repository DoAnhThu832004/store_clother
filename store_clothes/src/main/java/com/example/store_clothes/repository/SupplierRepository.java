package com.example.store_clothes.repository;

import com.example.store_clothes.entity.ImportReceipt;
import com.example.store_clothes.entity.Supplier;
import com.example.store_clothes.enums.ImportReceiptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SupplierRepository — Tầng truy cập dữ liệu cho Nhà Cung Cấp.
 *
 * LƯU Ý: @SQLRestriction("is_deleted = false") trên Supplier Entity
 * tự động lọc các nhà cung cấp đã xóa mềm khỏi MỌI query thông thường.
 * Các method findBy* sẽ không bao giờ trả về Supplier bị xóa mềm.
 */
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    /**
     * Kiểm tra tên nhà cung cấp đã tồn tại chưa (validate trước khi tạo mới).
     * Case-insensitive để tránh tạo trùng do khác chữ hoa/thường.
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Tìm nhà cung cấp theo số điện thoại.
     * Dùng để validate phone không trùng khi tạo/cập nhật NCC.
     */
    Optional<Supplier> findByPhone(String phone);

    /**
     * Tìm NCC khác (khác ID hiện tại) có cùng phone.
     * Dùng khi UPDATE để kiểm tra phone mới không trùng với NCC khác.
     */
    @Query("SELECT s FROM Supplier s WHERE s.phone = :phone AND s.id <> :excludeId")
    Optional<Supplier> findByPhoneAndIdNot(@Param("phone") String phone, @Param("excludeId") Long excludeId);

    /**
     * Danh sách phân trang với filter keyword và hasDebt.
     *
     * Logic:
     * - keyword null → không filter tên.
     * - hasDebt = true → WHERE debt_amount > 0.
     * - hasDebt = false / null → không filter theo debt.
     *
     * 💡 Senior Note — Dynamic Query với JPQL:
     * Dùng conditional (:hasDebt IS NULL OR (cond)) thay vì @Query nhiều overloads.
     * Spring Data chuyển Boolean null thành IS NULL so sánh được trong JPQL.
     */
    @Query("""
        SELECT s FROM Supplier s
        WHERE (:keyword IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (:hasDebt IS NULL OR (:hasDebt = true AND s.debtAmount > 0))
        ORDER BY s.createdAt DESC
        """)
    Page<Supplier> searchSuppliers(@Param("keyword") String keyword,
                                   @Param("hasDebt") Boolean hasDebt,
                                   Pageable pageable);

    /**
     * Tìm tất cả nhà cung cấp đang có công nợ (debtAmount > 0).
     * Dùng cho báo cáo công nợ hàng tháng.
     */
    @Query("SELECT s FROM Supplier s WHERE s.debtAmount > 0 ORDER BY s.debtAmount DESC")
    List<Supplier> findAllWithDebt();

    /**
     * Tìm nhà cung cấp theo tên (tìm kiếm gần đúng, không phân biệt hoa thường).
     * Dùng cho chức năng search autocomplete khi lập phiếu nhập.
     */
    @Query("SELECT s FROM Supplier s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Supplier> searchByName(@Param("name") String name);

    /**
     * Lấy 5 phiếu nhập gần nhất của nhà cung cấp (SUP-03).
     * Dùng cho màn hình chi tiết NCC → hiển thị lịch sử nhập hàng.
     *
     * 💡 Senior Note — Pagination với LIMIT:
     * Không dùng Pageable ở đây vì chỉ cần top-5 cố định,
     * Pageable sẽ sinh COUNT query thêm không cần thiết.
     */
    @Query("""
        SELECT ir FROM ImportReceipt ir
        WHERE ir.supplier.id = :supplierId
        ORDER BY ir.createdAt DESC
        LIMIT 5
        """)
    List<ImportReceipt> findTop5RecentImportReceipts(@Param("supplierId") Long supplierId);

    /**
     * Kiểm tra NCC có phiếu nhập đang DRAFT không (SUP-05).
     * Nếu có → không cho xóa mềm.
     */
    @Query("""
        SELECT COUNT(ir) > 0 FROM ImportReceipt ir
        WHERE ir.supplier.id = :supplierId
        AND ir.status = :status
        """)
    boolean existsDraftImportReceipt(@Param("supplierId") Long supplierId,
                                     @Param("status") ImportReceiptStatus status);
}
