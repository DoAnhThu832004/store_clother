package com.example.store_clothes.service;

import com.example.store_clothes.dto.request.CheckoutRequest;
import com.example.store_clothes.dto.response.OrderResponse;
import com.example.store_clothes.entity.*;
import com.example.store_clothes.enums.OrderStatus;
import com.example.store_clothes.enums.TransactionType;
import com.example.store_clothes.exception.BusinessException;
import com.example.store_clothes.exception.EntityNotFoundException;
import com.example.store_clothes.exception.InsufficientStockException;
import com.example.store_clothes.repository.CustomerRepository;
import com.example.store_clothes.repository.OrderRepository;
import com.example.store_clothes.repository.ProductVariantRepository;
import com.example.store_clothes.repository.StockHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
 *     Code: sortedItems.sort(Comparator.comparing(...variantId))
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
    private final CustomerRepository customerRepository;
    private final AuditLogService auditLogService;

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
                .status(OrderStatus.COMPLETED)
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
    // ORD-01 — HỦY HÓA ĐƠN + HOÀN KHO
    // =========================================================================

    /**
     * Hủy hóa đơn và hoàn toàn bộ tồn kho.
     *
     * ====================================================================
     * LUỒNG THỰC THI (7 BƯỚC) — PHẢI THỰC HIỆN ĐÚNG THỨ TỰ:
     * ====================================================================
     *
     * 1. Load Order, kiểm tra status = COMPLETED.
     * 2. Load danh sách OrderItem.
     * 3. SORT orderItems theo variantId ASC — CHỐNG DEADLOCK.
     * 4. Với mỗi OrderItem:
     *    a. SELECT FOR UPDATE variant (Pessimistic Lock timeout 3000ms)
     *    b. Hoàn kho: inventory += quantity
     *    c. Ghi StockHistory: TransactionType.RETURN, referenceCode="CANCEL-" + orderCode
     * 5. Đổi order.status = REFUNDED.
     * 6. Trừ loyaltyPoints khách hàng (nếu có).
     * 7. Ghi AuditLog @Async.
     *
     * 💡 Senior Note — Tại sao sort variantId trong cancelOrder giống checkout?
     * -----------------------------------------------------------------------
     * Deadlock có thể xảy ra khi 2 request hủy đơn ĐỒNG THỜI:
     *   Request X hủy đơn {variantId=3, variantId=5} → lock 3, chờ 5
     *   Request Y hủy đơn {variantId=5, variantId=3} → lock 5, chờ 3
     *   → Circular Wait → Deadlock
     * Nguy hiểm hơn: Request hủy đơn và request checkout cùng lúc:
     *   Checkout A lock variantId=2, chờ variantId=7
     *   Cancel B lock variantId=7, chờ variantId=2
     *   → Deadlock giữa checkout và cancel!
     * Giải pháp: Sort ASC trên MỌI luồng acquire DB lock → phá Circular Wait.
     *
     * 💡 Senior Note — Tại sao dùng TransactionType.RETURN thay vì IMPORT?
     * -----------------------------------------------------------------------
     * IMPORT = Nhập hàng từ nhà cung cấp (có phiếu nhập, có invoice).
     * RETURN  = Khách trả hàng (có lý do hủy đơn, không phải mua hàng mới).
     * Phân biệt 2 loại giúp báo cáo tồn kho phân biệt được:
     *   - Hàng nhập mới (IMPORT) vs Hàng hoàn trả (RETURN).
     *   - Tỷ lệ RETURN cao → cảnh báo chất lượng sản phẩm/dịch vụ.
     * Nếu dùng IMPORT → báo cáo "Tổng nhập kho" sẽ bao gồm cả hàng hoàn trả,
     * sai lệch báo cáo công nợ nhà cung cấp và báo cáo mua hàng.
     *
     * @param orderId   ID hóa đơn cần hủy
     * @param cancelledByUserId  ID người dùng đang thực hiện hủy (từ SecurityContext)
     * @param cancelledByUsername Username người dùng đang thực hiện hủy
     */
    @Transactional
    public void cancelOrder(UUID orderId, Long cancelledByUserId, String cancelledByUsername) {
        log.info("=== BẮT ĐẦU HỦY ĐƠN: orderId={}, by={} ===", orderId, cancelledByUsername);

        // =====================================================================
        // BƯỚC 1: LOAD ORDER + KIỂM TRA STATUS
        // =====================================================================
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Hóa đơn không tồn tại: " + orderId));

        if (order.getStatus() == OrderStatus.REFUNDED) {
            throw new BusinessException("Hóa đơn " + order.getOrderCode() + " đã được hủy trước đó.");
        }

        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException("Chỉ có thể hủy hóa đơn ở trạng thái COMPLETED. " +
                    "Trạng thái hiện tại: " + order.getStatus());
        }

        // =====================================================================
        // BƯỚC 2+3: SORT ORDER ITEMS THEO variantId ASC — CHỐNG DEADLOCK
        //
        // CRITICAL: Phải sort TRƯỚC KHI acquire bất kỳ lock nào.
        // Xem giải thích chi tiết tại Javadoc của method này.
        // =====================================================================
        List<OrderItem> sortedItems = order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getVariantId))
                .toList();

        log.debug("Sort {} items theo variantId ASC cho cancelOrder {}: {}",
                sortedItems.size(), order.getOrderCode(),
                sortedItems.stream().map(i -> "variantId=" + i.getVariantId()).toList());

        List<StockHistory> historiesToSave = new ArrayList<>();

        // =====================================================================
        // BƯỚC 4: VÒNG LẶP HOÀN KHO (THEO THỨ TỰ ĐÃ SORT)
        // =====================================================================
        for (OrderItem item : sortedItems) {

            // ------------------------------------------------------------------
            // BƯỚC 4a: SELECT FOR UPDATE — Pessimistic Lock (timeout 3000ms)
            // ------------------------------------------------------------------
            ProductVariant variant = variantRepository.findByIdForUpdate(item.getVariantId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Biến thể sản phẩm không tồn tại: variantId=" + item.getVariantId()));

            int balanceBefore = variant.getInventory();

            // ------------------------------------------------------------------
            // BƯỚC 4b: HOÀN KHO — inventory += quantity
            //
            // 💡 Senior Note — Tại sao không check âm kho ở đây?
            // Đây là HOÀN kho (cộng), không bao giờ âm.
            // Đơn hàng bị hủy → hàng quay về kho → không thể nhỏ hơn 0.
            // CHECK (inventory >= 0) ở DB vẫn còn nhưng sẽ không bao giờ bị vi phạm.
            // ------------------------------------------------------------------
            int balanceAfter = balanceBefore + item.getQuantity();
            variant.setInventory(balanceAfter);
            variantRepository.save(variant);

            log.debug("Hoàn kho variantId={}: {} → {} (hoàn +{})",
                    variant.getId(), balanceBefore, balanceAfter, item.getQuantity());

            // ------------------------------------------------------------------
            // BƯỚC 4c: GHI THẺ KHO (bất biến)
            // changeQuantity = dương vì HOÀN KHO (cộng lại)
            // transactionType = RETURN: phân biệt rõ với IMPORT từ NCC
            // referenceCode = "CANCEL-" + orderCode: tra cứu nguồn gốc
            // ------------------------------------------------------------------
            StockHistory history = StockHistory.builder()
                    .variantId(variant.getId())
                    .changeQuantity(item.getQuantity())          // Dương = hoàn kho
                    .transactionType(TransactionType.RETURN)
                    .referenceCode("CANCEL-" + order.getOrderCode())
                    .balanceBefore(balanceBefore)
                    .balanceAfter(balanceAfter)
                    .createdAt(LocalDateTime.now())
                    .build();
            historiesToSave.add(history);
        }

        // =====================================================================
        // BƯỚC 5: ĐỔI STATUS → REFUNDED
        // =====================================================================
        order.setStatus(OrderStatus.REFUNDED);
        orderRepository.save(order);

        // =====================================================================
        // BƯỚC 6: TRỪLẠI LOYALTY POINTS (nếu khách hàng không phải vãng lai)
        // =====================================================================
        if (order.getCustomerId() != null) {
            customerRepository.findById(order.getCustomerId()).ifPresent(customer -> {
                // Tính điểm đã tích lũy từ đơn này: finalAmount / 10000, round down
                long pointsEarned = order.getTotalAmount()
                        .divide(BigDecimal.valueOf(10000), 0, java.math.RoundingMode.DOWN)
                        .longValue();

                // Đảm bảo loyaltyPoints không âm
                int newPoints = Math.max(0, customer.getLoyaltyPoints() - (int) pointsEarned);
                customer.setLoyaltyPoints(newPoints);
                customerRepository.save(customer);

                log.debug("Trừ loyaltyPoints khách {}: -{} → {} điểm",
                        customer.getId(), pointsEarned, newPoints);
            });
        }

        // Batch INSERT tất cả thẻ kho một lần
        stockHistoryRepository.saveAll(historiesToSave);

        log.info("=== HỦY ĐƠN THÀNH CÔNG: orderCode={}, by={} ===",
                order.getOrderCode(), cancelledByUsername);

        // =====================================================================
        // BƯỚC 7: GHI AUDIT LOG @Async (Propagation.REQUIRES_NEW)
        // Không blocking — response trả ngay, log ghi trên thread riêng.
        // =====================================================================
        String details = String.format(
                "{\"orderId\": \"%s\", \"orderCode\": \"%s\", \"totalRefunded\": %s}",
                orderId, order.getOrderCode(), order.getTotalAmount()
        );
        auditLogService.log(cancelledByUserId, cancelledByUsername,
                "CANCEL_ORDER", "ORDER", null, details);
    }

    // =========================================================================
    // ORD-02 — DANH SÁCH HÓA ĐƠN VỚI FILTER STATUS
    // =========================================================================

    /**
     * Lấy danh sách hóa đơn với filter đa điều kiện + phân trang.
     *
     * @param status     Filter theo OrderStatus (null = tất cả)
     * @param customerId Filter theo khách hàng (null = tất cả)
     * @param from       Từ ngày (null = không filter)
     * @param to         Đến ngày (null = không filter)
     * @param pageable   Phân trang
     * @return Page<OrderResponse>
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(OrderStatus status,
                                          Long customerId,
                                          LocalDateTime from,
                                          LocalDateTime to,
                                          Pageable pageable) {
        return orderRepository.findOrders(status, customerId, from, to, pageable)
                .map(this::mapToResponse);
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
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .items(itemSummaries)
                .build();
    }
}
