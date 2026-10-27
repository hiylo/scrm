/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInspectionProperties.java
 * Date : 2026/10/23 09:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 工商税务巡检调度与告警配置。
 * <p>
 * 绑定 {@code scrm.inspection.*}: 工商 API 巡检与税务公告抓取的 Cron 表达式（默认凌晨错峰）、
 * 告警接收人（站内信, 复用 NotificationCenter）。频率红线: 1 次/天级, 错峰凌晨执行。
 * </p>
 * <ul>
 *   <li>business-cron - 工商 API 巡检 Cron（默认每天 02:30, 与 T03 错峰建议一致）</li>
 *   <li>tax-cron - 税务公告抓取 Cron（默认每天 03:00）</li>
 *   <li>notify-recipient - 告警站内信接收人用户 ID（默认 admin）</li>
 *   <li>notify-rule-prefix - 告警信号 ruleId 前缀（默认 inspection-）</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@ConfigurationProperties(prefix = "scrm.inspection")
public class ScrmInspectionProperties {

    /** 工商 API 巡检 Cron（默认每天 02:30, 错峰执行） */
    private String businessCron = "0 30 2 * * ?";

    /** 税务公告抓取 Cron（默认每天 03:00） */
    private String taxCron = "0 0 3 * * ?";

    /** 告警站内信接收人用户 ID（默认 admin, 复用 NotificationCenter） */
    private String notifyRecipient = "admin";

    /** 告警信号 ruleId 前缀 */
    private String notifyRulePrefix = "inspection-";

    /** 公告源开关（T03 结论: 广东/浙江静态列表默认开; 信用中国 412 反爬默认关, 走第三方 API 兜底） */
    private final NoticeSources notice = new NoticeSources();

    /**
     * 税务公开公告抓取数据源开关。
     * <p>
     * 绑定 {@code scrm.inspection.notice.*}（application.yml finance: 段别名,
     * 由集成步骤统一追加）。开关只控制「是否请求该站点」, 不涉及凭据。
     * </p>
     */
    @Data
    public static class NoticeSources {

        /** 广东电子税务局通知公告/欠税查询（A1/B1, 默认启用） */
        private boolean guangdongEnabled = true;

        /** 浙江电子税务局通知公告/重大违法/欠税查询（A2/A5/B4, 默认启用） */
        private boolean zhejiangEnabled = true;

        /** 信用中国自建抓取（默认 false, T03 C1: 全站 412 JS 反爬, 不自建） */
        private boolean creditChinaEnabled = false;

        /**
         * 陕西电子税务局通知公告（默认 false）。
         * <p>
         * 实测（2026-10-24）：栏目页全站返回 412 + 瑞数（RiverSecurity）JS 挑战，
         * 连无头浏览器都会被直接断开连接；但 {@code /module/web/jpage/dataproxy.jsp}
         * 数据端点可访问（HTTP 200）。因此适配器走 dataproxy，需补齐下面三个栏目参数
         * （只能从被 WAF 拦截的栏目页 HTML 里取得）后才可启用。
         * </p>
         */
        private boolean shaanxiEnabled = false;

        /** 陕西公告栏目 columnId（dataproxy 必需, 取自栏目页链接） */
        private String shaanxiColumnId = "";

        /** 陕西公告栏目 unitId（dataproxy 必需） */
        private String shaanxiUnitId = "";

        /** 陕西站点 webId（dataproxy 必需） */
        private String shaanxiWebId = "";
    }
}