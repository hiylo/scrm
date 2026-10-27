/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmPolicyPushProperties.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 政策匹配推送域调度与规则配置。
 * <p>
 * 绑定 {@code scrm.policy-push.*}: 每日快照补算 Cron（默认 03:00）、抽样核验条数
 * （默认 10 条逐条人工核验）、单客户 30 天推送配额（默认 2 条）、内部提醒接收人
 * （默认 admin）。客户直发走企微官方通道, 凭据到位前仅 APP 预览 + 销售人工转发
 * （默认开启内部提醒、客户触达为二期开关）。配置由 {@link FinanceTaxConfig} 注册。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@ConfigurationProperties(prefix = "scrm.policy-push")
public class ScrmPolicyPushProperties {

    /** 每日快照补算 Cron（默认每天 03:00） */
    private String refreshCron = "0 0 3 * * ?";

    /** 抽样核验条数（默认 10 条逐条人工核验客户名单 + 口径） */
    private int samplingAuditCount = 10;

    /** 单客户 30 天内至多接收的政策推送条数（配额, 默认 2） */
    private int quotaPer30Days = 2;

    /** 内部提醒模板编码（V9 预置 tax-deadline-remind 同模板家族, 政策用独立 category） */
    private String remindTemplateCode = "policy-push-remind";

    /** 内部提醒接收人（默认 admin, 实际按命中客户对应销售/会计推送） */
    private String notifyRecipient = "admin";
}
