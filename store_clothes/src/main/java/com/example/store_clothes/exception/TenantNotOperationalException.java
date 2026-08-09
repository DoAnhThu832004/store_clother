package com.example.store_clothes.exception;

/**
 * TenantNotOperationalException — Exception khi cửa hàng (Tenant) không thể hoạt động.
 *
 * PHÂN BIỆT VỚI BusinessException:
 * - BusinessException: Lỗi nghiệp vụ thông thường (validation, not found...).
 * - TenantNotOperationalException: Lỗi ở tầng PLATFORM — cửa hàng bị chặn ở cấp độ hệ thống.
 *   → Không phụ thuộc vào user đang làm gì, mà phụ thuộc vào trạng thái cửa hàng.
 *
 * THIẾT KẾ:
 * Kế thừa DomainException với ErrorCode cụ thể (TENANT_SUSPENDED hoặc TENANT_EXPIRED).
 * GlobalExceptionHandler đã có sẵn handler cho DomainException → không cần thêm handler mới.
 *
 * LUỒNG XỬ LÝ:
 *   TenantLifecycleFilter
 *     → phát hiện tenant.status = SUSPENDED
 *     → throw new TenantNotOperationalException(ErrorCode.TENANT_SUSPENDED)
 *     → DomainException handler → HTTP 403 + JSON body
 */
public class TenantNotOperationalException extends DomainException {

    /**
     * Tạo exception với ErrorCode cụ thể.
     * Sử dụng ErrorCode.TENANT_SUSPENDED hoặc ErrorCode.TENANT_EXPIRED.
     *
     * @param errorCode Mã lỗi mô tả lý do không hoạt động
     */
    public TenantNotOperationalException(ErrorCode errorCode) {
        super(errorCode);
    }

    /**
     * Tạo exception với ErrorCode và message tùy chỉnh.
     * Dùng khi cần thông báo kèm thêm thông tin (ví dụ: ngày hết hạn).
     *
     * @param errorCode Mã lỗi
     * @param customMessage Thông báo tùy chỉnh thay thế message mặc định của ErrorCode
     */
    public TenantNotOperationalException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
