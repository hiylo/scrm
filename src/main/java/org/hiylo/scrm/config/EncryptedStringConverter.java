/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : EncryptedStringConverter.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.common.util.AesGcmUtils;

import java.util.Base64;

/**
 * JPA AttributeConverter：使用 AES-256-GCM 对敏感字段进行数据库加密存储
 * <p>
 * 加密值以 "ENC:" 前缀标记，未加密的明文值以 "PLAIN:" 前缀标记（用于迁移过渡期）。
 * 加密密钥从环境变量 SCRM_ACCOUNT_ENCRYPTION_KEY 或配置属性 scrm.account.encryption-key 获取，
 * 必须为 Base64 编码的 32 字节（256 位）密钥。若密钥未配置，dev/local/test 环境降级为
 * 明文存储并输出警告日志；生产环境 (非 dev/local/test) 由
 * {@link EncryptionKeyStartupValidator} 在启动阶段直接拒绝启动 (fail-closed)。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    /** 加密值标记前缀 */
    static final String ENC_PREFIX = "ENC:";

    /** 明文值标记前缀（迁移兼容） */
    static final String PLAIN_PREFIX = "PLAIN:";

    /** 加密密钥（Base64 编码的 256 位 AES 密钥），null 表示未配置 */
    private static final String ENCRYPTION_KEY;

    static {
        String key = System.getenv("SCRM_ACCOUNT_ENCRYPTION_KEY");
        if (key == null || key.isBlank()) {
            key = System.getProperty("scrm.account.encryption-key");
        }
        ENCRYPTION_KEY = (key != null && !key.isBlank()) ? key.trim() : null;
        if (ENCRYPTION_KEY == null) {
            log.warn("[EncryptedStringConverter] SCRM_ACCOUNT_ENCRYPTION_KEY 未配置, 敏感字段将以明文存储 (PLAIN: 前缀), 请尽快配置加密密钥");
        }
    }

    /**
     * 判断加密密钥是否已配置。
     *
     * @return true=密钥可用; false=密钥缺失 (生产环境启动校验将据此拒绝启动)
     */
    public static boolean isEncryptionKeyConfigured() {
        return ENCRYPTION_KEY != null;
    }

    /**
     * 将实体属性加密后写入数据库
     *
     * @param attribute 明文字段值 (可为 null)
     * @return 加密后的值 (带 ENC: 前缀) 或明文降级值; null 原样返回
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        if (ENCRYPTION_KEY == null) {
            // 密钥未配置, 降级为明文存储
            return PLAIN_PREFIX + attribute;
        }
        try {
            String encrypted = AesGcmUtils.encrypt(ENCRYPTION_KEY, attribute);
            return ENC_PREFIX + encrypted;
        } catch (Exception e) {
            log.error("[EncryptedStringConverter] 加密失败, 降级为明文存储: {}", e.getMessage());
            return PLAIN_PREFIX + attribute;
        }
    }

    /**
     * 将数据库中的密文解密后写入实体属性
     *
     * @param dbData 数据库存储值 (可为 null)
     * @return 解密后的明文字段值; null 原样返回, 解密失败返回 null
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        if (dbData.startsWith(ENC_PREFIX)) {
            String cipherText = dbData.substring(ENC_PREFIX.length());
            if (ENCRYPTION_KEY == null) {
                log.error("[EncryptedStringConverter] 存在加密数据但密钥未配置, 无法解密");
                return null;
            }
            try {
                return AesGcmUtils.decrypt(ENCRYPTION_KEY, cipherText);
            } catch (Exception e) {
                log.error("[EncryptedStringConverter] 解密失败: {}", e.getMessage());
                return null;
            }
        }
        if (dbData.startsWith(PLAIN_PREFIX)) {
            // 明文值, 直接去除前缀返回（迁移兼容：下次写入时将自动加密）
            return dbData.substring(PLAIN_PREFIX.length());
        }
        // 无前缀的旧数据（迁移前已存在的明文值）, 直接返回
        return dbData;
    }
}
