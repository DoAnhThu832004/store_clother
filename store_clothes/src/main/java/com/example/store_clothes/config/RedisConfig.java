package com.example.store_clothes.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RedisConfig — Cấu hình RedisTemplate cho Idempotency Key storage.
 *
 * TẠI SAO DÙNG STRING SERIALIZER CHO CẢ KEY VÀ VALUE?
 *  - Idempotency chỉ cần lưu trạng thái đơn giản (key → "PROCESSED" / "PENDING").
 *  - String serializer tương thích 100% khi đọc bằng redis-cli để debug.
 *  - Tránh serialize/deserialize overhead của Java/JSON serializer.
 *
 * CÀI ĐẶT REDIS TRONG application.properties:
 *   spring.data.redis.host=localhost
 *   spring.data.redis.port=6379
 *   spring.data.redis.password=    # để trống nếu không có password
 *   spring.data.redis.timeout=2000ms
 *
 * Nếu chạy local không có Redis, có thể thêm dependency testcontainers hoặc
 * dùng embedded Redis (com.github.codemonstur:embedded-redis) cho môi trường dev.
 */
@Slf4j
@Configuration
public class RedisConfig {

    /**
     * RedisTemplate&lt;String, String&gt; — Template chuyên dụng cho Idempotency.
     *
     * Cấu hình:
     *  - keySerializer: StringRedisSerializer → key lưu dạng UTF-8 string.
     *  - valueSerializer: StringRedisSerializer → value lưu dạng UTF-8 string.
     *  - hashKeySerializer + hashValueSerializer: String (dự phòng nếu dùng Hash).
     *
     * @param factory RedisConnectionFactory từ Spring auto-configure (Lettuce client)
     * @return RedisTemplate được cấu hình sẵn cho Idempotency
     */
    @Bean
    public RedisTemplate<String, String> idempotencyRedisTemplate(
            RedisConnectionFactory factory) {

        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Key + Value đều dùng String serializer
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();

        log.info("RedisTemplate (Idempotency) configured with StringRedisSerializer");
        return template;
    }
}
