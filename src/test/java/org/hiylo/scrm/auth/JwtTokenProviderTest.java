/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : JwtTokenProviderTest.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.auth;

import org.hiylo.scrm.entity.ScrmUserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtTokenProvider 单元测试。
 * <p>
 * 覆盖签发-解析往返一致性、claims 字段完整性、过期时间配置、非法/篡改令牌拒绝与
 * 弱密钥启动阻断等分支。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("JwtTokenProvider JWT 令牌组件单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtTokenProviderTest {

    /** 测试用签名密钥 (满足 HS256 至少 32 字节要求) */
    private static final String SECRET = "test-secret-key-0123456789-abcdefghijklmnopqrstuvwxyz";

    /** 令牌有效期秒数 */
    private static final long EXPIRATION_SECONDS = 86400L;

    /** 测试激活 profile */
    private static final String TEST_PROFILE = "test";

    /** 被测组件 */
    private JwtTokenProvider provider;

    /** 用于签发的测试用户 */
    private ScrmUserEntity user;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, EXPIRATION_SECONDS, TEST_PROFILE);
        provider.init();

        user = new ScrmUserEntity();
        user.setId(9000000001L);
        user.setUsername("alice");
        user.setDisplayName("Alice Zhang");
        user.setRoles("ADMIN,OPERATOR");
        user.setStatus(1);
    }

    @Test
    @DisplayName("签发后解析: uid / username / roles 全部还原")
    void issueThenParse_claimsMatched() {
        String token = provider.issueToken(user);

        JwtTokenProvider.JwtClaims claims = provider.parse(token);

        assertThat(token).hasSizeGreaterThanOrEqualTo(3).contains(".");
        assertThat(claims.uid()).isEqualTo(9000000001L);
        assertThat(claims.username()).isEqualTo("alice");
        assertThat(claims.roles()).containsExactly("ADMIN", "OPERATOR");
    }

    @Test
    @DisplayName("签发后解析: sub 与 username 声明一致")
    void issueThenParse_subjectEqualsUsername() {
        String token = provider.issueToken(user);

        String username = provider.extractUsername(token);

        assertThat(username).isEqualTo("alice");
        assertThat(provider.validate(token)).isTrue();
    }

    @Test
    @DisplayName("roles 为空字符串: 解析出空列表而非 null")
    void issueThenParse_emptyRoles_yieldsEmptyList() {
        user.setRoles("");

        String token = provider.issueToken(user);

        assertThat(provider.parse(token).roles()).isEmpty();
    }

    @Test
    @DisplayName("roles 含空格与空项: 自动 trim 并过滤空项")
    void issueThenParse_rolesTrimmedAndFiltered() {
        user.setRoles(" ADMIN , , OPERATOR ");

        String token = provider.issueToken(user);

        assertThat(provider.parse(token).roles()).containsExactly("ADMIN", "OPERATOR");
    }

    @Test
    @DisplayName("篡改载荷的令牌: validate 返回 false 且 parse 抛 JwtException")
    void tamperedToken_rejected() {
        String token = provider.issueToken(user);
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "x." + parts[2];

        assertThat(provider.validate(tampered)).isFalse();
        assertThat(provider.extractUsername(tampered)).isNull();
        assertThatThrownBy(() -> provider.parse(tampered))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    @DisplayName("空令牌 / null 令牌: validate 返回 false, extractUsername 返回 null")
    void blankToken_rejected() {
        assertThat(provider.validate(null)).isFalse();
        assertThat(provider.validate("")).isFalse();
        assertThat(provider.validate("   ")).isFalse();
        assertThat(provider.extractUsername(null)).isNull();
        assertThat(provider.extractUsername("")).isNull();
        assertThatThrownBy(() -> provider.parse(null))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    @DisplayName("随机字符串令牌: validate 返回 false")
    void randomStringToken_rejected() {
        assertThat(provider.validate("not-a-jwt")).isFalse();
        assertThat(provider.validate("a.b.c")).isFalse();
    }

    @Test
    @DisplayName("过期时间配置: getExpirationSeconds 返回构造入参")
    void expirationSeconds_exposed() {
        assertThat(provider.getExpirationSeconds()).isEqualTo(EXPIRATION_SECONDS);
    }

    @Test
    @DisplayName("短过期时间: 到期后令牌校验失败 (过期语义生效)")
    void expiredToken_rejected() throws InterruptedException {
        // JJWT 将 exp 截断为整秒, 1 秒有效期实际只剩不足 1 秒, 易受 GC 停顿影响而误判;
        // 故取 2 秒有效期 (实际剩余 1~2 秒) + 2.5 秒等待, 保证断言两侧均有安全裕度
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, 2L, TEST_PROFILE);
        shortLived.init();

        String token = shortLived.issueToken(user);
        assertThat(shortLived.validate(token)).isTrue();

        Thread.sleep(2500L);
        assertThat(shortLived.validate(token)).isFalse();
    }

    @Test
    @DisplayName("其他密钥签发的令牌: 当前密钥无法通过校验")
    void tokenSignedByOtherSecret_rejected() {
        JwtTokenProvider other = new JwtTokenProvider(
                "another-secret-key-0123456789-abcdefghijklmnopqrstuvwxyz", EXPIRATION_SECONDS, TEST_PROFILE);
        other.init();

        String token = other.issueToken(user);

        assertThat(provider.validate(token)).isFalse();
    }

    @Test
    @DisplayName("密钥不足 32 字节: init 抛出弱密钥异常 (fail-closed)")
    void weakSecret_initThrows() {
        JwtTokenProvider weak = new JwtTokenProvider("short", EXPIRATION_SECONDS, TEST_PROFILE);

        assertThatThrownBy(weak::init)
                .isInstanceOf(io.jsonwebtoken.security.WeakKeyException.class)
                .hasMessageContaining("长度不足");
    }

    @Test
    @DisplayName("非开发环境使用开发占位密钥: init 抛出异常阻断启动 (fail-closed)")
    void devPlaceholderSecret_inProductionProfile_initThrows() {
        JwtTokenProvider prod = new JwtTokenProvider(
                "scrm-dev-only-change-me-in-production-0123456", EXPIRATION_SECONDS, "prod");

        assertThatThrownBy(prod::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SCRM_JWT_SECRET");
    }

    @Test
    @DisplayName("开发环境使用开发占位密钥: init 不阻断 (仅告警)")
    void devPlaceholderSecret_inDevProfile_initSucceeds() {
        JwtTokenProvider dev = new JwtTokenProvider(
                "scrm-dev-only-change-me-in-production-0123456", EXPIRATION_SECONDS, "dev");

        dev.init();

        String token = dev.issueToken(user);
        assertThat(dev.validate(token)).isTrue();
    }
}
