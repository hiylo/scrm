/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxCalendarProperties.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 报税日历域调度与预警配置。
 * <p>
 * 绑定 {@code scrm.tax-calendar.*}: 申报记录生成/状态推进/预警推送 Cron（默认
 * 01:00 / 08:00 / 09:00, 对齐 T08A §6 调度节奏）、预警节奏（7/3/1 天与逾期升级
 * 阈值天数, 默认 5）、推送接收人。配置由 {@link FinanceTaxConfig} 注册, 不修改
 * application.yml。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@ConfigurationProperties(prefix = "scrm.tax-calendar")
public class ScrmTaxCalendarProperties {

    /** 生成下期申报记录 Cron（默认每天 01:00） */
    private String generateCron = "0 0 1 * * ?";

    /** 状态推进（NOT_STARTED→PENDING / 逾期判定）Cron（默认每天 08:00） */
    private String advanceCron = "0 0 8 * * ?";

    /** 预警推送 Cron（默认每天 09:00） */
    private String warningCron = "0 0 9 * * ?";

    /** 预警节奏: 截止日前提醒天数（默认 7/3/1, 逗号分隔） */
    private String remindDaysBefore = "7,3,1";

    /** 逾期升级阈值天数（默认 5） */
    private int escalateOverdueDays = 5;

    /** 预警站内信接收人用户 ID（默认 admin） */
    private String notifyRecipient = "admin";

    /** 预警通知模板编码（V9 预置 tax-deadline-remind） */
    private String remindTemplateCode = "tax-deadline-remind";

    /** 日历事件是否落库（ScrmCalendarEventEntity, 默认 true; 关闭时仅聚合查询展示） */
    private boolean writeCalendarEvents = true;
}
