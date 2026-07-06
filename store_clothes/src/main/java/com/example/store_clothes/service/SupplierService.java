package com.example.store_clothes.service;

import com.example.store_clothes.dto.request.CreateSupplierRequest;
import com.example.store_clothes.dto.request.UpdateSupplierRequest;
import com.example.store_clothes.dto.response.SupplierResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * SupplierService — Interface định nghĩa contract cho module Nhà Cung Cấp.
 */
public interface SupplierService {

    /**
     * SUP-01: Tạo nhà cung cấp mới.
     * Validate phone unique trong NCC active.
     */
    SupplierResponse createSupplier(CreateSupplierRequest request);

    /**
     * SUP-02: Danh sách phân trang với filter keyword và hasDebt.
     * hasDebt=true → chỉ lấy NCC đang nợ (debtAmount > 0).
     */
    Page<SupplierResponse> getSuppliers(String keyword, Boolean hasDebt, Pageable pageable);

    /**
     * SUP-03: Chi tiết NCC kèm 5 phiếu nhập gần nhất.
     */
    SupplierResponse getSupplierById(Long id);

    /**
     * SUP-04: Cập nhật NCC với Optimistic Lock.
     * KHÔNG cho phép sửa debtAmount qua đây.
     */
    SupplierResponse updateSupplier(Long id, UpdateSupplierRequest request);

    /**
     * SUP-05: Xóa mềm NCC.
     * Điều kiện: debtAmount = 0 và không có phiếu nhập DRAFT.
     * Ghi AuditLog @Async.
     */
    void deleteSupplier(Long id);
}
