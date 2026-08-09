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
 * - Access Token: Thời hạn 24h, chứa extra claims (userId, fullName, roles, tenantId).
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

    // =========================================================================
    // JWT Claim Key Constants — Dùng chung giữa generateToken() và extract*()
    // Tập trung ở đây để tránh typo khi đọc/ghi claims
    // =========================================================================

    /** Key chứa ID nội bộ của User (Long). */
    public static final String CLAIM_USER_ID   = "userId";

    /** Key chứa họ tên đầy đủ của User (String). */
    public static final String CLAIM_FULL_NAME = "fullName";

    /** Key chứa danh sách roles (Collection<String>). */
    public static final String CLAIM_ROLES     = "roles";

    /**
     * Key chứa ID của Tenant (cửa hàng) mà User thuộc về.
     *
     * SECURITY NOTE:
     * tenantId trong JWT là "claim of convenience" — giúp tránh query DB tại Filter.
     * Tuy nhiên, Hibernate @TenantId là tuyến phòng thủ cuối cùng thực sự tin cậy.
     * Ngay cả khi JWT bị forge (nếu secret bị lộ), Hibernate vẫn đảm bảo
     * data isolation ở tầng DB vì tenantId được verify từ user session.
     *
     * Super Admin: tenantId = null trong JWT → filter bypass.
     */
    public static final String CLAIM_TENANT_ID = "tenantId";

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
     * Tạo Access Token với extra claims (userId, fullName, roles, tenantId).
     * Extra claims được nhúng vào JWT payload → không cần query DB tại Filter.
     *
     * MULTI-TENANT: extraClaims PHẢI chứa "tenantId" (Long hoặc null cho SUPER_ADMIN).
     * Caller (AuthService) chịu trách nhiệm build đúng extraClaims:
     * <pre>
     *   Map.of(
     *     JwtUtil.CLAIM_USER_ID,   user.getId(),
     *     JwtUtil.CLAIM_FULL_NAME, user.getFullName(),
     *     JwtUtil.CLAIM_ROLES,     roles,
     *     JwtUtil.CLAIM_TENANT_ID, user.getTenantId()  // null nếu SUPER_ADMIN
     *   )
     * </pre>
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
        Long tenantId = null;
        if (user instanceof User) {
            tenantId = ((User) user).getTenantId();
        }
        Map<String, Object> extraClaims = new java.util.HashMap<>();
        extraClaims.put(CLAIM_TENANT_ID, tenantId);
        return buildToken(user.getUsername(), extraClaims, refreshTokenExpiration);
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

    /**
     * Trích xuất tenantId từ JWT payload.
     *
     * Trả về null nếu:
     *   (a) token không có claim "tenantId" (Refresh Token, token cũ trước migration)
     *   (b) claim "tenantId" được set null tường minh (SUPER_ADMIN)
     *
     * JwtAuthFilter sẽ xử lý null bằng cách không set TenantContextHolder
     * → TenantIdentifierResolver trả về null → Hibernate bypass filter (Super Admin mode).
     *
     * @param token JWT token đã được parse thành công
     * @return tenantId (Long) hoặc null
     */
    public Long extractTenantId(String token) {
        Object tenantIdRaw = extractClaim(token, claims -> claims.get(CLAIM_TENANT_ID));
        if (tenantIdRaw == null) {
            return null;
        }
        // JJWT deserialize số nguyên thành Integer (nếu value nhỏ) hoặc Long.
        // Phải cast qua Number để xử lý cả 2 trường hợp an toàn.
        return ((Number) tenantIdRaw).longValue();
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
