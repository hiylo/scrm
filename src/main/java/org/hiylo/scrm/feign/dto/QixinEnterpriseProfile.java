/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : QixinEnterpriseProfile.java
 * Date : 2026/10/23 09:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 归一化企业工商快照（启信宝照面 + 经营异常解析产物）。
 * <p>
 * 巡检域以该对象构建状态 JSON 并计算 SHA-256 哈希做变化比对; 字段是
 * 「巡检需要的最小集」: 经营状态 / 异常名录 / 注销吊销 / 法人 / 股东 / 行政处罚摘要。
 * 第三方接口原始字段在 {@code rawJson} 中完整保留, 供人工复核与排查。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class QixinEnterpriseProfile {

    /** 企业名称 */
    private String enterpriseName;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 法定代表人 */
    private String legalPerson;

    /** 经营状态（在营/存续/注销/吊销/迁出/停业等, 归一化原文） */
    private String businessStatus;

    /** 注销日期（yyyy-MM-dd, 可空） */
    private String cancelDate;

    /** 吊销日期（yyyy-MM-dd, 可空） */
    private String revokeDate;

    /** 是否列入经营异常名录 */
    private boolean abnormal;

    /** 经营异常列入原因（可空） */
    private String abnormalReason;

    /** 经营异常列入日期（yyyy-MM-dd, 可空） */
    private String abnormalDate;

    /** 股东列表（名称 + 持股比例, 字符串形式） */
    private List<String> shareholders = new ArrayList<>();

    /** 行政处罚摘要（可空） */
    private String administrativePenalty;

    /** 查询时间 */
    private String queryTime;

    /** 第三方接口原始响应 JSON（完整保留, 脱敏后仅内部使用） */
    private String rawJson;
}