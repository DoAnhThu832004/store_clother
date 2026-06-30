package com.example.store_clothes.repository;

import com.example.store_clothes.entity.ImportReceiptDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository cho ImportReceiptDetail Entity.
 *
 * Trong hầu hết trường hợp, ImportReceiptDetail được quản lý thông qua
 * ImportReceipt (CascadeType.ALL). Repository này cung cấp thêm một số
 * query trực tiếp cho các trường hợp cần truy vấn riêng lẻ.
 */
public interface ImportReceiptDetailRepository extends JpaRepository<ImportReceiptDetail, Long> {

    /**
     * Lấy tất cả dòng chi tiết của một phiếu nhập.
     * Thường dùng khi không cần load toàn bộ ImportReceipt.
     *
     * @param receiptId ID của phiếu nhập
     * @return Danh sách chi tiết
     */
    List<ImportReceiptDetail> findByReceiptId(Long receiptId);

    /**
     * Đếm số lần một biến thể đã được nhập hàng qua các phiếu nhập đã COMPLETED.
     * Dùng để kiểm tra biến thể có lịch sử nhập hàng chưa.
     *
     * @param variantId ID biến thể
     * @return Số lần xuất hiện trong các phiếu nhập đã hoàn thành
     */
    @Query("SELECT COUNT(d) FROM ImportReceiptDetail d " +
           "JOIN d.receipt r " +
           "WHERE d.variant.id = :variantId " +
           "AND r.status = com.example.store_clothes.enums.ImportReceiptStatus.COMPLETED")
    long countCompletedImportsByVariantId(@Param("variantId") Long variantId);
}
