/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : QixinEnterpriseApi.java
 * Date : 2026/10/23 09:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign;

import org.hiylo.scrm.feign.dto.QixinEnterpriseProfile;

/**
 * 启信宝（启信慧眼 data.qixin.com）企业工商信息查询接口契约。
 * <p>
 * 按 T02 选型结论, 巡检主选启信宝: 工商照面（经营状态 / 注销 / 吊销 / 法人 / 股东）
 * 与经营异常两个接口满足巡检 7 项核心字段, 且单价最低（¥0.15/次）。
 * </p>
 * <p>
 * 本接口只声明「Feign 风格」的方法契约（一个方法 = 一个远程端点, 入参为查询键,
 * 返回体为归一化后的对象）; 实际传输由 {@link QixinEnterpriseApiClient} 用
 * {@code RestClient} 完成 —— 与本项目既有的 {@code AiChatClient} 一致, 避免为单个
 * 第三方 HTTP 接口引入 spring-cloud-openfeign 全家桶。
 * </p>
 * <p><b>配置占位</b>: 访问凭证 {@code scrm.inspection.qixin.token} 只从配置/环境变量读取,
 * 不落库、不入日志（见 D05 报告「API key 配置项清单」）。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface QixinEnterpriseApi {

    /**
     * 查询企业工商照面（经营状态 / 注销 / 吊销 / 法人 / 股东 / 行政处罚摘要）。
     *
     * @param enterpriseName 企业名称（与 creditCode 至少一个非空）
     * @param creditCode     统一社会信用代码（优先使用）
     * @return 归一化工商快照
     * @throws QixinApiException 未启用 / 凭证缺失 / 远程调用失败 / 返回码非成功
     */
    QixinEnterpriseProfile queryProfile(String enterpriseName, String creditCode) throws QixinApiException;

    /**
     * 查询企业经营异常名录（列入/移出原因、日期、机关）。
     *
     * @param enterpriseName 企业名称
     * @param creditCode     统一社会信用代码
     * @return 归一化工商快照（仅填充经营异常项与基础标识）
     * @throws QixinApiException 未启用 / 凭证缺失 / 远程调用失败 / 返回码非成功
     */
    QixinEnterpriseProfile queryAbnormal(String enterpriseName, String creditCode) throws QixinApiException;
}