package com.example.store_clothes.service;

import com.example.store_clothes.dto.request.CheckoutRequest;
import com.example.store_clothes.dto.response.OrderResponse;
import com.example.store_clothes.entity.*;
import com.example.store_clothes.enums.TransactionType;
import com.example.store_clothes.exception.BusinessException;
import com.example.store_clothes.exception.EntityNotFoundException;
import com.example.store_clothes.exception.InsufficientStockException;
import com.example.store_clothes.repository.OrderRepository;
import com.example.store_clothes.repository.ProductVariantRepository;
import com.example.store_clothes.repository.StockHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * OrderService - Tầng nghiệp vụ cho luồng thanh toán hóa đơn POS.
 *
 * ====================================================================
 * KIẾN TRÚC CHỐNG RACE CONDITION & DEADLOCK — ĐỌC TRƯỚC KHI SỬA:
 * ====================================================================
 *
 * [1] CHỐNG DEADLOCK — Thuật toán sắp xếp tăng dần theo variantId:
 *     Vấn đề: Thread A giữ lock variantId=1, chờ lock variantId=2.
 *             Thread B giữ lock variantId=2, chờ lock variantId=1.
 *             → Circular Wait → Deadlock.
 *     Giải pháp: Ép tất cả thread phải LUÔN LUÔN acquire lock theo
 *     thứ tự variantId ASC. Thread B không thể giữ lock id=2 trước
 *     khi acquire id=1 → Circular Wait bị phá vỡ hoàn toàn.
 *     Code: request.getItems().sort(Comparator.comparing(...variantId))
 *
 * [2] CHỐNG ÂM KHO (Over-selling) — Pessimistic Write Lock:
 *     Vấn đề: Thread A đọc inventory=1, Thread B đọc inventory=1.
 *             Cả hai cùng trừ 1 → cả hai ghi lại 0 → thực tế âm kho.
 *     Giải pháp: variantRepository.findByIdForUpdate() phát sinh
 *     câu SQL: SELECT ... FOR UPDATE — DB cấp exclusive row-level lock.
 *     Thread B phải chờ Thread A COMMIT trước mới đọc được → đọc
 *     đúng giá trị inventory sau khi Thread A đã trừ.
 *
 * [3] SNAPSHOT GIÁ:
 *     priceAtSale và importPriceAtSale được copy từ entity tại thời điểm
 *     checkout. Sau khi INSERT → bất biến (immutable). Tuyệt đối không
 *     JOIN bảng product_variants khi tính báo cáo doanh thu lịch sử.
 *
 * [4] ATOMICITY:
 *     @Transactional đảm bảo toàn bộ checkout (trừ kho + ghi thẻ kho +
 *     lưu hóa đơn) là một đơn vị nguyên tử. Nếu một bước thất bại →
 *     Spring rollback toàn bộ → tồn kho không bị trừ nhầm.
 * ====================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductVariantRepository variantRepository;
    private final StockHistoryRepository stockHistoryRepository;

    // Format mã hóa đơn: HD-YYYYMMDD-XXXX
    private static final String ORDER_CODE_PREFIX = "HD-";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // =========================================================================
    // CHECKOUT — Luồng thanh toán hóa đơn chính
    // =========================================================================

    /**
     * Xử lý thanh toán hóa đơn POS.
     *
     * Luồng thực thi (5 bước):
     * 1. Sort items theo variantId ASC  → phá vỡ Circular Wait, chống Deadlock.
     * 2. Acquire Pessimistic Write Lock → chống Over-selling (Race Condition).
     * 3. Validate & trừ tồn kho        → InsufficientStockException nếu âm kho.
     * 4. Snapshot giá + ghi thẻ kho    → Audit trail bất biến.
     * 5. Lưu hóa đơn + trả response    → Commit transaction, release tất cả lock.
     *
     * @param request Dữ liệu giỏ hàng từ Client (đã pass qua @Valid)
     * @return OrderResponse với mã hóa đơn và chi tiết snapshot
     * @throws InsufficientStockException    Khi tồn kho < số lượng yêu cầu (HTTP 409)
     * @throws EntityNotFoundException       Khi variantId không tồn tại     (HTTP 404)
     * @throws BusinessException             Khi paidAmount < totalAmount     (HTTP 400)
     * @throws org.springframework.dao.CannotAcquireLockException
     *                                       Khi timeout lock DB              (HTTP 409)
     */
    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        log.info("=== BẮT ĐẦU CHECKOUT: {} items ===", request.getItems().size());

        // =====================================================================
        // BƯỚC 1: SẮP XẾP ITEMS THEO variantId TĂNG DẦN
        //
        // ĐÂY LÀ BƯỚC SỐNG CÒN CHỐNG DEADLOCK — TUYỆT ĐỐI KHÔNG BỎ QUA.
        //
        // Kịch bản Deadlock nếu KHÔNG sort:
        //   Thu ngân A checkout {variantId=5, variantId=3} → lock 5 trước, chờ lock 3.
        //   Thu ngân B checkout {variantId=3, variantId=5} → lock 3 trước, chờ lock 5.
        //   → Circular Wait → DB phát hiện Deadlock → kill 1 transaction.
        //
        // Sau khi sort ASC: cả A và B đều phải lock theo thứ tự {3, 5}.
        //   Thread A lock 3, lock 5 → commit → Thread B lock 3, lock 5 → commit.
        //   → Không có Circular Wait → Không có Deadlock.
        // =====================================================================
        List<CheckoutRequest.CheckoutItemRequest> sortedItems = new ArrayList<>(request.getItems());
        sortedItems.sort(Comparator.comparing(CheckoutRequest.CheckoutItemRequest::getVariantId));

        log.debug("Items sau khi sort: {}", sortedItems.stream()
                .map(i -> "variantId=" + i.getVariantId()).toList());

        // Sinh mã hóa đơn sớm (chạy trong cùng transaction → thread-safe) để gán trực tiếp vào thẻ kho bất biến
        String orderCode = generateOrderCode();

        // =====================================================================
        // BƯỚC 2, 3, 4: VÒNG LẶP XỬ LÝ TỪNG ITEM
        //
        // KHÔNG đặt try-catch trong vòng lặp này.
        // Exception phải propagate tự nhiên ra ngoài để @Transactional rollback.
        // =====================================================================
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> itemsToSave = new ArrayList<>();
        List<StockHistory> historiesToSave = new ArrayList<>();

        for (CheckoutRequest.CheckoutItemRequest itemReq : sortedItems) {

            // -------------------------------------------------------
            // BƯỚC 2: ACQUIRE PESSIMISTIC WRITE LOCK
            //
            // findByIdForUpdate() phát sinh: SELECT ... FOR UPDATE
            // → DB cấp exclusive row-level lock trên row ProductVariant này.
            // → Thread kế tiếp muốn lock cùng row phải CHỜ (block)
            //   cho đến khi transaction hiện tại commit/rollback.
            // → Timeout = 3000ms (cấu hình trong Repository)
            //   → Nếu chờ > 3s → ném LockTimeoutException
            //   → Spring wrap thành CannotAcquireLockException
            //   → GlobalExceptionHandler trả về HTTP 409.
            // -------------------------------------------------------
            ProductVariant variant = variantRepository.findByIdForUpdate(itemReq.getVariantId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Biến thể sản phẩm không tồn tại với ID: " + itemReq.getVariantId()));

            log.debug("Đã acquire lock variantId={}, SKU={}, inventory hiện tại={}",
                    variant.getId(), variant.getSku(), variant.getInventory());

            // -------------------------------------------------------
            // BƯỚC 3: VALIDATE & TRỪ TỒN KHO
            //
            // Tại đây, giá trị inventory đọc được là CHÍNH XÁC vì đã lock.
            // Nếu Thread A đã trừ kho và commit → Thread B sẽ thấy giá trị
            // inventory đã được trừ, không phải giá trị cũ trước khi trừ.
            // -------------------------------------------------------
            int currentInventory = variant.getInventory();
            int requestedQty = itemReq.getQuantity();

            if (currentInventory < requestedQty) {
                // Throw ngay lập tức — @Transactional sẽ rollback tất cả
                // lock đã acquire trong vòng lặp này cũng được release.
                log.warn("Tồn kho không đủ: SKU={}, inventory={}, requested={}",
                        variant.getSku(), currentInventory, requestedQty);
                throw new InsufficientStockException(
                        variant.getSku(), requestedQty, currentInventory);
            }

            // Trừ tồn kho — giá trị mới sẽ được flush xuống DB khi transaction commit
            int updatedInventory = currentInventory - requestedQty;
            variant.setInventory(updatedInventory);
            variantRepository.save(variant);

            log.debug("Đã trừ kho variantId={}: {} → {} (trừ {})",
                    variant.getId(), currentInventory, updatedInventory, requestedQty);

            // -------------------------------------------------------
            // BƯỚC 4A: SNAPSHOT GIÁ — Tạo OrderItem bất biến
            //
            // priceAtSale = copy từ variant.getSalePrice() LÚC NÀY.
            // importPriceAtSale = copy từ variant.getImportPrice() LÚC NÀY.
            //
            // Sau khi INSERT vào DB → 2 trường này không bao giờ bị update.
            // Giá sản phẩm có thể thay đổi tháng sau — snapshot đảm bảo
            // báo cáo doanh thu tháng này vẫn chính xác.
            // -------------------------------------------------------
            BigDecimal priceAtSale    = variant.getSalePrice()   != null
                    ? variant.getSalePrice()   : BigDecimal.ZERO;
            BigDecimal importAtSale   = variant.getImportPrice() != null
                    ? variant.getImportPrice() : BigDecimal.ZERO;
            BigDecimal lineTotal      = priceAtSale.multiply(BigDecimal.valueOf(requestedQty));

            // Tên snapshot: "Tên sản phẩm - Màu - Size"
            String productNameAtSale = buildProductNameSnapshot(variant);

            OrderItem orderItem = OrderItem.builder()
                    .variantId(variant.getId())
                    .skuAtSale(variant.getSku())           // Snapshot SKU
                    .productNameAtSale(productNameAtSale)  // Snapshot tên
                    .quantity(requestedQty)
                    .priceAtSale(priceAtSale)              // [SNAPSHOT] Giá bán
                    .importPriceAtSale(importAtSale)       // [SNAPSHOT] Giá nhập
                    .lineTotal(lineTotal)
                    .build();
            itemsToSave.add(orderItem);

            totalAmount = totalAmount.add(lineTotal);

            // -------------------------------------------------------
            // BƯỚC 4B: GHI THẺ KHO — Audit trail bất biến
            //
            // CHỈ INSERT — không UPDATE/DELETE bản ghi thẻ kho bao giờ.
            // Số lượng âm (-requestedQty) để thể hiện XUẤT KHO (EXPORT).
            // -------------------------------------------------------
            StockHistory history = StockHistory.builder()
                    .variantId(variant.getId())
                    .changeQuantity(-requestedQty)                // Âm vì là XUẤT KHO
                    .transactionType(TransactionType.EXPORT)
                    .referenceCode(orderCode)                     // Gán trực tiếp mã hóa đơn
                    .balanceBefore(currentInventory)
                    .balanceAfter(updatedInventory)
                    .createdAt(LocalDateTime.now())
                    .build();
            historiesToSave.add(history);
        }

        // =====================================================================
        // BƯỚC 5: VALIDATE SỐ TIỀN & LƯU HÓA ĐƠN
        // =====================================================================

        // Validate paidAmount đủ trả
        if (request.getPaidAmount().compareTo(totalAmount) < 0) {
            throw new BusinessException(String.format(
                    "Số tiền khách đưa (%.0f) nhỏ hơn tổng tiền hàng (%.0f).",
                    request.getPaidAmount(), totalAmount));
        }

        BigDecimal changeAmount = request.getPaidAmount().subtract(totalAmount);

        // Tạo hóa đơn cha
        Order order = Order.builder()
                .orderCode(orderCode)
                .totalAmount(totalAmount)
                .paidAmount(request.getPaidAmount())
                .changeAmount(changeAmount)
                .note(request.getNote())
                .build();

        // Liên kết bidirectional trước khi save
        itemsToSave.forEach(order::addItem);

        // Một lần save duy nhất — cascade ALL sẽ INSERT cả OrderItem
        Order savedOrder = orderRepository.save(order);

        // Batch INSERT tất cả thẻ kho (tối ưu bằng rewriteBatchedStatements=true)
        stockHistoryRepository.saveAll(historiesToSave);

        log.info("=== CHECKOUT THÀNH CÔNG: orderCode={}, totalAmount={}, items={} ===",
                orderCode, totalAmount, sortedItems.size());

        return mapToResponse(savedOrder);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Sinh mã hóa đơn tự động theo format: HD-YYYYMMDD-XXXX.
     *
     * Thuật toán giống generateReceiptCode() trong ImportReceiptService.
     * Thread-safe vì chạy trong @Transactional — chỉ 1 thread commit
     * INSERT + SELECT MAX cùng lúc.
     */
    private String generateOrderCode() {
        String today = LocalDateTime.now().format(DATE_FORMATTER);
        String prefix = ORDER_CODE_PREFIX + today + "-";

        return orderRepository.findMaxOrderCodeByPrefix(prefix)
                .map(maxCode -> {
                    String seqStr = maxCode.substring(prefix.length());
                    int nextSeq = Integer.parseInt(seqStr) + 1;
                    return prefix + String.format("%04d", nextSeq);
                })
                .orElse(prefix + "0001");
    }

    /**
     * Tạo tên snapshot cho OrderItem từ entity lúc checkout.
     * Format: "{Tên sản phẩm} - {Màu} - {Size}"
     *
     * Tên Product được lấy từ LAZY-loaded relationship.
     * Vì đang trong transaction, Hibernate sẽ thực hiện thêm 1 query
     * để load Product.name — chấp nhận được vì chỉ xảy ra 1 lần/item.
     *
     * Senior Note: Nếu hiệu năng quan trọng, dùng JOIN FETCH variant với
     * product trong findByIdForUpdate() thay vì LAZY load tại đây.
     */
    private String buildProductNameSnapshot(ProductVariant variant) {
        StringBuilder sb = new StringBuilder();

        // Load tên sản phẩm từ LAZY relationship (1 query extra/item — acceptable)
        if (variant.getProduct() != null && variant.getProduct().getName() != null) {
            sb.append(variant.getProduct().getName());
        }
        if (variant.getColor() != null && !variant.getColor().isBlank()) {
            sb.append(" - ").append(variant.getColor());
        }
        if (variant.getSize() != null && !variant.getSize().isBlank()) {
            sb.append(" - ").append(variant.getSize());
        }
        return sb.toString();
    }

    /**
     * Map Order entity sang OrderResponse DTO.
     * Tất cả giá trong response là snapshot — đọc thẳng từ OrderItem.
     */
    private OrderResponse mapToResponse(Order order) {
        List<OrderResponse.OrderItemSummary> itemSummaries = order.getItems().stream()
                .map(item -> OrderResponse.OrderItemSummary.builder()
                        .variantId(item.getVariantId())
                        .skuAtSale(item.getSkuAtSale())
                        .productNameAtSale(item.getProductNameAtSale())
                        .quantity(item.getQuantity())
                        .priceAtSale(item.getPriceAtSale())
                        .importPriceAtSale(item.getImportPriceAtSale())
                        .lineTotal(item.getLineTotal())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .totalAmount(order.getTotalAmount())
                .paidAmount(order.getPaidAmount())
                .changeAmount(order.getChangeAmount())
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .items(itemSummaries)
                .build();
    }
}
