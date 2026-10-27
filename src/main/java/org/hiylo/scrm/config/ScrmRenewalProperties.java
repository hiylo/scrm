/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmRenewalProperties.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务续费流失域调度与规则配置。
 * <p>
 * 绑定 {@code scrm.renewal.*}: 到期扫描 Cron（默认每天 08:00）、提醒窗口（30/7/3 天,
 * 逗号分隔）、流失评分阈值（HIGH≥75 / MEDIUM≥50, 与 ChurnWarning 口径一致）、
 * 到期未续转流失天数（默认 15）、推送接收人。配置由 {@link FinanceTaxConfig} 注册。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@ConfigurationProperties(prefix = "scrm.renewal")
public class ScrmRenewalProperties {

    /** 到期扫描 Cron（默认每天 08:00） */
    private String scanCron = "0 0 8 * * ?";

    /** 续费提醒推送 Cron（默认每天 09:00, 30/7/3 三级提醒由 remind-days-before 驱动） */
    private String remindCron = "0 0 9 * * ?";

    /** 提醒节奏: 到期前提醒天数（默认 30/7/3, 逗号分隔） */
    private String remindDaysBefore = "30,7,3";

    /** 流失风险阈值: HIGH（默认 75） */
    private double highRiskThreshold = 75.0;

    /** 流失风险阈值: MEDIUM（默认 50） */
    private double mediumRiskThreshold = 50.0;

    /** 到期未续转流失天数（默认 15） */
    private int lostAfterDays = 15;

    /** 续费任务提醒模板编码（V9 预置 renewal-task-remind） */
    private String remindTemplateCode = "renewal-task-remind";

    /** 续费任务推送接收人（默认 admin, 未指定时用合同销售） */
    private String notifyRecipient = "admin";
}
