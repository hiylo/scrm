/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : PhoneUtils.java
 * Date : 2026/10/24 08:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.common.util;

import java.util.regex.Pattern;

/**
 * 电话号码规范化工具类。
 * <p>
 * 全项目唯一的号码归一化入口。此前 {@code ScrmCustomerService}、{@code ScrmSalesAppService}、
 * {@code ScrmCustomerBackfillService} 各有一份实现且语义不一致（前者不拒绝非法字符、
 * 不处理裸 {@code 86} 前缀），导致同一号码在不同入口落库形态不同。统一到本类后：
 * </p>
 * <ul>
 *   <li>去除空格、横线、中英文括号、点号</li>
 *   <li>剥离国际前缀：{@code +}、{@code +86}、{@code 0086}、长度恰好为 13 位的裸 {@code 86}</li>
 *   <li>结果必须是 3~20 位纯数字，否则返回 {@code null}（由调用方决定拒绝还是跳过）</li>
 *   <li>固话保持原样（仅去除格式符），如 {@code 010-88886666} → {@code 01088886666}</li>
 * </ul>
 * <p>
 * <b>注意</b>：本类只做格式归一，<b>不</b>校验号码是否真实存在；是否需要 11 位手机号
 * 由各业务场景自行判断（例如补录手机号要求 11 位，而固话场景不要求）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public final class PhoneUtils {

    /** 归一化结果合法形态：3~20 位纯数字 */
    private static final Pattern DIGITS = Pattern.compile("[0-9]{3,20}");

    /** 裸 86 前缀的号码长度（11 位手机号 + 2 位前缀） */
    private static final int BARE_86_LENGTH = 13;

    /** 工具类，私有构造防止实例化 */
    private PhoneUtils() {
    }

    /**
     * 规范化电话号码。
     *
     * @param raw 原始号码（可空）
     * @return 规范化后的纯数字号码；入参为空或格式非法时返回 {@code null}
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "")
                .replace("（", "")
                .replace("）", "")
                .replace(".", "");
        if (value.isEmpty()) {
            return null;
        }
        while (value.startsWith("+")) {
            value = value.substring(1);
        }
        if (value.startsWith("0086") && value.length() > 4) {
            value = value.substring(4);
        } else if (value.startsWith("86") && value.length() == BARE_86_LENGTH) {
            value = value.substring(2);
        }
        if (!DIGITS.matcher(value).matches()) {
            return null;
        }
        return value;
    }

    /**
     * 判断是否为 11 位手机号（归一化后）。
     *
     * @param normalized 已规范化的号码
     * @return 是 11 位纯数字则返回 {@code true}
     */
    public static boolean isMobile(String normalized) {
        return normalized != null && normalized.length() == 11 && DIGITS.matcher(normalized).matches();
    }
}
