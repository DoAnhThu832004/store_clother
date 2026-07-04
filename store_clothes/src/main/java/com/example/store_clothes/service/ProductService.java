package com.example.store_clothes.service;

import com.example.store_clothes.dto.request.CreateProductRequest;
import com.example.store_clothes.dto.request.CreateProductMatrixRequest;
import com.example.store_clothes.dto.request.UpdateProductRequest;
import com.example.store_clothes.dto.request.AddVariantRequest;
import com.example.store_clothes.dto.request.UpdateVariantRequest;
import com.example.store_clothes.dto.request.StockAdjustmentRequest;
import com.example.store_clothes.dto.response.ProductMatrixResponse;
import com.example.store_clothes.dto.response.ProductResponse;
import com.example.store_clothes.dto.response.VariantResponse;

/**
 * ProductService - Interface định nghĩa hợp đồng (contract) của tầng nghiệp vụ.
 *
 * NGUYÊN TẮC THIẾT KẾ:
 * 1. Interface-based Programming: Controller chỉ phụ thuộc vào interface này,
 *    không biết đến implementation. Dễ dàng mock trong unit test,
 *    dễ dàng swap implementation khi cần.
 *
 * 2. Return DTO, not Entity: Service trả về DTO, không bao giờ trả Entity trực tiếp.
 *    Lý do: Entity là JPA managed object, nếu trả về ngoài transaction context
 *    có thể trigger LazyInitializationException khi Jackson serialize.
 *
 * 3. Throw custom exceptions: Service ném custom RuntimeException (BusinessException,
 *    EntityNotFoundException), GlobalExceptionHandler sẽ bắt và convert sang HTTP response.
 */
public interface ProductService {

    /**
     * Tạo mới một sản phẩm cùng với tất cả biến thể của nó.
     *
     * Business rules:
     * - Mã sản phẩm (code) phải là duy nhất trong hệ thống.
     * - Mỗi SKU của biến thể phải là duy nhất trong hệ thống.
     * - Barcode (nếu có) phải là duy nhất.
     * - Phải có ít nhất 1 biến thể khi tạo sản phẩm.
     *
     * @param request DTO chứa thông tin sản phẩm và danh sách biến thể
     * @return ProductResponse DTO của sản phẩm vừa tạo, kèm danh sách variants
     * @throws com.example.store_clothes.exception.BusinessException nếu code/SKU/Barcode đã tồn tại
     */
    ProductResponse createProduct(CreateProductRequest request);

    /**
     * Sinh ma trận biến thể sản phẩm tự động theo tích Descartes (Colors × Sizes).
     *
     * 💡 Senior Note — Matrix vs. Manual Flow:
     * createProduct() (thủ công): Client tự định nghĩa từng SKU, màu, size cho từng variant.
     * createProductMatrix() (tự động): Client chỉ cần cung cấp colors[] + sizes[].
     * Service tự thực hiện Colors × Sizes = N biến thể, sinh SKU và giải quyết xung đột.
     *
     * Business rules:
     * - baseSalePrice phải >= baseImportPrice (cross-field validation).
     * - Mã sản phẩm (product code) tự sinh từ tên, phải duy nhất trong hệ thống.
     * - SKU tự sinh theo quy tắc viết tắt, xung đột được xử lý bằng suffix (-1, -2...).
     * - Xử lý xung đột SKU hoàn toàn In-Memory (O(1) lookup) — chỉ 1 query DB duy nhất.
     *
     * @param request DTO chứa tên sản phẩm, danh sách màu, danh sách size, giá cơ sở
     * @return ProductMatrixResponse chứa thông tin sản phẩm, tổng số biến thể, và danh sách variant
     * @throws com.example.store_clothes.exception.BusinessException nếu giá bán < giá nhập hoặc mã SP đã tồn tại
     */
    ProductMatrixResponse createProductMatrix(CreateProductMatrixRequest request);

    /**
     * Tìm kiếm biến thể sản phẩm theo SKU.
     *
     * SKU là mã quản lý kho nội bộ, thường được nhân viên nhập tay hoặc quét mã.
     * Query này sử dụng index idx_variant_sku → hiệu năng cao O(log n).
     *
     * @param sku Mã SKU cần tìm
     * @return VariantResponse DTO kèm thông tin Product cha
     * @throws com.example.store_clothes.exception.EntityNotFoundException nếu SKU không tồn tại
     */
    VariantResponse findBySku(String sku);

    /**
     * Tìm kiếm biến thể sản phẩm theo Barcode.
     *
     * Barcode thường được quét bằng máy đọc mã vạch tại quầy bán hàng.
     * Query này sử dụng index idx_variant_barcode → hiệu năng cao O(log n).
     *
     * @param barcode Mã barcode cần tìm
     * @return VariantResponse DTO kèm thông tin Product cha
     * @throws com.example.store_clothes.exception.EntityNotFoundException nếu Barcode không tồn tại
     */
    VariantResponse findByBarcode(String barcode);

    /**
     * Lấy thông tin chi tiết một sản phẩm kèm toàn bộ biến thể.
     *
     * @param productId ID của sản phẩm
     * @return ProductResponse DTO đầy đủ thông tin
     * @throws com.example.store_clothes.exception.EntityNotFoundException nếu không tìm thấy
     */
    ProductResponse getProductById(Long productId);

    /**
     * Xóa mềm (Soft Delete) một sản phẩm và toàn bộ biến thể con.
     *
     * CASCADE SOFT DELETE FLOW:
     * 1. Tìm Product theo ID (ném EntityNotFoundException nếu không tồn tại).
     * 2. Xóa mềm tất cả ProductVariant con → bulk UPDATE trong 1 query.
     * 3. Xóa mềm Product cha → @SQLDelete tự convert DELETE → UPDATE.
     *
     * Sau khi xóa mềm, sản phẩm và biến thể sẽ không xuất hiện trong
     * bất kỳ query nào do @SQLRestriction("is_deleted = false") tự động lọc.
     *
     * @param productId ID của sản phẩm cần xóa
     * @throws com.example.store_clothes.exception.EntityNotFoundException nếu không tìm thấy
     */
    void deleteProduct(Long productId);

    // =========================================================================
    // TICKET P-01 — Cập nhật thông tin sản phẩm gốc
    // =========================================================================

    /**
     * Cập nhật thông tin sản phẩm gốc (name, description, category).
     *
     * ⚠️ KHÔNG được phép thay đổi code sản phẩm sau khi tạo.
     * Lý do: code là khóa nghiệp vụ bất biến được tham chiếu từ OrderItem snapshot
     * và tất cả hệ thống tích hợp bên ngoài.
     *
     * @param productId ID sản phẩm cần cập nhật
     * @param request   DTO chứa thông tin mới (name, description, categoryId)
     * @return ProductResponse DTO sau khi cập nhật
     */
    ProductResponse updateProduct(Long productId, UpdateProductRequest request);

    // =========================================================================
    // TICKET PV-01 — Thêm biến thể đơn lẻ vào sản phẩm
    // =========================================================================

    /**
     * Thêm một biến thể mới vào sản phẩm đang tồn tại.
     *
     * Business rules:
     * - Sản phẩm phải ACTIVE.
     * - SKU (nếu truyền vào) phải unique toàn hệ thống.
     * - Nếu SKU null → tự sinh bằng VietnameseUtil.generateSku() + resolveSkuConflict().
     * - Nếu initialInventory > 0 → ghi StockHistory với type=ADJUSTMENT, ref="INIT".
     *
     * @param productId ID sản phẩm cha
     * @param request   DTO chứa thông tin biến thể mới
     * @return VariantResponse DTO của biến thể vừa tạo
     */
    VariantResponse addVariant(Long productId, AddVariantRequest request);

    // =========================================================================
    // TICKET PV-02 — Cập nhật giá và thuộc tính biến thể
    // =========================================================================

    /**
     * Cập nhật giá và/hoặc trạng thái của biến thể (patch semantics: null = không đổi).
     *
     * Sử dụng @Version (Optimistic Lock) trên ProductVariant để phát hiện
     * xung đột đồng thời. Nếu 2 manager cùng cập nhật → người sau nhận HTTP 409.
     *
     * @param variantId ID biến thể cần cập nhật
     * @param request   DTO chứa giá mới và/hoặc status mới
     * @return VariantResponse DTO sau khi cập nhật
     */
    VariantResponse updateVariant(Long variantId, UpdateVariantRequest request);

    // =========================================================================
    // TICKET PV-02b — Điều chỉnh tồn kho thủ công
    // =========================================================================

    /**
     * Điều chỉnh tồn kho về số lượng mới chỉ định (absolute, không phải delta).
     *
     * Chỉ ROLE_OWNER được phép thực hiện (enforce tại Controller với @PreAuthorize).
     * Dùng Pessimistic Lock để đảm bảo không có race condition.
     * Bắt buộc ghi StockHistory + AuditLog @Async.
     *
     * @param variantId ID biến thể cần điều chỉnh
     * @param request   DTO chứa newQuantity và reason bắt buộc
     */
    void adjustStock(Long variantId, StockAdjustmentRequest request);

    // =========================================================================
    // TICKET PV-03 — Xóa mềm biến thể
    // =========================================================================

    /**
     * Xóa mềm một biến thể sản phẩm.
     *
     * Điều kiện:
     * - inventory phải = 0.
     * - Không đang nằm trong ImportReceipt DRAFT nào.
     *
     * @SQLDelete tự động rename SKU để giải phóng UNIQUE constraint.
     *
     * @param variantId ID biến thể cần xóa
     */
    void deleteVariant(Long variantId);
}
