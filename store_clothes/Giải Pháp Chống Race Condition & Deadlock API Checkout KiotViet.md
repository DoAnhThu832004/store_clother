# **Tài Liệu Kỹ Thuật: Giải Quyết Race Condition & Deadlock \- KiotViet Clone**

Xin chào, với tư cách là Senior Backend Engineer, tôi đã phân tích luồng code API /api/v1/orders/checkout mà bạn đang xây dựng. Kiến trúc tổng thể và cách tiếp cận lưu snapshot cho OrderItem của bạn rất chuẩn xác. Tuy nhiên, để hệ thống thực sự "bulletproof" (chống đạn) trước tải cao và các giao dịch đồng thời, chúng ta cần hoàn thiện thêm lớp Repository và Exception Handling.

## **1\. Cơ Chế Chống Âm Kho & Deadlock**

* **Chống Deadlock (Sắp xếp theo ID):** Bằng cách sắp xếp request.getItems() theo variantId tăng dần trước khi lặp, chúng ta ép tất cả các luồng (thread) phải khóa các row trong database theo cùng một thứ tự. Điều này loại bỏ hoàn toàn rủi ro Circular Wait (Chờ chéo) gây ra Deadlock ở cấp độ Database.  
* **Chống Âm Kho (Pessimistic Locking):** Sử dụng SELECT FOR UPDATE thông qua @Lock(LockModeType.PESSIMISTIC\_WRITE). Luồng thứ hai chạm vào cùng một variantId sẽ phải chờ luồng thứ nhất commit transaction rồi mới được đọc số lượng tồn kho mới nhất.

## **2\. Bổ Sung Mã Nguồn Còn Thiếu**

### **2.1. ProductVariantRepository (Cấu hình Lock & Timeout)**

Bạn cần cấu hình thêm timeout để tránh việc thread bị treo vĩnh viễn nếu có sự cố xảy ra khóa chết (mặc dù đã sort, nhưng vẫn nên phòng thủ).

package com.kiotviet.repository;

import com.kiotviet.entity.ProductVariant;  
import jakarta.persistence.LockModeType;  
import jakarta.persistence.QueryHint;  
import org.springframework.data.jpa.repository.JpaRepository;  
import org.springframework.data.jpa.repository.Lock;  
import org.springframework.data.jpa.repository.Query;  
import org.springframework.data.jpa.repository.QueryHints;  
import org.springframework.data.repository.query.Param;  
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository  
public interface ProductVariantRepository extends JpaRepository\<ProductVariant, Long\> {

    // Khóa bi quan chống Over-selling  
    @Lock(LockModeType.PESSIMISTIC\_WRITE)  
    // Cài đặt timeout 3000ms (3 giây) để không làm treo thread pool nếu DB gặp sự cố  
    @QueryHints({@QueryHint(name \= "jakarta.persistence.lock.timeout", value \= "3000")})  
    @Query("SELECT v FROM ProductVariant v WHERE v.id \= :id")  
    Optional\<ProductVariant\> findByIdForUpdate(@Param("id") Long id);  
}

### **2.2. Custom Exception & Global Exception Handler (Cấu hình JSON trả về)**

Đảm bảo Client nhận được mã lỗi rõ ràng để hiển thị popup cho thu ngân thay vì lỗi 500 chung chung.

package com.kiotviet.exception;

import lombok.Getter;

@Getter  
public class InsufficientStockException extends RuntimeException {  
    private final String sku;  
    private final int requested;  
    private final int available;

    public InsufficientStockException(String sku, int requested, int available) {  
        super(String.format("Mặt hàng %s không đủ tồn kho. Yêu cầu: %d, Hiện có: %d", sku, requested, available));  
        this.sku \= sku;  
        this.requested \= requested;  
        this.available \= available;  
    }  
}

package com.kiotviet.exception;

import com.kiotviet.dto.response.ApiResponse;  
import lombok.extern.slf4j.Slf4j;  
import org.springframework.http.HttpStatus;  
import org.springframework.http.ResponseEntity;  
import org.springframework.web.bind.annotation.ExceptionHandler;  
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j  
@RestControllerAdvice  
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientStockException.class)  
    public ResponseEntity\<ApiResponse\<Object\>\> handleInsufficientStock(InsufficientStockException ex) {  
        log.warn("Lỗi tồn kho: {}", ex.getMessage());  
        return ResponseEntity.status(HttpStatus.BAD\_REQUEST)  
                .body(ApiResponse.error(HttpStatus.BAD\_REQUEST.value(), ex.getMessage()));  
    }  
      
    // Catch thêm lỗi CannotAcquireLockException nếu timeout khóa DB  
    @ExceptionHandler(org.springframework.dao.CannotAcquireLockException.class)  
    public ResponseEntity\<ApiResponse\<Object\>\> handleLockException(Exception ex) {  
        log.error("Hệ thống bận, không thể khóa bản ghi: {}", ex.getMessage());  
        return ResponseEntity.status(HttpStatus.CONFLICT)  
                .body(ApiResponse.error(HttpStatus.CONFLICT.value(), "Hệ thống đang xử lý giao dịch khác cho mặt hàng này. Vui lòng thử lại."));  
    }  
}

## **3\. Đánh Giá Code OrderService & Các Lưu Ý Thêm (Senior Review)**

Phần code OrderService.java bạn cung cấp đã thực hiện rất tốt bước 1 (Sắp xếp) và bước 2 (Deduct \+ Snapshot). Tuy nhiên, tôi xin bổ sung một số điểm tối ưu về mặt hệ thống như sau:

| Điểm Cần Lưu Ý | Mô Tả & Giải Pháp   |
| :---- | :---- |
| **Khả năng bị Lock Timeout** | Khi có flash sale, nhiều thu ngân mua cùng 1 món sẽ sinh ra hàng đợi. Nếu database xử lý không kịp trong 3s (cấu hình ở trên), nó sẽ throw PessimisticLockException. Việc có GlobalExceptionHandler bắt lỗi CONFLICT là điều bắt buộc. Nếu muốn tự động thử lại, hãy nghiên cứu thêm annotation @Retryable của Spring. |
| **Giao dịch dài (Long Transaction)** | Transaction @Transactional hiện tại đang bao bọc từ lúc lấy Data, gọi kho, lưu Order và cập nhật Khách Hàng. Tránh thực hiện gọi API bên thứ 3 (ví dụ gửi SMS/Email) bên trong transaction này. Hãy dùng @Async và Message Queue (RabbitMQ/Kafka) hoặc Spring ApplicationEvent cho các tác vụ phụ trợ. |
| **Vấn đề Index Database** | Bạn đã tạo Index rất chuẩn xác trên Entity. Lời khuyên là hãy chắc chắn cơ sở dữ liệu của bạn sử dụng Storage Engine hỗ trợ Row-level locking (Ví dụ: InnoDB trong MySQL/MariaDB, hoặc PostgreSQL). Nếu dùng MyISAM, lệnh khóa row sẽ bị nâng lên thành Table-level locking, gây nghẽn hiệu năng toàn bộ hệ thống. |

