package com.example.store_clothes.config;

import com.example.store_clothes.multitenancy.TenantCacheKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * RedisConfig — Cấu hình Redis: Idempotency Key + Tenant-Aware Cache Manager.
 *
 * KIẾN TRÚC CACHE MULTI-TENANT:
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  @Cacheable(value = "products")                                     │
 * │       ↓                                                             │
 * │  TenantCacheKeyGenerator.generate()                                 │
 * │       → "tenant:{tenantId}:ProductService:getById:1"               │
 * │       ↓                                                             │
 * │  RedisCacheManager → Redis SET "tenant:5:products:..." TTL=1h       │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * CHỐNG CROSS-TENANT CACHE LEAK:
 * Mỗi tenant có cache key namespace riêng biệt.
 * Tenant 5 không bao giờ đọc cache của Tenant 7 dù cùng gọi getProduct(1).
 *
 * TTL Strategy (khác nhau theo cache name):
 * - products, variants, categories: 1 giờ (dữ liệu ít thay đổi)
 * - customers, suppliers: 30 phút (thay đổi thường xuyên hơn)
 * - reports: 5 phút (stale report không chấp nhận được)
 * - default: 30 phút
 *
 * CÀI ĐẶT REDIS TRONG application.yaml:
 *   spring:
 *     data:
 *       redis:
 *         host: localhost
 *         port: 6379
 *         timeout: 2000ms
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    // =========================================================================
    // IDEMPOTENCY REDIS TEMPLATE
    // =========================================================================

    /**
     * RedisTemplate chuyên dụng cho Idempotency Key.
     *
     * TẠI SAO DÙNG STRING SERIALIZER CHO CẢ KEY VÀ VALUE?
     *  - Idempotency chỉ cần lưu trạng thái đơn giản (key → "PROCESSED" / "PENDING").
     *  - String serializer tương thích 100% khi đọc bằng redis-cli để debug.
     *  - Tránh serialize/deserialize overhead của Java/JSON serializer.
     *
     * @param factory RedisConnectionFactory từ Spring auto-configure (Lettuce client)
     */
    @Bean
    public RedisTemplate<String, String> idempotencyRedisTemplate(
            RedisConnectionFactory factory) {

        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();

        log.info("RedisTemplate (Idempotency) configured with StringRedisSerializer");
        return template;
    }

    // =========================================================================
    // TENANT-AWARE CACHE MANAGER
    // =========================================================================

    /**
     * RedisCacheManager — CacheManager chính của hệ thống.
     *
     * @Primary: Bean này được dùng mặc định cho @Cacheable, @CacheEvict, @CachePut.
     *
     * Cấu hình key serialization:
     * - keySerializer: String (cache key là String từ TenantCacheKeyGenerator)
     * - valueSerializer: GenericJackson2JsonRedisSerializer (serialize object → JSON)
     *   → Tương thích với nhiều loại object, readable bằng redis-cli
     *   → GenericJackson2 thêm @class field → đảm bảo deserialize đúng type
     */
    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();

        // Cấu hình serialization mặc định cho mọi cache
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))          // TTL mặc định: 30 phút
                .serializeKeysWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(keySerializer))
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
                .disableCachingNullValues();               // Không cache null → tránh negative cache leak

        // Cấu hình TTL riêng theo từng cache name
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // Catalog data: ít thay đổi → TTL dài
        cacheConfigs.put("products",   defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("variants",   defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("categories", defaultConfig.entryTtl(Duration.ofHours(1)));

        // Customer/Supplier: thay đổi thường xuyên hơn
        cacheConfigs.put("customers",  defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("suppliers",  defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // Report: stale data không chấp nhận được trong môi trường multi-tenant
        cacheConfigs.put("reports",    defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // Dashboard stats: refresh nhanh
        cacheConfigs.put("dashboard",  defaultConfig.entryTtl(Duration.ofMinutes(10)));

        CacheManager cm = RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();

        log.info("RedisCacheManager initialized with tenant-aware key generation (TTL: 30m default)");
        return cm;
    }

    /**
     * TenantCacheKeyGenerator bean — Default key generator cho mọi @Cacheable.
     *
     * Cách dùng trong Service:
     *   @Cacheable(value = "products")   // Dùng default keyGenerator
     *   @Cacheable(value = "products", keyGenerator = "tenantCacheKeyGenerator")  // Tường minh
     */
    @Bean("tenantKeyGenerator")
    public KeyGenerator tenantKeyGenerator() {
        return new TenantCacheKeyGenerator();
    }
}
