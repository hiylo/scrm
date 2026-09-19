/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : EncryptionKeyStartupValidatorTest.java
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link EncryptionKeyStartupValidator#validateKeyFormat(String)} 单元测试。
 * <p>
 * 重点是守住 docker-compose.yml 早期踩过的坑: 默认密钥 {@code dev-only-32-bytes-key-change-me!}
 * 长度"看起来"对, 但 AesGcmUtils 是按 Base64 解的, 该值含 '-' 与 '!' 直接抛
 * IllegalArgumentException, 且只在第一次写加密字段时才炸。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("加密密钥启动校验: Base64 格式与 AES 长度")
class EncryptionKeyStartupValidatorTest {

    /** 生成 32 字节密钥的合规样例 (与 .env.example / docker-compose.yml 默认值同形态) */
    private static final String VALID_32_BYTE_KEY = "c2NybS1kZXYtYWVzLTI1Ni1rZXktY2hhbmdlLW1lISE=";

    private static String base64Of(int rawBytes) {
        byte[] raw = new byte[rawBytes];
        for (int i = 0; i < rawBytes; i++) {
            raw[i] = (byte) ('a' + (i % 26));
        }
        return Base64.getEncoder().encodeToString(raw);
    }

    @Test
    @DisplayName("合规密钥放行: 解码后 16/24/32 字节均可")
    void validateKeyFormat_acceptsAesKeyLengths() {
        assertThatCode(() -> EncryptionKeyStartupValidator.validateKeyFormat(VALID_32_BYTE_KEY))
                .doesNotThrowAnyException();
        assertThatCode(() -> EncryptionKeyStartupValidator.validateKeyFormat(base64Of(16)))
                .doesNotThrowAnyException();
        assertThatCode(() -> EncryptionKeyStartupValidator.validateKeyFormat(base64Of(24)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("compose 旧默认值被拒: 含 '-' 与 '!' 不是合法 Base64")
    void validateKeyFormat_rejectsLegacyComposeDefault() {
        assertThatThrownBy(() -> EncryptionKeyStartupValidator.validateKeyFormat("dev-only-32-bytes-key-change-me!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不是合法的标准 Base64")
                .hasMessageContaining("openssl rand -base64 32")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("URL-safe Base64 密钥被拒: AesGcmUtils 用的是标准解码器, 含 '-' 即非法")
    void validateKeyFormat_rejectsUrlSafeAlphabet() {
        // 同一份 32 字节原文的两种编码: 标准字母表用 '+' '/' , url-safe 换成 '-' '_'
        String standard = "ISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0A=";
        String urlSafe = standard.replace('+', '-').replace('/', '_');
        assertThatCode(() -> EncryptionKeyStartupValidator.validateKeyFormat(standard))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> EncryptionKeyStartupValidator.validateKeyFormat(urlSafe))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不是合法的标准 Base64");
    }

    @Test
    @DisplayName("Base64 合法但长度不是 16/24/32 字节时拒绝, 错误信息给出实际字节数")
    void validateKeyFormat_rejectsWrongDecodedLength() {
        String twentyNineBytes = base64Of(29);
        assertThatThrownBy(() -> EncryptionKeyStartupValidator.validateKeyFormat(twentyNineBytes))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("29 字节")
                .hasMessageContaining("16/24/32");
    }

    @Test
    @DisplayName("空值放行: 缺失与否由 profile 分支决定, 不在格式校验里报错")
    void validateKeyFormat_ignoresBlank() {
        assertThatCode(() -> EncryptionKeyStartupValidator.validateKeyFormat(null)).doesNotThrowAnyException();
        assertThatCode(() -> EncryptionKeyStartupValidator.validateKeyFormat("")).doesNotThrowAnyException();
        assertThatCode(() -> EncryptionKeyStartupValidator.validateKeyFormat("   ")).doesNotThrowAnyException();
    }
}
