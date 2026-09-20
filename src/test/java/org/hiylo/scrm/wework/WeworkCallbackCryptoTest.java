/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkCallbackCryptoTest.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.wework;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WeworkCallbackCrypto 单元测试
 * <p>
 * 验证企业微信回调消息的签名验证、AES-256-CBC 加解密及往返一致性。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("WeworkCallbackCrypto 单元测试")
class WeworkCallbackCryptoTest {

    /** 测试用 corpId */
    private static final String CORP_ID = "ww1234567890abcdef";

    /** 测试用 Token */
    private static final String TOKEN = "test_token";

    /** 测试用 EncodingAESKey (43 字符) */
    private static final String AES_KEY = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";

    // ==================== verifySignature ====================

    @Test
    @DisplayName("verifySignature_success: 已知测试向量签名验证通过")
    void verifySignature_success() {
        // SHA1(sort(["test_token", "1234567890", "test_nonce", "test_echo"]))
        // 排序后拼接: "1234567890test_echotest_noncetest_token"
        String expectedSignature = "0f309174f270524fa7d28c80a8e79ccf6bb4cebe";

        boolean result = WeworkCallbackCrypto.verifySignature(
                TOKEN, "1234567890", "test_nonce", "test_echo", expectedSignature);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("verifySignature_wrongSignature: 签名不匹配返回 false")
    void verifySignature_wrongSignature() {
        boolean result = WeworkCallbackCrypto.verifySignature(
                TOKEN, "1234567890", "test_nonce", "test_echo", "wrong_signature");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verifySignature_differentParams: 不同参数产生不同签名")
    void verifySignature_differentParams() {
        // 同一 token, 不同 timestamp/nonce/echoStr 应产生不同签名
        String sig1 = computeSignatureManually(TOKEN, "111", "aaa", "echo1");
        String sig2 = computeSignatureManually(TOKEN, "222", "bbb", "echo2");

        assertThat(sig1).isNotEqualTo(sig2);

        assertThat(WeworkCallbackCrypto.verifySignature(TOKEN, "111", "aaa", "echo1", sig1)).isTrue();
        assertThat(WeworkCallbackCrypto.verifySignature(TOKEN, "222", "bbb", "echo2", sig2)).isTrue();
        // 交叉验证失败
        assertThat(WeworkCallbackCrypto.verifySignature(TOKEN, "111", "aaa", "echo1", sig2)).isFalse();
    }

    // ==================== encryptMessage / decryptMessage 往返 ====================

    @Test
    @DisplayName("encryptDecrypt_roundTrip: 加密后解密应返回原始消息")
    void encryptDecrypt_roundTrip() {
        String originalMsg = "hello wework callback test 你好企微";

        String encrypted = WeworkCallbackCrypto.encryptMessage(AES_KEY, CORP_ID, originalMsg);
        assertThat(encrypted).isNotBlank();

        String decrypted = WeworkCallbackCrypto.decryptMessage(AES_KEY, CORP_ID, encrypted);
        assertThat(decrypted).isEqualTo(originalMsg);
    }

    @Test
    @DisplayName("encryptDecrypt_roundTrip_emptyMessage: 空字符串加解密往返")
    void encryptDecrypt_roundTrip_emptyMessage() {
        String originalMsg = "";

        String encrypted = WeworkCallbackCrypto.encryptMessage(AES_KEY, CORP_ID, originalMsg);
        String decrypted = WeworkCallbackCrypto.decryptMessage(AES_KEY, CORP_ID, encrypted);

        assertThat(decrypted).isEqualTo(originalMsg);
    }

    @Test
    @DisplayName("encryptDecrypt_roundTrip_longMessage: 较长消息加解密往返")
    void encryptDecrypt_roundTrip_longMessage() {
        String originalMsg = "a".repeat(500);

        String encrypted = WeworkCallbackCrypto.encryptMessage(AES_KEY, CORP_ID, originalMsg);
        String decrypted = WeworkCallbackCrypto.decryptMessage(AES_KEY, CORP_ID, encrypted);

        assertThat(decrypted).isEqualTo(originalMsg);
    }

    // ==================== decryptMessage 异常 ====================

    @Test
    @DisplayName("decryptMessage_corpIdMismatch: corpId 不匹配抛 IllegalArgumentException")
    void decryptMessage_corpIdMismatch() {
        String originalMsg = "test message";
        String encrypted = WeworkCallbackCrypto.encryptMessage(AES_KEY, CORP_ID, originalMsg);

        assertThatThrownBy(() -> WeworkCallbackCrypto.decryptMessage(AES_KEY, "ww_wrong_corp_id", encrypted))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("corpId 不匹配");
    }

    @Test
    @DisplayName("decryptMessage_invalidPayload: 无效密文抛 IllegalArgumentException")
    void decryptMessage_invalidPayload() {
        // 使用非法 Base64 字符触发解密失败
        // 注意: IllegalArgumentException 可能由 Base64 解码直接抛出（消息不含"消息解密失败"）,
        // 也可能由后续 AES 解密失败包装抛出（消息含"消息解密失败"）, 两种均属于合法行为
        assertThatThrownBy(() -> WeworkCallbackCrypto.decryptMessage(AES_KEY, CORP_ID, "!!!!invalid!!!!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("decryptMessage_wrongKey: 错误密钥解密失败抛异常")
    void decryptMessage_wrongKey() {
        String originalMsg = "test message";
        String encrypted = WeworkCallbackCrypto.encryptMessage(AES_KEY, CORP_ID, originalMsg);

        // 使用不同的 aesKey 解密应失败
        String wrongKey = "ABCDEFG9876543210zyxwvutsrqponmlkjihgfedcba";
        assertThatThrownBy(() -> WeworkCallbackCrypto.decryptMessage(wrongKey, CORP_ID, encrypted))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== 辅助方法 ====================

    /**
     * 手动计算签名, 用于交叉验证
     */
    private String computeSignatureManually(String token, String timestamp, String nonce, String echoStr) {
        try {
            String[] arr = {token, timestamp, nonce, echoStr};
            java.util.Arrays.sort(arr);
            StringBuilder sb = new StringBuilder();
            for (String s : arr) {
                sb.append(s);
            }
            java.security.MessageDigest sha1 = java.security.MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b & 0xFF));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
