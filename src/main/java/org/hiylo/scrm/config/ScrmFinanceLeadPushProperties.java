/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmFinanceLeadPushProperties.java
 * Date : 2026/10/23 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 财税模块⑤ 高意向线索推送配置。
 * <p>
 * 绑定 {@code scrm.finance-lead-push.*}: 高意向（SUPER_HOT / HOT）线索经
 * NotificationCenter 推送销售时的接收人与模板配置。
 * </p>
 * <ul>
 *   <li>enabled - 推送总开关（默认 true; false 时仅记录不发送）</li>
 *   <li>template-code - 通知模板编码（默认 lead-hot-push, V13 迁移已预置）</li>
 *   <li>recipient - 推送接收人用户 ID（默认 admin）</li>
 *   <li>hot-push-enabled-model - 关联的财税评分模型编码（默认 FIN_LEAD_SCORE_V1,
 *       实现按编码解析模型 ID, 避免在代码中硬编码数值主键）</li>
 * </ul>
 * <p>
 * 配置由 {@link FinanceAiConfig} 注册（沿用 D04/D05 的
 * {@code @EnableConfigurationProperties} 装配模式）, 不修改 application.yml。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@ConfigurationProperties(prefix = "scrm.finance-lead-push")
public class ScrmFinanceLeadPushProperties {

    /** 推送总开关（默认 true; false 时仅记录日志不发送） */
    private boolean enabled = true;

    /** 高意向推送通知模板编码（默认 lead-hot-push, 对齐 V13 预置模板） */
    private String templateCode = "lead-hot-push";

    /** 推送接收人用户 ID（默认 admin, 可按销售团队滚动配置） */
    private String recipient = "admin";

    /** 关联的财税评分模型编码（默认 FIN_LEAD_SCORE_V1, V13 已入库） */
    private String modelCode = "FIN_LEAD_SCORE_V1";
}