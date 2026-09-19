/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : JwtTokenProvider.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.auth;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmUserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JWT 令牌签发与解析组件 (HS256)。
 * <p>
 * 采用 jjwt 0.12.x API: 签发用 {@code Jwts.builder().claims(...)} 组装声明,
 * 解析用 {@code Jwts.parser().verifyWith(key).build().parseSignedClaims(...)} 校验签名与有效期。
 * 声明约定: {@code sub}=username, {@code uid}=用户 ID,
 * {@code roles}=角色列表, {@code username}=username (冗余保留, 便于前端直接读取)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** claim 键: 用户名 (与 sub 同值, 冗余便于前端读取) */
    public static final String CLAIM_USERNAME = "username";

    /** claim 键: 用户 ID */
    public static final String CLAIM_UID = "uid";

    /** claim 键: 角色列表 */
    public static final String CLAIM_ROLES = "roles";

    /** 本地开发用占位密钥前缀, 启动时命中即告警提示必须替换 */
    private static final String DEV_SECRET_PREFIX = "scrm-dev-only-";

    /** HS256 密钥最小字节数 (256 位) */
    private static final int MIN_KEY_BYTES = 32;

    /** 允许保留开发占位密钥的开发类环境 */
    private static final Set<String> DEV_LIKE_PROFILES = Set.of("dev", "local", "test");

    /** 令牌签名密钥明文。⚠ 默认值仅为本地开发用占位密钥, 生产环境必须通过 SCRM_JWT_SECRET 替换 */
    private final String secret;

    /** 令牌有效期 (秒), 默认 86400 即 24 小时 */
    private final long expirationSeconds;

    /** 当前激活的 Spring profile (可为空) */
    private final String activeProfiles;

    /** HS256 签名密钥, 由 {@link #init()} 从 {@link #secret} 派生 */
    private SecretKey signingKey;

    /**
     * 构造 JWT 令牌组件。
     *
     * @param secret            签名密钥明文 (配置项 {@code scrm.security.jwt.secret})
     * @param expirationSeconds 令牌有效期秒数 (配置项 {@code scrm.security.jwt.expiration-seconds})
     * @param activeProfiles    当前激活的 Spring profile (配置项 {@code spring.profiles.active})
     */
    public JwtTokenProvider(
            @Value("${scrm.security.jwt.secret:scrm-dev-only-change-me-in-production-0123456}") String secret,
            @Value("${scrm.security.jwt.expiration-seconds:86400}") long expirationSeconds,
            @Value("${spring.profiles.active:}") String activeProfiles) {
        this.secret = secret;
        this.expirationSeconds = expirationSeconds;
        this.activeProfiles = activeProfiles;
    }

    /**
     * 启动时派生签名密钥并校验密钥强度, 弱密钥直接阻断启动 (fail-closed)。
     * <p>
     * 生产/未声明 profile 环境使用开发占位密钥时同样阻断启动, 防止公开密钥被用来伪造令牌
     * 完全绕过认证; 仅 dev/local/test 环境允许占位密钥并告警提示。
     * </p>
     */
    @PostConstruct
    void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new WeakKeyException("scrm.security.jwt.secret 长度不足 " + MIN_KEY_BYTES
                    + " 字节 (HS256 要求不少于 256 位), 请配置足够长的密钥");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        if (secret.startsWith(DEV_SECRET_PREFIX)) {
            if (isDevLikeProfile()) {
                log.warn("JWT 签名密钥为本地开发占位值 (scrm.security.jwt.secret), 开发环境可用; "
                        + "生产环境必须通过 SCRM_JWT_SECRET 替换为高强度随机密钥");
            } else {
                throw new IllegalStateException(
                        "SCRM_JWT_SECRET 仍为本地开发占位值 (scrm.security.jwt.secret), "
                                + "生产环境必须通过环境变量 SCRM_JWT_SECRET 注入高强度随机密钥, 防止伪造令牌绕过认证");
            }
        }
    }

    /**
     * 判断当前激活 profile 是否属于开发类环境。
     *
     * @return true=dev/local/test 之一 (或未激活任何 profile 时按开发处理)
     */
    private boolean isDevLikeProfile() {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return true;
        }
        return Arrays.stream(activeProfiles.split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .anyMatch(DEV_LIKE_PROFILES::contains);
    }

    /**
     * 为指定用户签发访问令牌。
     * <p>
     * 声明: {@code sub}=username, {@code uid}=用户 ID,
     * {@code roles}=角色列表, {@code username}=username; {@code exp} 由有效期秒数计算。
     * </p>
     *
     * @param user 登录用户实体
     * @return 紧凑格式的 JWT 字符串
     */
    public String issueToken(ScrmUserEntity user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000L);
        Map<String, Object> claims = new LinkedHashMap<>(4);
        claims.put(CLAIM_UID, user.getId());
        claims.put(CLAIM_ROLES, splitRoles(user.getRoles()));
        claims.put(CLAIM_USERNAME, user.getUsername());
        return Jwts.builder()
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 从令牌中提取用户名, 令牌非法时返回 null。
     *
     * @param token JWT 字符串
     * @return 用户名, 校验失败返回 null
     */
    public String extractUsername(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return parse(token).username();
        } catch (JwtException | ClassCastException e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析并校验令牌, 返回身份声明。
     *
     * @param token JWT 字符串
     * @return 令牌声明
     * @throws JwtException 令牌为空、签名无效、令牌过期或格式错误
     */
    public JwtClaims parse(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtException("JWT 令牌为空");
        }
        JwtParser parser = Jwts.parser().verifyWith(signingKey).build();
        Jws<Claims> jws = parser.parseSignedClaims(token);
        Claims claims = jws.getPayload();
        String username = claims.get(CLAIM_USERNAME, String.class);
        return new JwtClaims(toLong(claims.get(CLAIM_UID)), username,
                toStringList(claims.get(CLAIM_ROLES)));
    }

    /**
     * 校验令牌是否有效 (签名正确且未过期)。
     *
     * @param token JWT 字符串
     * @return true=令牌有效
     */
    public boolean validate(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            parse(token);
            return true;
        } catch (JwtException | ClassCastException e) {
            log.debug("JWT 校验失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取令牌有效期 (秒), 供登录接口回传 {@code expiresIn}。
     *
     * @return 有效期秒数
     */
    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    /**
     * 将 claim 原始值安全转换为 Long, 兼容 jjwt 反序列化产生的 Integer/Long 差异。
     *
     * @param value claim 原始值, 可为 null
     * @return 转换后的 Long, 无法转换返回 null
     */
    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 将 claim 原始值转换为字符串列表, 兼容 JSON 数组与逗号分隔字符串两种编码。
     *
     * @param value claim 原始值, 可为 null
     * @return 角色列表, 无角色返回空列表
     */
    private static List<String> toStringList(Object value) {
        List<String> roles = new ArrayList<>();
        if (value == null) {
            return roles;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    roles.add(String.valueOf(item).trim());
                }
            }
        } else {
            for (String role : String.valueOf(value).split(",")) {
                if (!role.isBlank()) {
                    roles.add(role.trim());
                }
            }
        }
        return List.copyOf(roles);
    }

    /**
     * 将逗号分隔的角色字符串拆为角色列表, 用于写入令牌声明。
     *
     * @param rolesCsv 逗号分隔的角色字符串, 可为 null
     * @return 角色列表, 无角色返回空列表
     */
    private static List<String> splitRoles(String rolesCsv) {
        List<String> roles = new ArrayList<>();
        if (rolesCsv == null || rolesCsv.isBlank()) {
            return roles;
        }
        for (String role : rolesCsv.split(",")) {
            if (!role.isBlank()) {
                roles.add(role.trim());
            }
        }
        return List.copyOf(roles);
    }

    /**
     * JWT 令牌声明记录。
     *
     * @param uid      用户 ID
     * @param username 用户名
     * @param roles    角色列表 (如 ADMIN / OPERATOR / MANAGER / SALES / VIEWER)
     */
    public record JwtClaims(Long uid, String username, List<String> roles) {
    }
}
