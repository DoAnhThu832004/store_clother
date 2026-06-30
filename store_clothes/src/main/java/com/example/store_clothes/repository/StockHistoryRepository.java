package com.example.store_clothes.repository;

import com.example.store_clothes.entity.StockHistory;
import com.example.store_clothes.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository cho StockHistory Entity (Thẻ Kho).
 *
 * ⚠️  QUY TẮC BẤT BIẾN TUYỆT ĐỐI:
 * Repository này CHỈ ĐƯỢC PHÉP có các method INSERT (save, saveAll).
 * NGHIÊM CẤM thêm bất kỳ method nào có hành vi UPDATE hoặc DELETE
 * vào bảng stock_history, bao gồm:
 *   - Các method tên bắt đầu bằng delete*
 *   - @Modifying + @Query với UPDATE/DELETE statement
 *   - Bất kỳ hành vi nào thay đổi dữ liệu đã ghi
 *
 * MỌI truy vấn đọc (SELECT) đều được phép và khuyến khích.
 */
public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {

    /**
     * Lấy toàn bộ lịch sử biến động tồn kho của một biến thể sản phẩm.
     * Sắp xếp theo thời gian mới nhất trước để hiển thị timeline.
     *
     * @param variantId ID của biến thể cần tra cứu lịch sử
     * @return Danh sách bản ghi lịch sử, mới nhất trước
     */
    List<StockHistory> findByVariantIdOrderByCreatedAtDesc(Long variantId);

    /**
     * Lấy lịch sử biến động theo mã phiếu tham chiếu.
     * Dùng để kiểm tra tất cả biến động kho liên quan đến một phiếu nhập cụ thể.
     *
     * @param referenceCode Mã phiếu tham chiếu (ví dụ: "PN-20260626-0001")
     * @return Danh sách bản ghi lịch sử của phiếu đó
     */
    List<StockHistory> findByReferenceCodeOrderByCreatedAtAsc(String referenceCode);

    /**
     * Lấy lịch sử biến động theo biến thể và loại giao dịch.
     * Ví dụ: Tìm tất cả lần NHẬP HÀNG của variant này.
     *
     * @param variantId       ID biến thể
     * @param transactionType Loại giao dịch cần lọc
     * @return Danh sách bản ghi lịch sử phù hợp
     */
    List<StockHistory> findByVariantIdAndTransactionTypeOrderByCreatedAtDesc(
            Long variantId, TransactionType transactionType);

    /**
     * Đếm số lần biến động tồn kho của một biến thể.
     * Dùng cho dashboard hoặc báo cáo tần suất nhập/xuất.
     *
     * @param variantId ID biến thể
     * @return Số lần biến động
     */
    long countByVariantId(Long variantId);

    /**
     * Lấy bản ghi thẻ kho mới nhất của một biến thể.
     * Dùng để lấy balanceAfter (tồn kho hiện tại được ghi nhận gần nhất).
     * Có thể dùng để kiểm tra tính nhất quán với ProductVariant.inventory.
     *
     * @param variantId ID biến thể
     * @return Bản ghi mới nhất (Optional để tránh exception nếu chưa có lịch sử)
     */
    @Query("SELECT sh FROM StockHistory sh WHERE sh.variantId = :variantId " +
           "ORDER BY sh.createdAt DESC LIMIT 1")
    java.util.Optional<StockHistory> findLatestByVariantId(@Param("variantId") Long variantId);
}
