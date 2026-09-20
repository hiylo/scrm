/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AesGcmUtils.java
 * Date : 2026/09/19 21:20:11
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Arrays;

/**
 * AES-GCM 加密解密工具类
 * 提供对称加密功能，安全性高于 AES-ECB
 *
 * @author Hsi Chu
 * @since 1.0.0
 */
public final class AesGcmUtils {

    /** 算法 */
    private static final String ALGORITHM = "AES";
    /** 变换 */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    /** GCM_TAG_BITS */
    private static final int GCM_TAG_BITS = 128;
    /** IV_LENGTH */
    private static final int IV_LENGTH = 12;
    /** 复用的安全随机数发生器，避免每次加密都新建实例 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * AES-GCM加密工具
     */
    private AesGcmUtils() {
    }

    /**
     * 加密
     * @param base64Key base64键
     * @param plaintext 明文
     * @return 字符串
     */
    public static String encrypt(String base64Key, String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] key = Base64.getDecoder().decode(base64Key);
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] output = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(ciphertext, 0, output, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(output);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 解密
     * @param base64Key base64键
     * @param cipherText 密文
     * @return 字符串
     */
    public static String decrypt(String base64Key, String cipherText) {
        if (cipherText == null) {
            return null;
        }
        try {
            byte[] key = Base64.getDecoder().decode(base64Key);
            byte[] input = Base64.getDecoder().decode(cipherText);

            byte[] iv = Arrays.copyOfRange(input, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(input, IV_LENGTH, input.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
