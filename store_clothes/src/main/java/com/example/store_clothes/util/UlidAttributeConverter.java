package com.example.store_clothes.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

/**
 * UlidAttributeConverter — JPA Converter tự động ánh xạ UUID ↔ BINARY(16) trong MySQL.
 *
 * NGUYÊN LÝ HOẠT ĐỘNG:
 *  - Khi JPA ghi xuống DB (convertToDatabaseColumn):
 *      UUID (Java) → byte[16] (MySQL BINARY(16))
 *  - Khi JPA đọc từ DB (convertToEntityAttribute):
 *      byte[16] (MySQL BINARY(16)) → UUID (Java)
 *
 * LÝ DO DÙNG BINARY(16) THAY VÌ VARCHAR(36):
 *  - BINARY(16) = 16 bytes, VARCHAR(36) = 36 bytes + overhead.
 *  - Index BINARY(16) nhỏ hơn 2.25x → query join nhanh hơn đáng kể khi bảng > 1M rows.
 *  - Sắp xếp BINARY(16) nhanh hơn VARCHAR (so sánh binary vs string collation).
 *
 * CÁCH DÙNG TRÊN ENTITY:
 *
 *   @Id
 *   @Column(name = "id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
 *   @Convert(converter = UlidAttributeConverter.class)
 *   private UUID id;
 *
 *   @PrePersist
 *   protected void generateId() {
 *       if (this.id == null) {
 *           this.id = UlidGenerator.generateAsUuid();
 *       }
 *   }
 *
 * autoApply = false: Chỉ áp dụng khi entity khai báo @Convert(converter = ...) tường minh.
 * Không tự động chuyển mọi UUID — tránh conflict với các cột UUID dùng VARCHAR thông thường.
 */
@Converter(autoApply = false)
public class UlidAttributeConverter implements AttributeConverter<UUID, byte[]> {

    /**
     * Java → DB: Chuyển UUID thành 16 bytes để lưu vào cột BINARY(16).
     * Gọi khi JPA thực hiện INSERT hoặc UPDATE.
     *
     * @param uuid UUID object từ entity (null nếu field chưa set)
     * @return mảng 16 bytes, hoặc null nếu uuid là null
     */
    @Override
    public byte[] convertToDatabaseColumn(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return uuidToBytes(uuid);
    }

    /**
     * DB → Java: Chuyển 16 bytes đọc từ BINARY(16) thành UUID object.
     * Gọi khi JPA thực hiện SELECT và map kết quả vào entity.
     *
     * @param bytes mảng 16 bytes từ ResultSet (null nếu cột DB là NULL)
     * @return UUID object, hoặc null nếu bytes là null
     */
    @Override
    public UUID convertToEntityAttribute(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return bytesToUuid(bytes);
    }

    // =========================================================================
    // HELPER — Chuyển đổi UUID ↔ byte[16]
    // =========================================================================

    /**
     * UUID → byte[16]: Dùng 2 long (MSB + LSB) của UUID để tạo 16 bytes.
     * Big-endian để đảm bảo thứ tự sắp xếp timestamp ULID được giữ nguyên trong BINARY(16).
     */
    private byte[] uuidToBytes(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        byte[] result = new byte[16];
        for (int i = 7; i >= 0; i--) {
            result[i]     = (byte) (msb & 0xFF);
            result[i + 8] = (byte) (lsb & 0xFF);
            msb >>= 8;
            lsb >>= 8;
        }
        return result;
    }

    /**
     * byte[16] → UUID: Tái tạo UUID từ 16 bytes theo Big-endian.
     */
    private UUID bytesToUuid(byte[] bytes) {
        long msb = 0L;
        long lsb = 0L;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xFF);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xFF);
        }
        return new UUID(msb, lsb);
    }
}
