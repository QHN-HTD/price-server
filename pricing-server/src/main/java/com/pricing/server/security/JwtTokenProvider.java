package com.pricing.server.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Token 提供器
 * <p>
 * 负责 JWT Token 的创建、解析、验证。
 * 使用 HMAC-SHA256 签名算法，Token 有效期 30 分钟。
 * </p>
 *
 * <p><b>安全设计：</b>
 * <ul>
 *   <li>密钥通过环境变量注入，不硬编码</li>
 *   <li>Token 包含角色信息，用于 RBAC 授权</li>
 *   <li>Token 过期自动失效，无状态登出</li>
 * </ul>
 * </p>
 *
 * @author PriceWise Team
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:1800000}")
    private long expirationMs;

    @Value("${jwt.issuer:smart-pricing-engine}")
    private String issuer;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        // 解码 Base64 密钥并创建 HMAC-SHA 密钥
        byte[] keyBytes = Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(secret.getBytes()));
        // 确保密钥长度 ≥ 256 bits
        if (keyBytes.length < 32) {
            log.warn("JWT 密钥长度不足 256 bits，安全性降低。建议使用更长的密钥。");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 创建 JWT Token。
     *
     * @param username 用户名
     * @param role     角色（ADMIN / ANALYST）
     * @return JWT Token 字符串
     */
    public String createToken(String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("username", username);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 从 Token 中提取用户名。
     *
     * @param token JWT Token
     * @return 用户名
     */
    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * 从 Token 中提取角色。
     *
     * @param token JWT Token
     * @return 角色字符串
     */
    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * 验证 Token 是否有效。
     *
     * @param token JWT Token
     * @return true 表示有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT Token 已过期: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.debug("不支持的 JWT Token: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.debug("无效的 JWT Token 格式: {}", e.getMessage());
        } catch (SignatureException e) {
            log.warn("JWT 签名验证失败（可能为伪造Token）: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.debug("JWT Token 为空: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 获取 Token 剩余有效时间（毫秒）。
     *
     * @param token JWT Token
     * @return 剩余毫秒数
     */
    public long getRemainingTime(String token) {
        try {
            Date expiration = parseClaims(token).getExpiration();
            return Math.max(0, expiration.getTime() - System.currentTimeMillis());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 解析 Token 的 Claims。
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
