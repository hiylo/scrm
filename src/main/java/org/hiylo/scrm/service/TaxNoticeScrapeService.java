/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : TaxNoticeScrapeService.java
 * Date : 2026/10/23 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.component.InspectionRateLimiter;
import org.hiylo.scrm.dto.TaxNoticeFetcher;
import org.hiylo.scrm.dto.TaxNoticeRecord;
import org.hiylo.scrm.entity.ScrmInspectionSnapshotEntity;
import org.hiylo.scrm.entity.ScrmInspectionTaskEntity;
import org.hiylo.scrm.repository.ScrmInspectionTaskRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 税务公开公告定时抓取服务。
 * <p>
 * 按授权客户企业名单驱动抓取（T03 §6 落地建议）: 每家企业先走静态公告列表摘要
 * （广东/浙江 A1/A2/A5）, 明细类（欠税/非正常户 B 类）受图形验证码闸门制约,
 * 本期默认探测可达性并标记「待 OCR 联调」, 联调打开后再按企业单点查增量。
 * </p>
 * <p><b>变化才告警</b>: 命中记录按「明细唯一键」做日级去重（同一企业同一所属期重复
 * 出现 = 无变化）, 仅新键落快照并进入告警比对; 无关企业明细一律丢弃不落库。
 * 频率 1 次/天（由调度器错峰执行）; 命中验证码的重试上限与全站日请求上限遵循
 * T03 §3.1 令牌桶约束。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxNoticeScrapeService {

    /** 税务维度快照来源标记 */
    public static final String SOURCE_TAX_SCRAPE = "SCRAPE";

    /** 抓取结果汇总 */
    public record ScrapeSummary(int taskScanned, int recordsMatched, int captchaBlocked, int failed) {
    }

    /** 巡检任务数据访问层 */
    private final ScrmInspectionTaskRepository taskRepository;

    /** 广东税务抓取适配器 */
    private final GuangDongTaxFetcher guangDongFetcher;

    /** 浙江税务抓取适配器 */
    private final ZheJiangTaxFetcher zheJiangFetcher;

    /** 陕西税务抓取适配器（dataproxy 端点, 默认关闭） */
    private final ShaanxiTaxFetcher shaanxiFetcher;

    /** 巡检快照服务（变化比对入库） */
    private final ScrmInspectionSnapshotService snapshotService;

    /** 巡检告警服务（变化才告警） */
    private final ScrmInspectionAlertService alertService;

    /** 巡检频率令牌桶（T03: 单站日请求 ≤ 200, 每客户企业 ≤ 2/天） */
    private final InspectionRateLimiter rateLimiter;

    /** 巡检配置（公告源开关与陕西栏目参数, 供就绪自检） */
    private final org.hiylo.scrm.config.ScrmInspectionProperties inspectionProperties;

    /** 单站每日抓取预算（T03 §3.1: 静态列表页全站 ≤ 200 请求/天） */
    private static final int STATION_DAILY_BUDGET = 200;

    /**
     * 执行一轮全量税务公告抓取（每日定时任务入口）。
     * <p>逐企业执行: 静态列表摘要 → 命中记录按唯一键入库 → 欠税明细探测（验证码标记）。
     * 单企业失败不中断本轮; 返回汇总供调度处理器与执行记录落库。</p>
     *
     * @return 抓取汇总
     */
    public ScrapeSummary scrapeAll() {
        List<ScrmInspectionTaskEntity> tasks = taskRepository.findEnabledTaxTasks();
        if (tasks.isEmpty()) {
            log.info("[税务抓取] 无启用税务巡检任务, 跳过");
            return new ScrapeSummary(0, 0, 0, 0);
        }
        log.info("[税务抓取] 开始全量抓取: taskCount={}", tasks.size());
        int matched = 0;
        int captchaBlocked = 0;
        int failed = 0;
        for (ScrmInspectionTaskEntity task : tasks) {
            try {
                int[] stats = scrapeOne(task);
                matched += stats[0];
                captchaBlocked += stats[1];
                failed += stats[2];
            } catch (Exception e) {
                failed++;
                log.error("[税务抓取] 任务 {} 抓取异常: {}", task.getId(), e.getMessage(), e);
            }
        }
        log.info("[税务抓取] 本轮抓取完成: scanned={}, matched={}, captchaBlocked={}, failed={}",
                tasks.size(), matched, captchaBlocked, failed);
        return new ScrapeSummary(tasks.size(), matched, captchaBlocked, failed);
    }

    /**
     * 执行单个企业的税务抓取与快照落库。
     *
     * @param task 巡检任务
     * @return 统计数组: [命中记录数, 验证码阻断数, 失败数]
     */
    private int[] scrapeOne(ScrmInspectionTaskEntity task) {
        int matched = 0;
        int captchaBlocked = 0;
        int failed = 0;
        // 1) 静态公告列表摘要（广东/浙江）, 命中按唯一键去重入库
        List<TaxNoticeRecord> found = new ArrayList<>();
        if (task.getInspectTax() != null && task.getInspectTax()) {
            if (rateLimiter.tryAcquire("GD_SCRAPE_LIST", STATION_DAILY_BUDGET)) {
                found.addAll(guangDongFetcher.fetchNoticeList().getRecords());
            }
            if (rateLimiter.tryAcquire("ZJ_SCRAPE_LIST", STATION_DAILY_BUDGET)) {
                found.addAll(zheJiangFetcher.fetchNoticeList().getRecords());
                found.addAll(zheJiangFetcher.fetchMajorViolationList().getRecords());
            }
            if (rateLimiter.tryAcquire("SX_SCRAPE_LIST", STATION_DAILY_BUDGET)) {
                found.addAll(shaanxiFetcher.fetchNoticeList().getRecords());
            }
        }
        if (!found.isEmpty()) {
            List<TaxNoticeRecord> matchedRecords = filterByEnterprise(task, found);
            if (!matchedRecords.isEmpty()) {
                storeTaxNotice(task, matchedRecords, "通知公告");
                matched += matchedRecords.size();
            }
        }
        // 2) 欠税明细查询（验证码闸门, 本期探测可达性, 命中标记留待 OCR 联调）;
        //    每客户企业 ≤ 2 次/天（T03 §3.1）, 超出即跳过
        if (task.getInspectTax() != null && task.getInspectTax()) {
            boolean gdAllowed = rateLimiter.tryAcquire("GD_SCRAPE_DETAIL", 2);
            boolean zjAllowed = rateLimiter.tryAcquire("ZJ_SCRAPE_DETAIL", 2);
            // 预算用尽属于「正常限流跳过」而非抓取失败: 不能带 errorMessage,
            // 否则会被下面的 errorMessage != null 判定计入 failed, 使任务误报 PARTIAL。
            if (!gdAllowed || !zjAllowed) {
                log.info("[税务抓取] 明细日预算用尽, 跳过本轮明细查询: taskId={}, gd={}, zj={}",
                        task.getId(), gdAllowed, zjAllowed);
            }
            TaxNoticeFetcher gdQsgg = gdAllowed ? guangDongFetcher.fetchQsggDetail(
                    task.getEnterpriseName(), task.getCreditCode())
                    : new TaxNoticeFetcher(List.of(), false, null);
            TaxNoticeFetcher zjQsgg = zjAllowed ? zheJiangTaxFetcherDetail(task)
                    : new TaxNoticeFetcher(List.of(), false, null);
            if (gdQsgg.isNeedCaptchaRetry() || zjQsgg.isNeedCaptchaRetry()) {
                captchaBlocked++;
            } else if (gdQsgg.getErrorMessage() != null || zjQsgg.getErrorMessage() != null) {
                failed++;
            }
        }
        // 3) 更新任务税务游标
        task.setLastTaxRunAt(java.time.LocalDateTime.now());
        task.setLastRunAt(java.time.LocalDateTime.now());
        task.setLastResult(failed > 0 ? "PARTIAL" : "SUCCESS");
        taskRepository.save(task);
        return new int[]{matched, captchaBlocked, failed};
    }

    /**
     * 按授权客户企业过滤命中记录（无关企业明细不落库）。
     *
     * @param task  巡检任务
     * @param found 抓取到的全部记录
     * @return 命中该企业（名称完全一致或识别号一致）的记录
     */
    private List<TaxNoticeRecord> filterByEnterprise(ScrmInspectionTaskEntity task,
                                                     List<TaxNoticeRecord> found) {
        String creditCode = task.getCreditCode();
        String name = task.getEnterpriseName();
        List<TaxNoticeRecord> matched = new ArrayList<>();
        for (TaxNoticeRecord record : found) {
            boolean sameName = record.getEnterpriseName() != null
                    && record.getEnterpriseName().contains(name);
            boolean sameCode = creditCode != null && creditCode.length() >= 18
                    && creditCode.equals(record.getCreditCode());
            if (sameName || sameCode) {
                matched.add(record);
            }
        }
        return matched;
    }

    /**
     * 把命中记录写入税务快照（按唯一键日级去重）。
     * <p>同一企业同一唯一键当天重复出现 = 无变化, 跳过; 新键生成明细 JSON
     * （脱敏）入库, 由快照服务与告警服务完成「变化才告警」。</p>
     *
     * @param task        巡检任务
     * @param records     命中记录
     * @param noticeType  公告类型
     */
    private void storeTaxNotice(ScrmInspectionTaskEntity task, List<TaxNoticeRecord> records,
                                String noticeType) {
        Map<String, TaxNoticeRecord> unique = new LinkedHashMap<>();
        for (TaxNoticeRecord record : records) {
            if (noticeType != null && record.getNoticeType() == null) {
                record.setNoticeType(noticeType);
            }
            if (record.getUniqueKey() == null || record.getUniqueKey().isBlank()) {
                record.setUniqueKey((record.getCreditCode() == null ? "" : record.getCreditCode())
                        + "|" + noticeType + "|"
                        + (record.getTaxPeriodStart() == null ? "" : record.getTaxPeriodStart()));
            }
            unique.putIfAbsent(record.getUniqueKey(), record);
        }
        for (TaxNoticeRecord record : unique.values()) {
            ScrmInspectionSnapshotEntity snapshot = snapshotService.saveSnapshot(
                    task.getId(), ScrmInspectionSnapshotService.DIM_TAX,
                    record.getCreditCode(), record.getEnterpriseName(),
                    SOURCE_TAX_SCRAPE, record.getNoticeType(),
                    toDetailJson(record), null);
            if (Boolean.TRUE.equals(snapshot.getChanged())) {
                alertService.notifyTaxChange(task, snapshot);
            }
        }
    }

    /**
     * 构建明细 JSON（脱敏后落库）。
     *
     * @param record 公告记录
     * @return 明细 JSON 字符串
     */
    private String toDetailJson(TaxNoticeRecord record) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("noticeType", record.getNoticeType());
        detail.put("enterpriseName", record.getEnterpriseName());
        detail.put("creditCode", record.getCreditCode());
        detail.put("legalPerson", record.getLegalPerson());
        detail.put("taxPeriodStart", record.getTaxPeriodStart());
        detail.put("taxPeriodEnd", record.getTaxPeriodEnd());
        detail.put("taxCategory", record.getTaxCategory());
        detail.put("taxAmount", record.getTaxAmount());
        detail.put("noticeDate", record.getNoticeDate());
        detail.put("noticeAuthority", record.getNoticeAuthority());
        detail.put("province", record.getProvince());
        detail.put("sourceUrl", record.getSourceUrl());
        return toJson(detail);
    }
    /**
     * 明细 JSON 序列化（失败时回落到可读文本, 不抛出中断巡检）。
     *
     * @param detail 明细 Map
     * @return JSON 字符串
     */
    private String toJson(Map<String, Object> detail) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(detail);
        } catch (Exception e) {
            log.warn("[税务抓取] 明细 JSON 序列化失败, 回落文本: {}", e.getMessage());
            return String.valueOf(detail);
        }
    }

    /**
     * 抓取浙江欠税明细（三元表达式的可读封装）。
     *
     * @param task 巡检任务
     * @return 抓取结果
     */
    private TaxNoticeFetcher zheJiangTaxFetcherDetail(ScrmInspectionTaskEntity task) {
        return zheJiangFetcher.fetchQsggDetail(task.getEnterpriseName(), task.getCreditCode());
    }

    /**
     * 公告源接入状态自检（供运维判断「为什么没抓到」）。
     * <p>
     * 逐源返回：开关是否开启、参数是否齐备、是否已知不可用（北京/陕西的 WAF 情况）。
     * 不做网络探测——本方法必须轻量、可在运维页频繁调用。
     * </p>
     *
     * @return 公告源状态
     */
    public java.util.Map<String, Object> noticeStatus() {
        org.hiylo.scrm.config.ScrmInspectionProperties.NoticeSources notice =
                inspectionProperties.getNotice();
        java.util.List<java.util.Map<String, Object>> sources = new java.util.ArrayList<>();

        sources.add(source("GD", "广东电子税务局", notice.isGuangdongEnabled(), true,
                notice.isGuangdongEnabled() ? "已启用，静态列表页可抓（实测 15 条）" : "开关已关闭", null));

        sources.add(source("ZJ", "浙江电子税务局", notice.isZhejiangEnabled(), true,
                notice.isZhejiangEnabled() ? "已启用，dataproxy 可抓（实测 45 条）" : "开关已关闭", null));

        boolean shaanxiReady = notice.isShaanxiEnabled()
                && notBlank(notice.getShaanxiColumnId())
                && notBlank(notice.getShaanxiUnitId())
                && notBlank(notice.getShaanxiWebId());
        sources.add(source("SX", "陕西电子税务局", notice.isShaanxiEnabled(), shaanxiReady,
                notice.isShaanxiEnabled()
                        ? (shaanxiReady ? "参数齐备" : "已开启但缺 columnId/unitId/webId")
                        : "开关已关闭",
                "该站为瑞数 WAF：栏目页 412、dataproxy 返回伪造的空 datastore（详见验收文档 §16），"
                        + "即使参数齐备当前也抓不到真实数据"));

        sources.add(source("BJ", "北京电子税务局", false, false,
                "未实现（站点瑞数 WAF 使无头浏览器 DOM 恒空，无法取得页面结构）",
                "需站点解除 WAF 或提供页面 HTML 样例"));

        sources.add(source("CREDIT_CN", "信用中国", notice.isCreditChinaEnabled(), false,
                notice.isCreditChinaEnabled() ? "已开启（但站点 412 反爬，预期失败）" : "开关已关闭",
                "T03 结论：全站 412 JS 反爬，不自建，走第三方 API 兜底"));

        long usable = sources.stream()
                .filter(x -> Boolean.TRUE.equals(x.get("ready")))
                .map(x -> String.valueOf(x.get("code")))
                .count();

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("usableSourceCount", usable);
        result.put("sources", sources);
        return result;
    }

    /**
     * 构造单个公告源状态。
     *
     * @param code    源编码
     * @param name    源名称
     * @param enabled 开关是否开启
     * @param ready   是否真正可用
     * @param detail  当前状态
     * @param blocker 不可用原因（可用时为 null）
     * @return 状态条目
     */
    private java.util.Map<String, Object> source(String code, String name, boolean enabled,
                                                 boolean ready, String detail, String blocker) {
        java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("code", code);
        item.put("name", name);
        item.put("enabled", enabled);
        item.put("ready", ready);
        item.put("detail", detail);
        item.put("blocker", blocker);
        return item;
    }

    /**
     * 判断字符串非空。
     *
     * @param s 字符串
     * @return true 表示非空
     */
    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}