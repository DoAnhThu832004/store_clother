package com.example.store_clothes.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * VietnameseUtil - Tiện ích xử lý chuỗi tiếng Việt và sinh mã SKU tự động.
 *
 * 💡 Senior Note — Thiết kế Utility Class:
 * - Class được khai báo final, constructor private → không thể khởi tạo hay kế thừa.
 * - Tất cả method đều là static → gọi trực tiếp không cần inject Spring Bean.
 * - Phù hợp với các hàm tiện ích thuần túy (pure functions) không có state.
 *
 * 💡 Senior Note — NFD Normalization:
 * NFD (Canonical Decomposition) tách ký tự có dấu thành ký tự gốc + combining marks.
 * Ví dụ: "ệ" → "e" + combining grave + combining hook below.
 * Sau đó NON_ASCII pattern xóa hết combining marks → còn lại ký tự ASCII thuần.
 * Đây là kỹ thuật chuẩn để xử lý tiếng Việt không phụ thuộc vào thư viện ngoài.
 */
public final class VietnameseUtil {

    /** Pattern xóa mọi ký tự ngoài ASCII (combining marks sau NFD normalization). */
    private static final Pattern NON_ASCII = Pattern.compile("[^\\p{ASCII}]");

    /**
     * Pattern xóa mọi ký tự không phải chữ-số, khoảng trắng, hoặc gạch ngang.
     * Dùng trong toSlug() để làm sạch chuỗi trước khi tạo slug URL-friendly.
     */
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9\\s-]");

    /** Pattern thu gọn nhiều khoảng trắng liên tiếp thành 1 khoảng trắng. */
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

    /** Utility class — không cho phép khởi tạo. */
    private VietnameseUtil() {
        throw new UnsupportedOperationException("Utility class — không được khởi tạo");
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Xóa dấu tiếng Việt khỏi chuỗi đầu vào.
     *
     * 💡 Senior Note — Thuật toán NFD:
     * Bước 1: Normalizer.normalize(input, NFD) → tách dấu ra khỏi ký tự gốc.
     * Bước 2: NON_ASCII.replaceAll("") → xóa toàn bộ combining diacritical marks.
     * Kết quả: "Áo Thun Nam" → "Ao Thun Nam".
     *
     * @param input Chuỗi tiếng Việt đầu vào (có thể null hoặc blank)
     * @return Chuỗi không dấu, hoặc "" nếu input null/blank
     */
    public static String removeDiacritics(String input) {
        if (input == null || input.isBlank()) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return NON_ASCII.matcher(normalized).replaceAll("");
    }

    /**
     * Chuyển đổi chuỗi thành slug URL-friendly.
     *
     * 💡 Senior Note — Ứng dụng trong sinh mã sản phẩm:
     * "Áo Thun Nam Oversize" → "ao-thun-nam-oversize"
     * Sau đó có thể .toUpperCase().replace("-","_") → "AO_THUN_NAM_OVERSIZE" làm product code.
     *
     * Pipeline xử lý:
     * 1. removeDiacritics()     : "Áo Thun" → "Ao Thun"
     * 2. NON_ALPHANUMERIC.remove: Xóa ký tự đặc biệt (trừ space, hyphen)
     * 3. MULTIPLE_SPACES.replace: Thu gọn whitespace
     * 4. trim() + lowercase     : "ao thun" → "ao-thun"
     *
     * @param input Chuỗi cần chuyển đổi
     * @return Slug dạng "ten-san-pham", hoặc "" nếu input null/blank
     */
    public static String toSlug(String input) {
        if (input == null || input.isBlank()) return "";
        String noDiacritics = removeDiacritics(input);
        String cleaned = NON_ALPHANUMERIC.matcher(noDiacritics).replaceAll("");
        String trimmed = MULTIPLE_SPACES.matcher(cleaned.trim()).replaceAll(" ");
        return trimmed.toLowerCase().replace(" ", "-");
    }

    /**
     * Sinh mã SKU tự động từ tên sản phẩm, màu sắc và kích cỡ.
     *
     * 💡 Senior Note — Quy tắc sinh SKU:
     * SKU = [viết tắt tên SP] + "-" + [viết tắt màu] + "-" + [size làm sạch]
     * Ví dụ: generateSku("Áo Thun Nam", "Đen", "XL") → "ATN-D-XL"
     * Ví dụ: generateSku("Quần Jeans Slim", "Xanh Navy", "32") → "QJS-XN-32"
     *
     * 💡 Senior Note — Tính chất quan trọng của SKU sinh tự động:
     * SKU này là "base SKU" — có thể bị trùng nếu cùng tên SP có cùng viết tắt.
     * Lớp Service sẽ dùng resolveSkuConflict() để gắn suffix (-1, -2...) nếu trùng.
     * → Đây là pattern phân tách trách nhiệm (SRP): Util chỉ sinh base, Service xử lý conflict.
     *
     * @param productName Tên sản phẩm (ví dụ: "Áo Thun Nam Oversize")
     * @param color       Tên màu sắc (ví dụ: "Đen", "Xanh Nước Biển")
     * @param size        Kích cỡ (ví dụ: "S", "M", "L", "XL", "32")
     * @return Mã SKU dạng "XXX-YYY-ZZZ" (uppercase)
     */
    public static String generateSku(String productName, String color, String size) {
        String productAbbr = getAbbreviation(productName);
        String colorAbbr   = getAbbreviation(color);
        // Size: xóa dấu + giữ lại chữ-số + uppercase (ví dụ: "XL" → "XL", "32" → "32")
        String sizeClean   = removeDiacritics(size)
                .replaceAll("[^a-zA-Z0-9]", "")
                .toUpperCase();
        return productAbbr + "-" + colorAbbr + "-" + sizeClean;
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Lấy chữ cái đầu của mỗi từ trong chuỗi, ghép lại và uppercase.
     *
     * 💡 Senior Note — Java 21 Stream API:
     * Dùng split("\\s+") để tách từ bởi bất kỳ whitespace nào (space, tab...).
     * filter(!isEmpty) để loại bỏ phần tử rỗng từ split (edge case với leading spaces).
     * map(charAt(0)) → lấy ký tự đầu tiên của mỗi từ.
     * Collectors.joining() → ghép tất cả thành 1 chuỗi.
     *
     * Ví dụ: "ao thun nam" → ["ao","thun","nam"] → ["a","t","n"] → "ATN"
     *
     * @param text Chuỗi đầu vào (đã hoặc chưa bỏ dấu đều được)
     * @return Chuỗi viết tắt uppercase (ví dụ: "ATN"), hoặc "" nếu text null/blank
     */
    private static String getAbbreviation(String text) {
        if (text == null || text.isBlank()) return "";
        String noDiacritics = removeDiacritics(text).trim();
        return Arrays.stream(noDiacritics.split("\\s+"))
                .filter(word -> !word.isEmpty())
                .map(word -> String.valueOf(word.charAt(0)))
                .collect(Collectors.joining())
                .toUpperCase();
    }
}
