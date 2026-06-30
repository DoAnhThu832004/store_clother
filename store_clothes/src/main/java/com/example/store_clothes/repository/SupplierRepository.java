package com.example.store_clothes.repository;

import com.example.store_clothes.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho Supplier Entity.
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
     */
    Optional<Supplier> findByPhone(String phone);

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
}
