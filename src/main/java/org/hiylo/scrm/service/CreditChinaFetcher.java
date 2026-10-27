/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CreditChinaFetcher.java
 * Date : 2026/10/23 10:50:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.ScrmInspectionProperties;
import org.hiylo.scrm.dto.TaxNoticeFetcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * 信用中国（creditchina.gov.cn）行政处罚 / 失信被执行人抓取适配器。
 * <p>
 * <b>默认不启用, 且不建议自建</b>: 按 T03 §0/C1 实测结论, 信用中国全站返回
 * HTTP 412 JS 反爬挑战（{@code $_ts} 动态 token + 动态解密 JS + {@code __jsluid_s}
 * cookie）, 纯 HTTP 客户端与 headless 浏览器均不稳定, 自建成本高且易触发 IP 风险。
 * 因此本类<b>不发起任何请求</b>, 只返回带明确原因的空结果, 由上层记为
 * 「跳过（走第三方 API 兜底）」——与立项方案「信用中国/失信被执行人并入 T02
 * 第三方 API」的分工一致。
 * </p>
 * <p>
 * 若后续确需自建, 打开 {@code finance.inspection.notice.credit-china-enabled=true}
 * （application.yml finance: 段, 别名 {@code scrm.inspection.credit-china.enabled}）
 * 并在此实现真实抓取（建议独立进程 + 限频 + 失败降级, 不阻塞税务公告主链路）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditChinaFetcher {

    /** 信用中国行政处罚栏目（仅作取证记录, 本类不请求） */
    private static final String ADMIN_PENALTY_URL =
            "https://www.creditchina.gov.cn/xinyongxingzhengchufa/index.html";

    /** 巡检调度配置（公告源开关: finance.inspection.notice.credit-china-enabled） */
    private final ScrmInspectionProperties inspectionProperties;

    /**
     * 是否启用信用中国抓取（默认 false, T03 已定不自建）。
     *
     * @return 配置的开关值
     */
    private boolean enabled() {
        return inspectionProperties.getNotice().isCreditChinaEnabled();
    }

    /**
     * 抓取信用中国行政处罚 / 失信公告。
     * <p>T03 结论: 全站 412 JS 反爬, 首期不自建 —— 返回空结果 + 原因说明,
     * 不发起请求（避免无意义的对抗与 IP 风险）。</p>
     *
     * @return 空结果 + 跳过原因
     */
    public TaxNoticeFetcher fetchAdministrativePenalty() {
        if (!enabled()) {
            log.info("[信用中国抓取] 按 T03 结论跳过自建: 站点 412 JS 反爬, 改由第三方 API 兜底, url={}",
                    ADMIN_PENALTY_URL);
            return new TaxNoticeFetcher(new ArrayList<>(), false,
                    "SKIPPED: 信用中国 412 反爬, 按 T03 走第三方 API 兜底");
        }
        // 启用后仍需实现真实抓取（当前不含自建实现, 显式声明未接入, 不伪造成功）
        log.warn("[信用中国抓取] 已配置启用但本实现未接入真实抓取逻辑, 返回空结果");
        return new TaxNoticeFetcher(new ArrayList<>(), false, "NOT_IMPLEMENTED: 信用中国自建抓取未接入");
    }
}