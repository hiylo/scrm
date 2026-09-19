/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkCallbackCrypto.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.wework;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * 企业微信回调消息加解密工具类
 * <p>
 * 实现企业微信回调 URL 验签、消息体 AES-256-CBC 解密与回复消息加密。
 * 加解密算法遵循企业微信官方文档:
 * <ul>
 *   <li>签名验证: SHA1(sort([token, timestamp, nonce, echoStr]))</li>
 *   <li>消息解密: AES-256-CBC, key = Base64(EncodingAESKey + "="), iv = key 前 16 字节</li>
 *   <li>明文格式: random(16) + msgLen(4) + msg + corpId</li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
public final class WeworkCallbackCrypto {

    /** AES 算法/模式/填充 */
    private static final String AES_CIPHER = "AES/CBC/PKCS5Padding";

    /** AES 密钥算法 */
    private static final String AES_KEY_ALGORITHM = "AES";

    /** SHA-1 算法 */
    private static final String SHA1_ALGORITHM = "SHA-1";

    /** 安全随机数生成器（复用实例，避免每次 new 消耗熵池） */
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    private WeworkCallbackCrypto() {
        // 工具类, 禁止实例化
    }

    /**
     * 验证企业微信回调签名
     * <p>
     * 将 token, timestamp, nonce, echoStr 按字典序排序拼接后做 SHA-1,
     * 与企业微信传入的 signature 比对。
     * </p>
     *
     * @param token     回调 Token (企业在企业微信后台配置)
     * @param timestamp 时间戳
     * @param nonce     随机数
     * @param echoStr   验证字符串 (GET 验证时为 echostr, POST 时为 Encrypt 密文)
     * @param signature 企业微信传入的签名
     * @return 签名验证通过返回 true, 否则 false
     */
    public static boolean verifySignature(String token, String timestamp, String nonce,
                                          String echoStr, String signature) {
        String computed = computeSignature(token, timestamp, nonce, echoStr);
        boolean valid = computed != null && computed.equals(signature);
        if (!valid) {
            log.warn("企业微信回调签名验证失败: computed={}, expected={}", computed, signature);
        }
        return valid;
    }

    /**
     * 计算签名 SHA1(sort([token, timestamp, nonce, echoStr]))
     *
     * @param token     回调 Token
     * @param timestamp 时间戳
     * @param nonce     随机数
     * @param echoStr   验证字符串
     * @return SHA-1 签名 (十六进制小写), 异常时返回 null
     */
    private static String computeSignature(String token, String timestamp, String nonce, String echoStr) {
        try {
            String[] arr = {token, timestamp, nonce, echoStr};
            Arrays.sort(arr);
            StringBuilder sb = new StringBuilder();
            for (String s : arr) {
                sb.append(s);
            }
            MessageDigest sha1 = MessageDigest.getInstance(SHA1_ALGORITHM);
            byte[] digest = sha1.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            log.error("计算企业微信签名异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解密企业微信回调消息
     * <p>
     * AES-256-CBC 解密, 密钥由 EncodingAESKey (43 字符) + "=" 拼接后 Base64 解码得到 32 字节密钥,
     * 初始向量 IV 为密钥前 16 字节。解密后去掉 random(16) + msgLen(4) 前缀和 corpId 后缀,
     * 提取消息明文。
     * </p>
     *
     * @param aesKey  EncodingAESKey (43 字符), 企业微信后台配置
     * @param corpId   企业 CorpID
     * @param encryptedXml 密文字符串 (XML 中 &lt;Encrypt&gt; 元素值)
     * @return 解密后的消息明文
     * @throws IllegalArgumentException 解密失败或 corpId 校验不匹配
     */
    public static String decryptMessage(String aesKey, String corpId, String encryptedXml) {
        try {
            byte[] key = Base64.getDecoder().decode(aesKey + "=");
            byte[] iv = Arrays.copyOfRange(key, 0, 16);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, AES_KEY_ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(AES_CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec);

            byte[] encrypted = Base64.getDecoder().decode(encryptedXml);
            byte[] decrypted = cipher.doFinal(encrypted);

            // 明文格式: random(16字节) + msgLen(4字节, 网络字节序) + msg + corpId
            // 跳过前 16 字节随机串
            int pos = 16;
            // 读取 4 字节消息长度 (网络字节序, big-endian)
            int msgLen = ((decrypted[pos] & 0xFF) << 24)
                    | ((decrypted[pos + 1] & 0xFF) << 16)
                    | ((decrypted[pos + 2] & 0xFF) << 8)
                    | (decrypted[pos + 3] & 0xFF);
            pos += 4;

            String msg = new String(decrypted, pos, msgLen, StandardCharsets.UTF_8);
            pos += msgLen;

            String receivedCorpId = new String(decrypted, pos, decrypted.length - pos, StandardCharsets.UTF_8);
            if (!corpId.equals(receivedCorpId)) {
                log.warn("企业微信解密 corpId 不匹配: expected={}, received={}", corpId, receivedCorpId);
                throw new IllegalArgumentException("corpId 不匹配: expected=" + corpId + ", received=" + receivedCorpId);
            }
            return msg;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("企业微信消息解密异常: {}", e.getMessage(), e);
            throw new IllegalArgumentException("消息解密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加密企业微信回复消息
     * <p>
     * 按企业微信要求的格式: random(16) + msgLen(4) + msg + corpId 拼接后
     * AES-256-CBC 加密, 再 Base64 编码返回。
     * </p>
     *
     * @param aesKey  EncodingAESKey (43 字符)
     * @param corpId   企业 CorpID
     * @param replyMsg 回复消息明文
     * @return Base64 编码的密文
     * @throws IllegalArgumentException 加密失败
     */
    public static String encryptMessage(String aesKey, String corpId, String replyMsg) {
        try {
            byte[] key = Base64.getDecoder().decode(aesKey + "=");
            byte[] iv = Arrays.copyOfRange(key, 0, 16);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, AES_KEY_ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // 拼接明文: random(16) + msgLen(4) + msg + corpId
            byte[] randomBytes = generateRandomBytes(16);
            byte[] msgBytes = replyMsg.getBytes(StandardCharsets.UTF_8);
            byte[] corpIdBytes = corpId.getBytes(StandardCharsets.UTF_8);

            byte[] msgLenBytes = new byte[4];
            int msgLen = msgBytes.length;
            msgLenBytes[0] = (byte) ((msgLen >> 24) & 0xFF);
            msgLenBytes[1] = (byte) ((msgLen >> 16) & 0xFF);
            msgLenBytes[2] = (byte) ((msgLen >> 8) & 0xFF);
            msgLenBytes[3] = (byte) (msgLen & 0xFF);

            byte[] plaintext = new byte[randomBytes.length + msgLenBytes.length + msgBytes.length + corpIdBytes.length];
            int offset = 0;
            System.arraycopy(randomBytes, 0, plaintext, offset, randomBytes.length);
            offset += randomBytes.length;
            System.arraycopy(msgLenBytes, 0, plaintext, offset, msgLenBytes.length);
            offset += msgLenBytes.length;
            System.arraycopy(msgBytes, 0, plaintext, offset, msgBytes.length);
            offset += msgBytes.length;
            System.arraycopy(corpIdBytes, 0, plaintext, offset, corpIdBytes.length);

            Cipher cipher = Cipher.getInstance(AES_CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plaintext);

            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("企业微信消息加密异常: {}", e.getMessage(), e);
            throw new IllegalArgumentException("消息加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成指定长度的随机字节
     *
     * @param length 字节长度
     * @return 随机字节数组
     */
    private static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * 字节数组转十六进制小写字符串
     *
     * @param bytes 字节数组
     * @return 十六进制小写字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}
