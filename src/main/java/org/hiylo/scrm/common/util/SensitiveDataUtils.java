/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : SensitiveDataUtils.java
 * Date : 2026/09/19 21:20:11
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.common.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * 敏感数据工具类
 * <p>提供敏感数据的脱敏展示和 AES-GCM 加密/解密功能，核心功能包括：
 * <ul>
 *   <li>手机号脱敏(maskPhone)：11位手机号中间4位替换为****(如"13812345678"→"138****5678")</li>
 *   <li>身份证脱敏(maskIdCard)：18位身份证号中间8位替换为********(如"110101199001011234"→"110101********1234")</li>
 *   <li>姓名脱敏(maskName)：保留首尾字符，中间替换为*(2字姓名保留首字，3字及以上保留首尾)</li>
 *   <li>邮箱脱敏(maskEmail)：@前保留首字符+****(如"test@example.com"→"t****@example.com")</li>
 *   <li>银行卡脱敏(maskBankCard)：保留前4后4位，中间替换为*(如"6222021234567890"→"6222********7890")</li>
 *   <li>地址脱敏(maskAddress)：保留前半部分，后半部分替换为*</li>
 *   <li>通用脱敏(maskGeneric)：保留前4后4位，中间替换为****(长度不超过8位统一返回****)</li>
 *   <li>AES-GCM加密(encrypt)：使用 AES/GCM/NoPadding 加密，12字节随机IV + 128位认证标签，
 *       密文格式为 Base64(IV + ciphertext + authTag)，密钥支持16/24/32字节</li>
 *   <li>AES-GCM解密(decrypt)：从 Base64 密文中提取 IV 并解密还原明文</li>
 * </ul>
 * AES 密钥通过系统属性 sensitive.aes.key 或环境变量 SENSITIVE_AES_KEY 配置，
 * 可通过 setAesKey 动态更新(支持 AES-128/192/256)。</p>
 * @author Hsi Chu
 */
@Slf4j
public class SensitiveDataUtils {

    /** AES_ALGORITHM */
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    /** GCM_IV_LENGTH */
    private static final int GCM_IV_LENGTH = 12;
    /** GCM_TAG_LENGTH */
    private static final int GCM_TAG_LENGTH = 128;
    /** 复用的安全随机数发生器，避免每次加密都新建实例 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 获取敏感数据工具实例
     */
    private static volatile String aesKey = System.getProperty("sensitive.aes.key",
            System.getenv().getOrDefault("SENSITIVE_AES_KEY", ""));

    /**
     * sensitive数据工具类
     * @return 私有
     */
    private SensitiveDataUtils() {
    }

    /**
     * 设置aes键
     * @param key 键
     */
    public static void setAesKey(String key) {
        if (key == null || (key.length() != 16 && key.length() != 24 && key.length() != 32)) {
            throw new IllegalArgumentException("AES key must be 16, 24, or 32 bytes");
        }
        aesKey = key;
    }

    /**
     * 手机号脱敏
     * @param phone 手机号
     * @return 字符串
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 身份证脱敏：18位身份证号中间8位替换为********
     *
     * @param idCard 身份证号
     * @return 脱敏后的身份证号，长度不为18时原样返回
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() != 18) {
            return idCard;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(14);
    }

    /**
     * 姓名脱敏：保留首尾字符，中间替换为*（2字姓名仅保留首字）
     *
     * @param name 姓名
     * @return 脱敏后的姓名，长度不超过1时原样返回
     */
    public static String maskName(String name) {
        if (name == null || name.length() <= 1) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(name.charAt(0));
        for (int i = 1; i < name.length() - 1; i++) {
            sb.append("*");
        }
        sb.append(name.charAt(name.length() - 1));
        return sb.toString();
    }

    /**
     * 邮箱脱敏
     * @param email 邮箱
     * @return 字符串
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        String localPart = parts[0];
        String domainPart = parts[1];

        if (localPart.length() <= 1) {
            return email;
        }

        String maskedLocal = localPart.charAt(0) + "****";
        return maskedLocal + "@" + domainPart;
    }

    /**
     * 银行卡脱敏：保留前4位与后4位，中间替换为*
     *
     * @param bankCard 银行卡号
     * @return 脱敏后的银行卡号，长度小于8时原样返回
     */
    public static String maskBankCard(String bankCard) {
        if (bankCard == null || bankCard.length() < 8) {
            return bankCard;
        }
        int length = bankCard.length();
        int maskLength = length - 8;
        StringBuilder sb = new StringBuilder();
        sb.append(bankCard.substring(0, 4));
        for (int i = 0; i < maskLength; i++) {
            sb.append("*");
        }
        sb.append(bankCard.substring(length - 4));
        return sb.toString();
    }

    /**
     * 获取aes键
     * @return 键
     * @throws IllegalStateException 未配置密钥时抛出
     */
    private static String requireAesKey() {
        if (aesKey == null || aesKey.isEmpty()) {
            throw new IllegalStateException(
                    "未配置AES密钥, 请通过系统属性 sensitive.aes.key 或环境变量 SENSITIVE_AES_KEY 设置");
        }
        return aesKey;
    }

    /**
     * 加密
     * @param data 数据
     * @return 字符串
     */
    public static String encrypt(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        String key = requireAesKey();
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("加密失败", e);
            /**
             * runtime异常
             * @return throw new
             */
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 解密
     * @param encryptedData 加密数据
     * @return 字符串
     */
    public static String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }
        String key = requireAesKey();
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedData);

            if (combined.length < GCM_IV_LENGTH + GCM_TAG_LENGTH / 8) {
                log.error("解密失败: 密文长度不足，数据不完整");
                return null;
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] decrypted = cipher.doFinal(cipherText);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("解密失败", e);
            /**
             * runtime异常
             * @return throw new
             */
            throw new RuntimeException("解密失败", e);
        }
    }

    /**
     * mask地址
     * @param address 地址
     * @return 字符串
     */
    public static String maskAddress(String address) {
        if (address == null || address.length() < 10) {
            return address;
        }
        int maskStart = address.length() / 2;
        StringBuilder sb = new StringBuilder();
        sb.append(address.substring(0, maskStart));
        for (int i = maskStart; i < address.length(); i++) {
            sb.append("*");
        }
        return sb.toString();
    }

    /**
     * 通用脱敏：保留前4位与后4位，中间替换为****（长度不超过8位统一返回****）
     *
     * @param value 待脱敏的值
     * @return 脱敏后的值
     */
    public static String maskGeneric(String value) {
        if (value == null || value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    /** 匹配 API Key 等敏感字段的正则 */
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|apikey)[\"\\s:=]+([^\"\\s,}]+)",
            Pattern.CASE_INSENSITIVE
    );

    /** 匹配密码等敏感字段的正则 */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(?i)(password|pwd|pass)[\"\\s:=]+([^\"\\s,}]+)",
            Pattern.CASE_INSENSITIVE
    );

    /** 匹配数据库连接串中口令的正则 */
    private static final Pattern DB_PASSWORD_PATTERN = Pattern.compile(
            "://[^:]+:([^@]+)@",
            Pattern.CASE_INSENSITIVE
    );

    /** 匹配私钥等敏感字段的正则 */
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "(?i)(private[_-]?key|privatekey)[\"\\s:=]+([^\"\\s,}]+)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 对日志文本中的敏感信息（API Key、密码、数据库口令、私钥等）进行遮蔽
     *
     * @param message 原始日志文本
     * @return 脱敏后的日志文本；入参为空字符串或 null 时原样返回
     */
    public static String maskSensitiveMessage(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }

        String masked = message;
        masked = API_KEY_PATTERN.matcher(masked).replaceAll(
                m -> m.group(1) + "=" + maskGeneric(m.group(2))
        );
        masked = PASSWORD_PATTERN.matcher(masked).replaceAll(
                m -> m.group(1) + "=" + maskGeneric(m.group(2))
        );
        masked = DB_PASSWORD_PATTERN.matcher(masked).replaceAll(
                m -> "://" + maskGeneric(m.group(1)) + ":***@"
        );
        masked = PRIVATE_KEY_PATTERN.matcher(masked).replaceAll(
                m -> m.group(1) + "=" + maskGeneric(m.group(2))
        );
        return masked;
    }

    /**
     * 对任意对象进行日志脱敏，空对象原样返回
     *
     * @param obj 待脱敏对象
     * @return 脱敏后的对象字符串；入参为 null 时返回 null
     */
    public static Object maskSensitiveObject(Object obj) {
        if (obj == null) {
            return null;
        }
        return maskSensitiveMessage(obj.toString());
    }
}
