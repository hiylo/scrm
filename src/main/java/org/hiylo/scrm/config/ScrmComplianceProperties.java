/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmComplianceProperties.java
 * Date : 2026/10/25 10:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 合规域（工商年报 / 资质许可）配置。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "scrm.compliance")
public class ScrmComplianceProperties {

    /** 年报生成调度（默认每年 1 月 1 日 01:30 为在服企业生成上年度年报任务） */
    private String generateCron = "0 30 1 1 1 ?";

    /** 年报逾期推进 + 资质到期状态推进（默认每日 01:00） */
    private String advanceCron = "0 0 1 * * ?";

    /** 年报与资质到期提醒（默认每日 09:30） */
    private String remindCron = "0 30 9 * * ?";

    /** 年报提醒模板编码 */
    private String annualRemindTemplateCode = "annual-report-remind";

    /** 资质到期提醒模板编码 */
    private String qualificationRemindTemplateCode = "qualification-expire-remind";

    /**
     * 默认收件人（主体未指派负责会计时回落）。
     * <p>与报税/续费提醒保持同一约定：优先发负责会计，未指派才用此值。</p>
     */
    private String notifyRecipient = "admin";

    /** 年报提前提醒天数（距截止日还有几天开始提醒） */
    private Integer annualRemindDaysBefore = 30;

    /** 单轮提醒最多推送条数（防止一次刷爆通知表） */
    private Integer maxRemindPerRound = 200;
}
