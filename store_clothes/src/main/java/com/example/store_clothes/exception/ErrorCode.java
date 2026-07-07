package com.example.store_clothes.exception;

import org.springframework.http.HttpStatus;

/**
 * ErrorCode — Tập trung toàn bộ mã lỗi nghiệp vụ (Domain Error Codes) của hệ thống.
 *
 * NGUYÊN TẮC SỬ DỤNG:
 * - Mỗi lỗi nghiệp vụ PHẢI có một ErrorCode định danh riêng biệt.
 * - Frontend dùng field "code" (string) để ánh xạ ra thông báo lỗi cục bộ phù hợp.
 * - TUYỆT ĐỐI không dùng chuỗi text thô (hardcoded string) làm mã lỗi ở Controller/Service.
 *
 * CÁCH DÙNG:
 *   throw new DomainException(ErrorCode.CATEGORY_HAS_ACTIVE_PRODUCTS);
 *   throw new DomainException(ErrorCode.INSUFFICIENT_STOCK, Map.of("sku", "SKU-001", "available", 5));
 */
public enum ErrorCode {

    // =========================================================================
    // MODULE: SẢN PHẨM & BIẾN THỂ
    // =========================================================================

    /** Mã sản phẩm (product code) đã tồn tại trong hệ thống */
    PRODUCT_CODE_DUPLICATED(HttpStatus.CONFLICT, "Mã sản phẩm này đã tồn tại"),

    /** SKU biến thể bị trùng lặp khi tạo mới hoặc cập nhật */
    VARIANT_SKU_DUPLICATED(HttpStatus.CONFLICT, "Mã SKU biến thể đã tồn tại"),

    /** Biến thể không tìm thấy hoặc đã bị xóa mềm */
    VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy biến thể sản phẩm"),

    /** Số lượng tồn kho không đủ để thực hiện bán hàng / xuất kho */
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Số lượng hàng tồn kho không đủ"),

    /** Tồn kho sau điều chỉnh sẽ bị âm — vi phạm ràng buộc kinh doanh */
    STOCK_ADJUSTMENT_NEGATIVE(HttpStatus.BAD_REQUEST, "Điều chỉnh tồn kho không được làm số lượng bị âm"),

    /** Không thể xóa sản phẩm đang có biến thể còn hoạt động */
    PRODUCT_HAS_ACTIVE_VARIANTS(HttpStatus.CONFLICT, "Không thể xóa sản phẩm đang có biến thể hoạt động"),

    // =========================================================================
    // MODULE: DANH MỤC
    // =========================================================================

    /** Không tìm thấy danh mục */
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục sản phẩm"),

    /** Không thể xóa danh mục đang chứa sản phẩm còn hoạt động */
    CATEGORY_HAS_ACTIVE_PRODUCTS(HttpStatus.CONFLICT, "Không thể xóa danh mục đang chứa sản phẩm đang hoạt động"),

    /** Không thể xóa danh mục cha đang có danh mục con bên dưới */
    CATEGORY_HAS_CHILDREN(HttpStatus.CONFLICT, "Không thể xóa danh mục đang có danh mục con"),

    /** Phát hiện tham chiếu vòng khi cập nhật parentId (CAT-05) */
    CATEGORY_CIRCULAR_REFERENCE(HttpStatus.BAD_REQUEST, "Không thể gán danh mục thành con của chính nó hoặc của danh mục con nó"),

    /** Không thể xóa danh mục cha khi đang có depth > 1 (tạo cháu) */
    CATEGORY_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST, "Không thể tạo danh mục cháu — hệ thống chỉ hỗ trợ tối đa 2 cấp danh mục"),

    // =========================================================================
    // MODULE: NHÀ CUNG CẤP
    // =========================================================================

    /** Không tìm thấy nhà cung cấp */
    SUPPLIER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy nhà cung cấp"),

    /** Không thể xóa nhà cung cấp đang còn dư nợ chưa thanh toán */
    SUPPLIER_OUTSTANDING_DEBT(HttpStatus.CONFLICT, "Không thể xóa nhà cung cấp đang còn công nợ chưa thanh toán"),

    /** Số điện thoại nhà cung cấp đã tồn tại */
    SUPPLIER_PHONE_DUPLICATED(HttpStatus.CONFLICT, "Số điện thoại nhà cung cấp đã được sử dụng"),

    /** Không thể xóa NCC khi còn phiếu nhập ở trạng thái DRAFT */
    SUPPLIER_HAS_DRAFT_RECEIPTS(HttpStatus.CONFLICT, "Không thể xóa nhà cung cấp đang có phiếu nhập chưa hoàn thành"),

    // =========================================================================
    // MODULE: KHÁCH HÀNG
    // =========================================================================

    /** Không tìm thấy khách hàng */
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy khách hàng"),

    /** Số điện thoại khách hàng đã tồn tại (ở KH đang active) */
    CUSTOMER_PHONE_DUPLICATED(HttpStatus.CONFLICT, "Số điện thoại đã được đăng ký bởi khách hàng khác"),

    /** Số điện thoại tồn tại ở KH đã bị xóa mềm — cần xác nhận restore từ người dùng */
    CUSTOMER_PHONE_BELONGS_TO_DELETED(HttpStatus.CONFLICT, "Số điện thoại đã từng được đăng ký bởi một khách hàng đã bị xóa. Vui lòng liên hệ quản lý để khôi phục."),

    /** Không thể xóa khách hàng đang có đơn hàng PENDING chưa hoàn thành */
    CUSTOMER_HAS_PENDING_ORDERS(HttpStatus.CONFLICT, "Không thể xóa khách hàng đang có đơn hàng chưa hoàn thành"),

    // =========================================================================
    // MODULE: PHIẾU NHẬP KHO
    // =========================================================================

    /** Không tìm thấy phiếu nhập kho */
    IMPORT_RECEIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu nhập kho"),

    /** Phiếu nhập đã hoàn thành, không thể chỉnh sửa nữa */
    IMPORT_RECEIPT_NOT_EDITABLE(HttpStatus.CONFLICT, "Chỉ có thể chỉnh sửa phiếu nhập đang ở trạng thái DRAFT"),

    /** Số tiền thanh toán vượt quá tổng giá trị phiếu */
    IMPORT_RECEIPT_PAID_EXCEEDS_TOTAL(HttpStatus.BAD_REQUEST, "Số tiền thanh toán không được vượt quá tổng giá trị phiếu nhập"),

    // =========================================================================
    // MODULE: HÓA ĐƠN / ĐƠN HÀNG
    // =========================================================================

    /** Không tìm thấy hóa đơn */
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy hóa đơn"),

    /** Hóa đơn đã bị hủy trước đó, không thể hủy lại */
    ORDER_ALREADY_CANCELLED(HttpStatus.CONFLICT, "Hóa đơn này đã được hủy trước đó"),

    /** Hóa đơn không ở trạng thái cho phép hủy (chỉ hủy được PAID/COMPLETED) */
    ORDER_NOT_CANCELLABLE(HttpStatus.CONFLICT, "Hóa đơn này không thể hủy ở trạng thái hiện tại"),

    // =========================================================================
    // MODULE: NGƯỜI DÙNG / NHÂN VIÊN
    // =========================================================================

    /** Không tìm thấy người dùng */
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),

    /** Tên đăng nhập đã tồn tại */
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "Tên đăng nhập này đã được sử dụng"),

    /** Email đã được đăng ký bởi tài khoản khác */
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "Email này đã được sử dụng bởi tài khoản khác"),

    // =========================================================================
    // IDEMPOTENCY
    // =========================================================================

    /** Request trùng lặp đã được xử lý trước đó (Redis cache hit) */
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "Request này đã được xử lý, vui lòng không gửi lại"),

    // =========================================================================
    // LỖI HỆ THỐNG CHUNG
    // =========================================================================

    /** Không đủ quyền thực hiện thao tác */
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"),

    /** Lỗi hệ thống không xác định */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau");

    // =========================================================================

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
