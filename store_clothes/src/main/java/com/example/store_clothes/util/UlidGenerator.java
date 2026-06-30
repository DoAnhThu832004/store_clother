package com.example.store_clothes.util;

import de.huxhorn.sulky.ulid.ULID;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * UlidGenerator — Utility sinh ULID và chuyển đổi giữa các format lưu trữ.
 *
 * ULID (Universally Unique Lexicographically Sortable Identifier):
 *  - 128-bit, time-sortable, URL-safe.
 *  - Cho phép Client (POS offline) tự sinh ID mà không cần gọi Server → không trùng lặp khi sync.
 *  - Có thể sắp xếp theo thời gian tạo (timestamp 48-bit đầu).
 *
 * CHIẾN LƯỢC LƯU TRỮ:
 *  - Trong MySQL: BINARY(16) — tiết kiệm dung lượng, index hiệu quả hơn VARCHAR(36) tới 4x.
 *  - Trong Java: dùng UUID làm surrogate (128-bit, tương thích).
 *  - Trên API (JSON): trả về chuỗi 26 ký tự ULID hoặc UUID string tùy cấu hình.
 *
 * CÁCH DÙNG:
 *   // Sinh ULID mới:
 *   String ulid = UlidGenerator.generate();         // "01J4X8K9M7P3Q5R2T6W1Y0Z8H5"
 *
 *   // Lưu vào DB (BINARY 16 bytes):
 *   byte[] binary = UlidGenerator.toBytes(ulid);
 *
 *   // Đọc từ DB ra chuỗi:
 *   String restored = UlidGenerator.fromBytes(binary);
 *
 *   // Chuyển sang UUID để dùng với JPA:
 *   UUID uuid = UlidGenerator.toUuid(ulid);
 *
 * @Component: Singleton Bean — reuse ULID generator instance (thread-safe).
 */
@Component
public class UlidGenerator {

    /**
     * ULID generator instance — Thread-safe, dùng chung toàn ứng dụng.
     * Mỗi lần gọi generate() sinh một ULID unique theo thời gian thực.
     */
    private static final ULID ULID_INSTANCE = new ULID();

    // =========================================================================
    // PHƯƠNG THỨC SINH ULID
    // =========================================================================

    /**
     * Sinh một ULID mới — 26 ký tự Crockford Base32.
     * Ví dụ: "01J4X8K9M7P3Q5R2T6W1Y0Z8H5"
     *
     * @return chuỗi ULID 26 ký tự, time-sortable, globally unique
     */
    public static String generate() {
        return ULID_INSTANCE.nextULID();
    }

    // =========================================================================
    // CHUYỂN ĐỔI ULID ↔ BINARY(16)
    // =========================================================================

    /**
     * Chuyển chuỗi ULID → 16 bytes để lưu vào cột BINARY(16) trong MySQL.
     * Dùng trong JPA AttributeConverter khi write xuống DB.
     *
     * @param ulid chuỗi ULID 26 ký tự
     * @return mảng 16 bytes tương ứng
     */
    public static byte[] toBytes(String ulid) {
        ULID.Value value = ULID.parseULID(ulid);
        return toBytes(value);
    }

    /**
     * Chuyển ULID.Value → 16 bytes.
     */
    public static byte[] toBytes(ULID.Value value) {
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(value.getMostSignificantBits());
        bb.putLong(value.getLeastSignificantBits());
        return bb.array();
    }

    /**
     * Phục hồi chuỗi ULID từ 16 bytes đọc ra từ cột BINARY(16) trong MySQL.
     * Dùng trong JPA AttributeConverter khi read từ DB.
     *
     * @param bytes mảng 16 bytes từ DB
     * @return chuỗi ULID 26 ký tự
     */
    public static String fromBytes(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        long msb = bb.getLong();
        long lsb = bb.getLong();
        ULID.Value value = new ULID.Value(msb, lsb);
        return value.toString();
    }

    // =========================================================================
    // CHUYỂN ĐỔI ULID ↔ UUID (dùng với JPA @Id kiểu UUID)
    // =========================================================================

    /**
     * Chuyển chuỗi ULID → UUID.
     * Dùng khi entity Java dùng UUID làm kiểu @Id.
     *
     * @param ulid chuỗi ULID 26 ký tự
     * @return UUID tương ứng (cùng 128 bits)
     */
    public static UUID toUuid(String ulid) {
        ULID.Value value = ULID.parseULID(ulid);
        return new UUID(value.getMostSignificantBits(), value.getLeastSignificantBits());
    }

    /**
     * Chuyển UUID → chuỗi ULID.
     *
     * @param uuid UUID cần chuyển đổi
     * @return chuỗi ULID 26 ký tự tương ứng
     */
    public static String fromUuid(UUID uuid) {
        ULID.Value value = new ULID.Value(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
        return value.toString();
    }

    /**
     * Sinh một UUID mới từ ULID — time-sortable, không trùng lặp.
     * Dùng cho @GeneratedValue bằng cách gọi: entity.setId(UlidGenerator.generateAsUuid())
     *
     * @return UUID mới dựa trên ULID timestamp
     */
    public static UUID generateAsUuid() {
        return toUuid(generate());
    }

    // =========================================================================
    // CHUYỂN ĐỔI ULID ↔ UUID STRING
    // =========================================================================

    /**
     * Chuyển chuỗi ULID → chuỗi UUID dạng 8-4-4-4-12.
     * Ví dụ: "018f8e3c-7a2b-7e4d-8f1a-2b3c4d5e6f7a"
     *
     * @param ulid chuỗi ULID 26 ký tự
     * @return chuỗi UUID dạng hyphenated
     */
    public static String toUuidString(String ulid) {
        return toUuid(ulid).toString();
    }

    /**
     * Chuyển chuỗi UUID dạng hyphenated → chuỗi ULID 26 ký tự.
     *
     * @param uuidString chuỗi UUID dạng "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
     * @return chuỗi ULID 26 ký tự tương ứng
     */
    public static String fromUuidString(String uuidString) {
        return fromUuid(UUID.fromString(uuidString));
    }
}
