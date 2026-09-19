/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : HmacUtils.java
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * HMAC-SHA256 签名工具
 *
 * @author Hsi Chu
 * @since 1.0.0
 */
public final class HmacUtils {

    /**
     * hmac工具类
     * @return 私有
     */
    private HmacUtils() {
    }

    /**
     * hmacSha256Hex
     * @param secret 密钥
     * @param data 数据
     * @return 字符串
     */
    public static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            /**
             * toHex
             * @return 返回
             */
            return toHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("计算HMAC失败", e);
        }
    }

    /**
     * toHex
     * @param bytes 字节数组
     * @return 字符串
     */
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 验证HMAC签名
     *
     * @param secret    密钥
     * @param data      数据
     * @param signature 签名
     * @return 是否验证通过
     */
    public static boolean verifyHmacSha256(String secret, String data, String signature) {
        try {
            String expectedSignature = hmacSha256Hex(secret, data);
            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
