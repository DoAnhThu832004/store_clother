package com.example.store_clothes.security;

import com.example.store_clothes.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * JwtUtil - Tiện ích tạo và xác thực JWT Token.
 *
 * THIẾT KẾ:
 * - Access Token: Thời hạn 24h, chứa extra claims (userId, fullName, roles).
 * - Refresh Token: Thời hạn 7 ngày, chỉ chứa username (dùng để cấp lại access token).
 *
 * SECRET KEY:
 * - Được cấu hình qua application.yaml (jwt.secret).
 * - Phải là chuỗi Base64-encoded ≥ 256 bits (32 bytes).
 * - Không hardcode trong source code.
 *
 * DEPENDENCY: io.jsonwebtoken:jjwt-api/impl/jackson (phiên bản 0.12.x).
 */
@Slf4j
@Component
public class JwtUtil {

    /**
     * Khóa bí mật từ config. Cấu hình trong application.yaml:
     * jwt:
     *   secret: <base64-encoded-256bit-secret>
     *   access-token-expiration: 86400000  # 24h (ms)
     *   refresh-token-expiration: 604800000 # 7 ngày (ms)
     */
    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token-expiration:86400000}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    // =========================================================================
    // TOKEN GENERATION
    // =========================================================================

    /**
     * Tạo Access Token với extra claims (userId, fullName, roles).
     * Extra claims được nhúng vào JWT payload → không cần query DB tại Filter.
     *
     * @param user        UserDetails của người dùng vừa xác thực thành công
     * @param extraClaims Map chứa các thông tin bổ sung cần đóng gói vào token
     * @return JWT Access Token dạng chuỗi compact
     */
    public String generateToken(UserDetails user, Map<String, Object> extraClaims) {
        return buildToken(user.getUsername(), extraClaims, accessTokenExpiration);
    }

    /**
     * Tạo Refresh Token — chỉ chứa username, không có extra claims.
     * Dùng để cấp lại access token khi hết hạn, không dùng để xác thực API.
     */
    public String generateRefreshToken(UserDetails user) {
        return buildToken(user.getUsername(), Map.of(), refreshTokenExpiration);
    }

    /** Hàm tạo token dùng chung. */
    private String buildToken(String subject, Map<String, Object> claims, long expiration) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    // =========================================================================
    // TOKEN VALIDATION
    // =========================================================================

    /**
     * Xác thực token: kiểm tra username khớp và token chưa hết hạn.
     *
     * @param token       JWT token cần xác thực
     * @param userDetails UserDetails nạp từ DB để so sánh
     * @return true nếu token hợp lệ
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    // =========================================================================
    // CLAIMS EXTRACTION
    // =========================================================================

    /** Trích xuất username từ Subject claim. */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /** Trích xuất thời hạn token. */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /** Generic: Trích xuất bất kỳ claim nào từ token. */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /** Decode Base64 secret → HMAC-SHA256 signing key. */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
