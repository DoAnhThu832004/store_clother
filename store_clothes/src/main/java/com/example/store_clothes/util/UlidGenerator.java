package com.example.store_clothes.util;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * UlidGenerator — Utility sinh ULID và chuyển đổi giữa các format lưu trữ (Thuần Java, không dependency).
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
 */
@Component
public class UlidGenerator {

    private static final char[] CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // =========================================================================
    // PHƯƠNG THỨC SINH ULID
    // =========================================================================

    /**
     * Sinh một ULID mới — 26 ký tự Crockford Base32.
     * Ví dụ: "01J4X8K9M7P3Q5R2T6W1Y0Z8H5"
     */
    public static String generate() {
        return fromUuid(generateAsUuid());
    }

    /**
     * Sinh một UUID mới từ ULID — time-sortable, không trùng lặp.
     * Dùng cho @GeneratedValue bằng cách gọi: entity.setId(UlidGenerator.generateAsUuid())
     */
    public static UUID generateAsUuid() {
        long timestamp = System.currentTimeMillis();
        byte[] bytes = new byte[16];

        // Put timestamp in the first 48 bits (6 bytes)
        bytes[0] = (byte) (timestamp >>> 40);
        bytes[1] = (byte) (timestamp >>> 32);
        bytes[2] = (byte) (timestamp >>> 24);
        bytes[3] = (byte) (timestamp >>> 16);
        bytes[4] = (byte) (timestamp >>> 8);
        bytes[5] = (byte) (timestamp);

        // Put 80 bits of cryptographically secure random data in the remaining 10 bytes
        byte[] randBytes = new byte[10];
        SECURE_RANDOM.nextBytes(randBytes);
        System.arraycopy(randBytes, 0, bytes, 6, 10);

        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new UUID(bb.getLong(), bb.getLong());
    }

    // =========================================================================
    // CHUYỂN ĐỔI ULID ↔ BINARY(16)
    // =========================================================================

    /**
     * Chuyển chuỗi ULID → 16 bytes để lưu vào cột BINARY(16) trong MySQL.
     */
    public static byte[] toBytes(String ulid) {
        if (ulid == null || ulid.length() != 26) {
            throw new IllegalArgumentException("ULID must be exactly 26 characters");
        }
        String upper = ulid.toUpperCase();
        byte[] bytes = new byte[16];

        // Parse each character to its 5-bit value
        int[] values = new int[26];
        for (int i = 0; i < 26; i++) {
            char c = upper.charAt(i);
            int val = -1;
            for (int j = 0; j < CROCKFORD_ALPHABET.length; j++) {
                if (CROCKFORD_ALPHABET[j] == c) {
                    val = j;
                    break;
                }
            }
            if (val == -1) {
                throw new IllegalArgumentException("Invalid Crockford Base32 character: " + c);
            }
            values[i] = val;
        }

        // Distribute the 26 * 5 = 130 bits into 16 bytes (128 bits)
        // The first 2 bits are padding (must be 0).
        for (int i = 0; i < 26; i++) {
            int val = values[i];
            int bitStart = i * 5 - 2;
            for (int b = 0; b < 5; b++) {
                int bitPos = bitStart + b;
                if (bitPos >= 0 && bitPos < 128) {
                    int bit = (val >>> (4 - b)) & 1;
                    if (bit == 1) {
                        int byteIdx = bitPos / 8;
                        int bitIdx = 7 - (bitPos % 8);
                        bytes[byteIdx] |= (byte) (1 << bitIdx);
                    }
                }
            }
        }
        return bytes;
    }

    /**
     * Phục hồi chuỗi ULID từ 16 bytes đọc ra từ cột BINARY(16) trong MySQL.
     */
    public static String fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != 16) {
            throw new IllegalArgumentException("Bytes must be exactly 16 bytes");
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return fromUuid(new UUID(bb.getLong(), bb.getLong()));
    }

    // =========================================================================
    // CHUYỂN ĐỔI ULID ↔ UUID
    // =========================================================================

    /**
     * Chuyển chuỗi ULID → UUID.
     */
    public static UUID toUuid(String ulid) {
        byte[] bytes = toBytes(ulid);
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new UUID(bb.getLong(), bb.getLong());
    }

    /**
     * Chuyển UUID → chuỗi ULID.
     */
    public static String fromUuid(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();

        byte[] bytes = new byte[16];
        ByteBuffer.wrap(bytes).putLong(msb).putLong(lsb);

        char[] chars = new char[26];
        for (int i = 0; i < 26; i++) {
            int bitStart = i * 5 - 2;
            int val = 0;
            for (int b = 0; b < 5; b++) {
                int bitPos = bitStart + b;
                if (bitPos >= 0 && bitPos < 128) {
                    int byteIdx = bitPos / 8;
                    int bitIdx = 7 - (bitPos % 8);
                    int bit = (bytes[byteIdx] >>> bitIdx) & 1;
                    val = (val << 1) | bit;
                } else {
                    val = val << 1;
                }
            }
            chars[i] = CROCKFORD_ALPHABET[val];
        }
        return new String(chars);
    }

    // =========================================================================
    // CHUYỂN ĐỔI ULID ↔ UUID STRING
    // =========================================================================

    /**
     * Chuyển chuỗi ULID → chuỗi UUID dạng 8-4-4-4-12.
     */
    public static String toUuidString(String ulid) {
        return toUuid(ulid).toString();
    }

    /**
     * Chuyển chuỗi UUID dạng hyphenated → chuỗi ULID 26 ký tự.
     */
    public static String fromUuidString(String uuidString) {
        return fromUuid(UUID.fromString(uuidString));
    }
}
