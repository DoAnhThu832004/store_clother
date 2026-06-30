# **TÀI LIỆU ĐẶC TẢ VÀ BÀN GIAO MÃ NGUỒN**

*Module: Nhập Hàng & Ghi Thẻ Kho (Stock Ledger) | Vai trò: Senior Backend Engineer Review*

## **1\. ĐÁNH GIÁ CHUYÊN MÔN TỪ SENIOR BACKEND ENGINEER**

### **1.1 Điểm Sáng Trong Thiết Kế Hệ Thống (Ưu điểm)**

* **Kiểm soát bất đồng bộ (Concurrency Control):** Sử dụng cơ chế khóa bi quan *Pessimistic Locking* (thông qua hàm findByIdForUpdate trên dòng dữ liệu ProductVariant) cực kỳ chuẩn xác, loại bỏ triệt để lỗi Race Condition khi nhiều phiếu nhập hoặc đơn bán hàng đồng thời cập nhật kho.  
* **Thiết kế Thẻ kho (Stock History) tối ưu:** Việc phân rã và lưu trữ tường minh cả hai trạng thái balanceBefore và balanceAfter tạo ra một chuỗi Audit Trail hoàn hảo, hỗ trợ tối đa việc đối soát dữ liệu và khôi phục dòng lịch sử khi phát hiện chênh lệch.  
* **Tối ưu hóa câu lệnh Sequence:** Sử dụng Native Query xử lý hàm MAX kết hợp toán tử LIKE trên chuỗi mã hóa ngày giúp tăng đáng kể hiệu năng truy vấn thông qua Index sẵn có thay vì quét toàn bộ bảng dữ liệu.

### **1.2 Khuyến Nghị Nâng Cấp Khi Scale Lớn (Lưu ý)**

* **Cấu hình Batch Insert:** Cần kích hoạt tham số rewriteBatchedStatements=true (đối với MySQL) trong cấu hình kết nối ứng dụng nhằm tối ưu lệnh stockHistoryRepository.saveAll() thành một câu lệnh duy nhất khi số lượng mặt hàng trong phiếu nhập lớn.  
* **Tách biệt Transaction sinh mã:** Nếu tần suất tạo phiếu nháp (Draft) cực kỳ lớn, việc lấy chuỗi lớn nhất trong DB có thể tạo độ trễ nhỏ. Khuyến nghị dịch chuyển sang Redis Counter nếu hệ thống cần mở rộng chịu tải cao.

## **2\. ĐẶC TẢ YÊU CẦU NGHIỆP VỤ & CÔNG THỨC**

Hệ thống xử lý nghiệp vụ nhập hàng từ nhà cung cấp theo các bước tuần tự được bọc trong một Transaction duy nhất:

* Chuyển trạng thái phiếu từ DRAFT sang COMPLETED.  
* Tăng số lượng tồn kho (inventory) thực tế của từng biến thể hàng hóa.  
* Cập nhật giá nhập (importPrice) mới nhất cho sản phẩm.  
* Ghi nhận một bản ghi bất biến vào thẻ kho (StockHistory) với số lượng biến động mang dấu dương (+).  
* Tái tính toán công nợ của nhà cung cấp dựa trên công thức tài chính dưới đây:

| Công thức tính công nợ mới của Nhà cung cấp |
| :---: |
| **Debt\_mới \= Debt\_cũ \+ TotalAmount \- PaidAmount** |

## **3\. CHI TIẾT MÃ NGUỒN BOILERPLATE BÀN GIAO**

### **3.1 Tầng DTO (Data Transfer Object)**

**File: CreateImportReceiptRequest.java**

package com.kiotviet.dto.request;

import jakarta.validation.Valid;  
import jakarta.validation.constraints.\*;  
import lombok.Data;  
import java.math.BigDecimal;  
import java.util.List;

@Data  
public class CreateImportReceiptRequest {  
    @NotNull(message \= "Nhà cung cấp không được để trống")  
    private Long supplierId;

    @NotEmpty(message \= "Danh sách hàng nhập không được rỗng")  
    @Valid  
    private List\<ImportDetailRequest\> items;

    private BigDecimal paidAmount;  
    private String note;

    @Data  
    public static class ImportDetailRequest {  
        @NotNull(message \= "ID biến thể không được để trống")  
        private Long variantId;

        @NotNull @Positive(message \= "Số lượng phải lớn hơn 0")  
        private Integer quantity;

        @NotNull @Positive(message \= "Giá nhập phải lớn hơn 0")  
        private BigDecimal importPrice;  
    }  
}

### **3.2 Tầng Cơ sở Dữ liệu (Entities)**

**File: Supplier.java**

package com.kiotviet.entity;

import jakarta.persistence.\*;  
import lombok.\*;  
import java.math.BigDecimal;

@Entity  
@Table(name \= "suppliers")  
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder  
public class Supplier extends BaseEntity {  
    @Column(name \= "name", nullable \= false, length \= 200\)  
    private String name;

    @Column(name \= "phone", length \= 20\)  
    private String phone;

    @Column(name \= "address", length \= 500\)  
    private String address;

    @Column(name \= "debt\_amount", nullable \= false, precision \= 15, scale \= 2\)  
    @Builder.Default  
    private BigDecimal debtAmount \= BigDecimal.ZERO;  
}

**File: ImportReceipt.java**

package com.kiotviet.entity;

import com.kiotviet.enums.ImportReceiptStatus;  
import jakarta.persistence.\*;  
import lombok.\*;  
import java.math.BigDecimal;  
import java.util.ArrayList;  
import java.util.List;

@Entity  
@Table(name \= "import\_receipts")  
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder  
public class ImportReceipt extends BaseEntity {  
    @Column(name \= "receipt\_code", nullable \= false, unique \= true, length \= 30\)  
    private String receiptCode;

    @ManyToOne(fetch \= FetchType.LAZY)  
    @JoinColumn(name \= "supplier\_id", nullable \= false)  
    private Supplier supplier;

    @Column(name \= "total\_amount", nullable \= false, precision \= 15, scale \= 2\)  
    private BigDecimal totalAmount;

    @Column(name \= "paid\_amount", nullable \= false, precision \= 15, scale \= 2\)  
    private BigDecimal paidAmount;

    @Enumerated(EnumType.STRING)  
    @Column(name \= "status", nullable \= false, length \= 20\)  
    private ImportReceiptStatus status;

    @OneToMany(mappedBy \= "receipt", cascade \= CascadeType.ALL, orphanRemoval \= true)  
    @Builder.Default  
    private List\<ImportReceiptDetail\> details \= new ArrayList\<\>();  
}

**File: StockHistory.java (Bất Biến \- Không Được Update/Delete)**

package com.kiotviet.entity;

import com.kiotviet.enums.TransactionType;  
import jakarta.persistence.\*;  
import lombok.\*;  
import java.time.LocalDateTime;

@Entity  
@Table(name \= "stock\_history")  
@Getter @NoArgsConstructor @AllArgsConstructor @Builder  
public class StockHistory {  
    @Id  
    @GeneratedValue(strategy \= GenerationType.IDENTITY)  
    private Long id;

    @Column(name \= "variant\_id", nullable \= false)  
    private Long variantId;

    @Column(name \= "change\_quantity", nullable \= false)  
    private Integer changeQuantity;

    @Enumerated(EnumType.STRING)  
    @Column(name \= "transaction\_type", nullable \= false, length \= 20\)  
    private TransactionType transactionType;

    @Column(name \= "reference\_code", length \= 30\)  
    private String referenceCode;

    @Column(name \= "balance\_before", nullable \= false)  
    private Integer balanceBefore;

    @Column(name \= "balance\_after", nullable \= false)  
    private Integer balanceAfter;

    @Column(name \= "created\_at", nullable \= false)  
    private LocalDateTime createdAt;  
}

### **3.3 Tầng Xử lý Nghiệp vụ (Service)**

**File: ImportReceiptService.java**

package com.kiotviet.service;

import com.kiotviet.dto.request.CreateImportReceiptRequest;  
import com.kiotviet.entity.\*;  
import com.kiotviet.enums.ImportReceiptStatus;  
import com.kiotviet.enums.TransactionType;  
import com.kiotviet.exception.BusinessException;  
import com.kiotviet.exception.EntityNotFoundException;  
import com.kiotviet.repository.\*;  
import lombok.RequiredArgsConstructor;  
import org.springframework.stereotype.Service;  
import org.springframework.transaction.annotation.Transactional;  
import java.math.BigDecimal;  
import java.time.LocalDateTime;  
import java.util.ArrayList;  
import java.util.List;

@Service  
@RequiredArgsConstructor  
public class ImportReceiptService {  
    private final ImportReceiptRepository receiptRepository;  
    private final ProductVariantRepository variantRepository;  
    private final StockHistoryRepository stockHistoryRepository;  
    private final SupplierRepository supplierRepository;

    @Transactional  
    public ImportReceipt completeReceipt(Long receiptId) {  
        ImportReceipt receipt \= receiptRepository.findById(receiptId)  
                .orElseThrow(() \-\> new EntityNotFoundException("Phiếu nhập không tồn tại", receiptId));

        if (receipt.getStatus() \!= ImportReceiptStatus.DRAFT) {  
            throw new BusinessException("Chỉ cho phép hoàn thành phiếu nhập từ trạng thái DRAFT");  
        }

        receipt.setStatus(ImportReceiptStatus.COMPLETED);  
        List\<StockHistory\> histories \= new ArrayList\<\>();

        for (ImportReceiptDetail detail : receipt.getDetails()) {  
            ProductVariant variant \= variantRepository.findByIdForUpdate(detail.getVariant().getId())  
                    .orElseThrow(() \-\> new EntityNotFoundException("Biến thể không tồn tại", detail.getVariant().getId()));

            int balanceBefore \= variant.getInventory();  
            int balanceAfter \= balanceBefore \+ detail.getQuantity();

            variant.setInventory(balanceAfter);  
            variant.setImportPrice(detail.getImportPrice());  
            variantRepository.save(variant);

            StockHistory history \= StockHistory.builder()  
                    .variantId(variant.getId())  
                    .changeQuantity(detail.getQuantity())  
                    .transactionType(TransactionType.IMPORT)  
                    .referenceCode(receipt.getReceiptCode())  
                    .balanceBefore(balanceBefore)  
                    .balanceAfter(balanceAfter)  
                    .createdAt(LocalDateTime.now())  
                    .build();  
            histories.add(history);  
        }

        stockHistoryRepository.saveAll(histories);

        Supplier supplier \= receipt.getSupplier();  
        BigDecimal currentDebt \= supplier.getDebtAmount();  
        BigDecimal addedDebt \= receipt.getTotalAmount().subtract(receipt.getPaidAmount());  
        supplier.setDebtAmount(currentDebt.add(addedDebt));  
        supplierRepository.save(supplier);

        return receiptRepository.save(receipt);  
    }  
}

### **3.4 Tầng Kiểm Soát Đầu Vào (Controller)**

**File: ImportReceiptController.java**

package com.kiotviet.controller;

import com.kiotviet.dto.response.ApiResponse;  
import com.kiotviet.entity.ImportReceipt;  
import com.kiotviet.service.ImportReceiptService;  
import lombok.RequiredArgsConstructor;  
import org.springframework.http.ResponseEntity;  
import org.springframework.web.bind.annotation.\*;

@RestController  
@RequestMapping("/api/v1/imports")  
@RequiredArgsConstructor  
public class ImportReceiptController {  
    private final ImportReceiptService importReceiptService;

    @PostMapping("/{id}/complete")  
    public ResponseEntity\<ApiResponse\<ImportReceipt\>\> complete(@PathVariable Long id) {  
        ImportReceipt completed \= importReceiptService.completeReceipt(id);  
        return ResponseEntity.ok(ApiResponse.success(completed));  
    }  
}

## **4\. HƯỚNG DẪN BÀN GIAO CHO DEVELOPER**

* **Tính toàn vẹn (Atomicity):** Tuyệt đối không bắt catch ngoại lệ bên trong vòng lặp của Service mà không throw ngược lại, nhằm đảm bảo cơ chế rollback tự động của @Transactional hoạt động chính xác khi có bất kỳ dòng hàng nào bị lỗi dữ liệu.  
* **Tính bất biến của Thẻ kho:** Nghiêm cấm tạo các hàm update hoặc delete dữ liệu liên quan đến bảng stock\_history dưới mọi hình thức nhằm bảo vệ tính toàn vẹn của dữ liệu kiểm toán.