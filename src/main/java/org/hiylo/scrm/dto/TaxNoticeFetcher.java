/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : TaxNoticeFetcher.java
 * Date : 2026/10/23 09:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 税务公开公告抓取结果。
 * <p>
 * 一次抓取动作（一个省份 / 一个查询入口）的产出: 命中的公告记录列表 +
 * 本轮是否需要验证码继续重试 + 抓取错误描述。命中记录只保留与授权客户企业
 * 匹配的部分（按 T03 合规红线, 无关企业明细不落库）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public class TaxNoticeFetcher {

    /** 公告记录列表（可能为空） */
    private final List<TaxNoticeRecord> records;

    /** 是否需要验证码重试（B 类查询应用命中图形验证码时 true） */
    private final boolean needCaptchaRetry;

    /** 抓取错误描述（可空） */
    private final String errorMessage;

    /**
     * 构造抓取结果。
     *
     * @param records          公告记录列表（不可变）
     * @param needCaptchaRetry 是否需要验证码重试
     * @param errorMessage     抓取错误描述
     */
    public TaxNoticeFetcher(List<TaxNoticeRecord> records, boolean needCaptchaRetry, String errorMessage) {
        this.records = records == null ? new ArrayList<>() : records;
        this.needCaptchaRetry = needCaptchaRetry;
        this.errorMessage = errorMessage;
    }

    /**
     * 获取公告记录列表。
     *
     * @return 公告记录列表（非空）
     */
    public List<TaxNoticeRecord> getRecords() {
        return records;
    }

    /**
     * 是否需要验证码重试。
     *
     * @return true 表示命中图形验证码, 应由上层重取验证码重试
     */
    public boolean isNeedCaptchaRetry() {
        return needCaptchaRetry;
    }

    /**
     * 获取抓取错误描述。
     *
     * @return 错误描述（可能为 null）
     */
    public String getErrorMessage() {
        return errorMessage;
    }
}