/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : EncryptionKeyStartupValidator.java
 * Date : 2026/09/14 12:00:00
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Base64;
import java.util.Set;

/**
 * 加密密钥启动校验配置。
 * <p>
 * 敏感字段数据库加密依赖 {@code SCRM_ACCOUNT_ENCRYPTION_KEY} 环境变量
 * (或配置属性 {@code scrm.account.encryption-key})。
 * 为避免生产环境敏感字段以 PLAIN 明文落库, 非 dev/local/test 环境密钥缺失时启动即抛出
 * {@link IllegalStateException} (fail-closed); dev/local/test 环境仅记录警告 (明文过渡存储,
 * 便于本地开发), 与 {@link CallbackSecretStartupValidator} 的 profile 判定方式保持一致。
 * </p>
 * <p>
 * 密钥已配置时还要校验其<b>格式</b>: {@code AesGcmUtils} 用 {@link Base64#getDecoder()} 解码该值
 * 后直接交给 {@code SecretKeySpec}, 因此必须是无填充标准 Base64 且解码后恰为 16/24/32 字节。
 * 格式错误若不在启动时拦截, 会推迟到第一次写入加密字段时抛 {@code RuntimeException}, 表现为
 * 与密钥毫无关系的接口 500, 极难定位。格式校验不受 profile 影响 —— 一个坏密钥在任何环境都不合法。
 * </p>
 *
 * @author Hsi Chu
 */
@Configuration
@Slf4j
public class EncryptionKeyStartupValidator {

    /** 允许密钥缺失的开发类环境 */
    private static final Set<String> DEV_LIKE_PROFILES = Set.of("dev", "local", "test");

    /** AES-GCM 允许的密钥字节数 (对应 AES-128/192/256) */
    private static final Set<Integer> VALID_KEY_BYTE_LENGTHS = Set.of(16, 24, 32);

    /** 当前激活的 Spring profile (可为空) */
    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    /** 加密密钥, 与 application.yml 的 scrm.account.encryption-key 同源 (Spring 宽松绑定可直接读到同名环境变量) */
    @Value("${scrm.account.encryption-key:}")
    private String encryptionKey;

    /**
     * 启动时校验加密密钥。
     * <p>
     * 非 dev/local/test 环境密钥缺失时抛出 {@link IllegalStateException} 阻断启动,
     * 防止敏感字段明文落库; 已存量 PLAIN 明文数据不做自动迁移, 待密钥配置后按需重写。
     * 密钥存在时校验其 Base64 合法性与解码长度。
     * </p>
     */
    @PostConstruct
    public void validateEncryptionKeyConfigured() {
        if (EncryptedStringConverter.isEncryptionKeyConfigured()) {
            validateKeyFormat(encryptionKey);
            return;
        }
        if (isDevLikeProfile()) {
            log.warn("SCRM_ACCOUNT_ENCRYPTION_KEY 未配置 (config key: scrm.account.encryption-key), "
                    + "敏感字段将以明文存储 (PLAIN: 前缀), 请尽快配置加密密钥");
            return;
        }
        throw new IllegalStateException(
                "环境变量 SCRM_ACCOUNT_ENCRYPTION_KEY 未配置 (config key: scrm.account.encryption-key), "
                        + "敏感字段将明文落库, 生产环境必须显式配置加密密钥");
    }

    /**
     * 校验密钥格式: 标准 Base64, 且解码后 16/24/32 字节。
     * <p>空白时直接返回 —— 缺失与否由 {@link #validateEncryptionKeyConfigured()} 按 profile 决定。</p>
     *
     * @param encryptionKey 待校验密钥, 可为空
     * @throws IllegalStateException 非法 Base64, 或解码后长度不是 16/24/32 字节
     */
    static void validateKeyFormat(String encryptionKey) {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            return;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encryptionKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "SCRM_ACCOUNT_ENCRYPTION_KEY 不是合法的标准 Base64 字符串 (AesGcmUtils 按 Base64 解码该值), "
                            + "生成合规密钥示例: openssl rand -base64 32; 原始错误: " + e.getMessage(), e);
        }
        if (!VALID_KEY_BYTE_LENGTHS.contains(decoded.length)) {
            throw new IllegalStateException(
                    "SCRM_ACCOUNT_ENCRYPTION_KEY 解码后为 " + decoded.length
                            + " 字节, AES-GCM 只接受 16/24/32 字节 (AES-128/192/256), "
                            + "生成合规密钥示例: openssl rand -base64 32");
        }
    }

    /**
     * 判断当前激活 profile 是否属于开发类环境。
     *
     * @return true=dev/local/test 之一 (或未激活任何 profile)
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
}
