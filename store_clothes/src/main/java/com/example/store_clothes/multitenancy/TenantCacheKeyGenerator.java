package com.example.store_clothes.multitenancy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * TenantCacheKeyGenerator — Tự động gắn tenantId vào mọi cache key.
 *
 * VẤN ĐỀ — Cache Data Leak giữa các Tenant:
 * Nếu không tách biệt cache key theo tenant, tình huống sau sẽ xảy ra:
 *
 *   Tenant A request "getProduct(id=1)"   → Cache key: "product::1"  → Cache MISS → DB query → Cache SET
 *   Tenant B request "getProduct(id=1)"   → Cache key: "product::1"  → Cache HIT  → Nhận data của Tenant A!
 *
 * Đây là lỗi Cross-Tenant Cache Leakage — nghiêm trọng hơn cả DB data leak
 * vì không qua bất kỳ tầng security nào.
 *
 * GIẢI PHÁP — Key Pattern:
 *   tenant:{tenantId}:{cacheName}:{args}
 *
 * Ví dụ:
 *   @Cacheable(value = "products", keyGenerator = "tenantCacheKeyGenerator")
 *   → getProduct(1L) với tenantId=5  → key: "tenant:5:products:1"
 *   → getProduct(1L) với tenantId=7  → key: "tenant:7:products:1"
 *
 * TÍCH HỢP:
 * Đăng ký là default keyGenerator trong CacheConfig → áp dụng cho mọi @Cacheable
 * mà không khai báo tường minh key/keyGenerator.
 *
 * XỬ LÝ NULL TENANT (Super Admin / System):
 * Khi tenantId = null (Super Admin hoặc system call), dùng prefix "tenant:system".
 * Super Admin thường không cần cache (query toàn bộ data thường là admin action ít gặp).
 * Nếu cần cache cho Super Admin, dùng cache riêng với TTL ngắn hơn.
 *
 * ĐĂNG KÝ:
 * Được đăng ký là default keyGenerator trong CacheConfig.
 * Để sử dụng: @Cacheable(value = "products") hoặc @Cacheable(value = "products", keyGenerator = "tenantCacheKeyGenerator")
 */
@Slf4j
@Component("tenantCacheKeyGenerator")
public class TenantCacheKeyGenerator implements KeyGenerator {

    /** Prefix đánh dấu key thuộc tenant cụ thể. */
    private static final String TENANT_PREFIX  = "tenant";

    /** Prefix khi không có tenant context (Super Admin / system jobs). */
    private static final String SYSTEM_PREFIX  = "system";

    /**
     * Sinh cache key bao gồm tenantId.
     *
     * Format: tenant:{tenantId}:{className}:{methodName}:{args...}
     *
     * Ví dụ:
     *   ProductService.getById(1L) với tenant 5     → "tenant:5:ProductService:getById:1"
     *   ProductService.search("ao", "L") với tenant 5 → "tenant:5:ProductService:search:ao:L"
     *   Super Admin gọi same method                  → "tenant:system:ProductService:getById:1"
     *
     * @param target Đối tượng chứa method được cache
     * @param method Method được cache
     * @param params Tham số truyền vào method
     * @return Cache key duy nhất per-tenant
     */
    @Override
    @NonNull
    public Object generate(@NonNull Object target, @NonNull Method method, @NonNull Object... params) {
        Long tenantId = TenantContextHolder.getTenantId();

        String tenantSegment = (tenantId != null)
                ? String.valueOf(tenantId)
                : SYSTEM_PREFIX;

        String className  = target.getClass().getSimpleName();
        String methodName = method.getName();

        String argsSegment = (params.length == 0)
                ? "no_args"
                : Arrays.stream(params)
                        .map(param -> param == null ? "null" : param.toString())
                        .collect(Collectors.joining(":"));

        String cacheKey = TENANT_PREFIX + ":" + tenantSegment
                + ":" + className
                + ":" + methodName
                + ":" + argsSegment;

        log.trace("Cache key generated: {}", cacheKey);
        return cacheKey;
    }
}
