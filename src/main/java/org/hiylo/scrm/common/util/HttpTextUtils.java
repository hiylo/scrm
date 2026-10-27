/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : HttpTextUtils.java
 * Date : 2026/10/24 14:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.common.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP 文本响应解码工具。
 * <p>
 * <b>解决的问题</b>：不少政务站点返回 {@code Content-Type: text/html} 却不带
 * {@code charset}，Spring {@code RestClient} 的 {@code StringHttpMessageConverter}
 * 会把响应体按 ISO-8859-1 解码，导致 UTF-8 中文变成 {@code å½å®¶ç¨å¡æ»å±} 这类乱码。
 * 广东站带 {@code charset=utf-8} 所以正常，浙江站不带就乱码。
 * </p>
 * <p>
 * 因此抓取端统一取 {@code byte[]}，再用本工具按「响应头 charset → HTML meta charset → 兜底 UTF-8」
 * 的顺序解码。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public final class HttpTextUtils {

    /** HTML meta 里声明的字符集（兼容 charset=utf-8 / charset="UTF-8" / charset='gbk'） */
    private static final Pattern META_CHARSET = Pattern.compile(
            "(?i)<meta[^>]+charset\\s*=\\s*[\"']?\\s*([a-zA-Z0-9_\\-]+)");

    /** 工具类，私有构造防止实例化 */
    private HttpTextUtils() {
    }

    /**
     * 解码 HTTP 文本响应。
     *
     * @param bytes         响应体字节（可空）
     * @param headerCharset 响应头声明的字符集（可空）
     * @return 解码后的文本（字节为空时返回空串）
     */
    public static String decode(byte[] bytes, String headerCharset) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        Charset charset = resolve(headerCharset);
        if (charset == null) {
            charset = resolve(detectMetaCharset(bytes));
        }
        if (charset == null) {
            charset = StandardCharsets.UTF_8;
        }
        return new String(bytes, charset);
    }

    /**
     * 解码 HTTP 文本响应（无响应头 charset 时按 meta 判断）。
     *
     * @param bytes 响应体字节
     * @return 解码后的文本
     */
    public static String decode(byte[] bytes) {
        return decode(bytes, null);
    }

    /**
     * 从字节流前 2KB 里探测 HTML meta charset。
     *
     * @param bytes 响应体字节
     * @return 声明的字符集名（未声明返回 null）
     */
    private static String detectMetaCharset(byte[] bytes) {
        // HTML 的 meta 一定出现在文档头部, 只扫前 2KB 即可（避免大页面全量解码）
        int limit = Math.min(bytes.length, 2048);
        String head = new String(bytes, 0, limit, StandardCharsets.ISO_8859_1);
        Matcher matcher = META_CHARSET.matcher(head);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 把字符集名解析为 {@link Charset}（未知名返回 null 而不是抛异常）。
     *
     * @param name 字符集名
     * @return Charset 或 null
     */
    private static Charset resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Charset.forName(name.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
