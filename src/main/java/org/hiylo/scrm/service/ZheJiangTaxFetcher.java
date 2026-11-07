/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ZheJiangTaxFetcher.java
 * Date : 2026/10/23 09:50:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.ScrmInspectionProperties;
import org.hiylo.scrm.dto.TaxNoticeFetcher;
import org.hiylo.scrm.dto.TaxNoticeParser;
import org.hiylo.scrm.dto.TaxNoticeRecord;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 浙江电子税务局税务公告抓取适配器。
 * <p>
 * 按 T03 实证实现:
 * <ul>
 *   <li><b>A2 静态列表</b> 「通知公告」{$code col22104/index.html} - 普通 HTML, 无验证码, 直接抓首页;</li>
 *   <li><b>A5 公布栏</b> 「重大税收违法失信主体信息」公布栏 - 同 A2 套路;</li>
 *   <li><b>B4/B5 查询应用</b> 欠税 / 非正常户 - 行政执法公示平台 wings 框架, 表单带 checkCode 验证码
 *       + 录入日期起止（可增量）, 与广东同类, 本期默认不过码（见 {@link TaxNoticeScrapeService}）。</li>
 * </ul>
 * 只抓公开栏目; 命中记录由上层按「授权客户企业名单」过滤后落库。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZheJiangTaxFetcher {

    /** 省份代码 */
    public static final String PROVINCE = "ZJ";

    /** 浙江税务通知公告列表页 */
    private static final String NOTICE_LIST_URL =
            "http://zhejiang.chinatax.gov.cn/col/col22104/index.html";

    /** 浙江重大税收违法失信主体信息公布栏 */
    private static final String MAJOR_VIOLATION_LIST_URL =
            "http://zhejiang.chinatax.gov.cn/col/col24411/index.html";

    /**
     * 浙江重大税收违法失信主体信息 dataproxy 数据端点（栏目页只是骨架,
     * 真实列表由此异步加载; columnid=24411 对应该栏目, 实测 2298 条 / 23 页）。
     */
    private static final String MAJOR_VIOLATION_DATA_URL =
            "http://zhejiang.chinatax.gov.cn/module/web/jpage/dataproxy.jsp"
                    + "?page=1&appid=1&webid=15&path=/&columnid=24411&unitid=61782&permissiontype=0";

    /** 浙江行政执法公示平台 - 欠税公告查询（wings, 验证码） */
    private static final String QSGG_QUERY_URL =
            "http://zhejiang.chinatax.gov.cn:16002/sword?ctrl=XzzfgsptXzzsCtrl_qsggcx";

    /** 浏览器 UA */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    /** 公告解析器 */
    private final TaxNoticeParser parser;

    /** 巡检调度配置（公告源开关: finance.inspection.notice.*） */
    private final ScrmInspectionProperties inspectionProperties;

    /** HTTP 客户端（默认连接/读取超时 5s/10s） */
    private final RestClient restClient = RestClient.builder()
            .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
            .build();

    /**
     * 抓取浙江税务「通知公告」列表（A2, 每日任务）。
     *
     * @return 抓取结果
     */
    public TaxNoticeFetcher fetchNoticeList() {
        if (!inspectionProperties.getNotice().isZhejiangEnabled()) {
            log.info("[浙税抓取-A2] 已按配置关闭浙江公告源, 跳过抓取");
            return new TaxNoticeFetcher(new ArrayList<>(), false, "SKIPPED: 浙江公告源已关闭");
        }
        try {
            String html = get(NOTICE_LIST_URL);
            List<TaxNoticeRecord> records = parser.parseStaticList(html, "通知公告", PROVINCE, null);
            log.info("[浙税抓取-A2] 通知公告列表抓取完成: count={}", records.size());
            return new TaxNoticeFetcher(records, false, null);
        } catch (Exception e) {
            log.warn("[浙税抓取-A2] 通知公告列表抓取失败: {}", e.getMessage());
            return new TaxNoticeFetcher(new ArrayList<>(), false, e.getMessage());
        }
    }

    /**
     * 抓取浙江「重大税收违法失信主体信息」公布栏（A5, 每日任务）。
     *
     * @return 抓取结果
     */
    public TaxNoticeFetcher fetchMajorViolationList() {
        if (!inspectionProperties.getNotice().isZhejiangEnabled()) {
            log.info("[浙税抓取-A5] 已按配置关闭浙江公告源, 跳过抓取");
            return new TaxNoticeFetcher(new ArrayList<>(), false, "SKIPPED: 浙江公告源已关闭");
        }
        try {
            // A5 栏目页只有页面骨架, 列表由 dataproxy 异步加载;
            // 直接请求数据端点才能拿到记录（实测 2298 条 / 23 页）。
            String xml = get(MAJOR_VIOLATION_DATA_URL);
            List<TaxNoticeRecord> records =
                    parser.parseStaticList(xml, "重大税收违法", PROVINCE, null);
            log.info("[浙税抓取-A5] 重大税收违法公布栏抓取完成: count={}", records.size());
            return new TaxNoticeFetcher(records, false, null);
        } catch (Exception e) {
            log.warn("[浙税抓取-A5] 重大税收违法公布栏抓取失败: {}", e.getMessage());
            return new TaxNoticeFetcher(new ArrayList<>(), false, e.getMessage());
        }
    }

    /**
     * 抓取浙江欠税公告查询明细（B4, 每日任务, 验证码闸门）。
     *
     * @param enterpriseName 企业名称
     * @param creditCode     统一社会信用代码
     * @return 抓取结果（默认含「需验证码」标记）
     */
    public TaxNoticeFetcher fetchQsggDetail(String enterpriseName, String creditCode) {
        if (!inspectionProperties.getNotice().isZhejiangEnabled()) {
            log.info("[浙税抓取-B4] 已按配置关闭浙江公告源, 跳过欠税明细探测");
            return new TaxNoticeFetcher(new ArrayList<>(), false, "SKIPPED: 浙江公告源已关闭");
        }
        try {
            String html = get(QSGG_QUERY_URL);
            log.info("[浙税抓取-B4] 欠税公告查询入口可达: name={}, creditCode={}, pageLen={}",
                    enterpriseName, safeCreditCode(creditCode), html == null ? 0 : html.length());
            return new TaxNoticeFetcher(new ArrayList<>(), true, "欠税公告查询依赖图形验证码, 待 OCR 联调");
        } catch (Exception e) {
            log.warn("[浙税抓取-B4] 欠税公告查询入口不可达: name={}, err={}",
                    enterpriseName, e.getMessage());
            return new TaxNoticeFetcher(new ArrayList<>(), false, e.getMessage());
        }
    }

    /**
     * 以浏览器 UA GET 指定 URL。
     *
     * @param url 目标 URL
     * @return 响应体文本
     */
    private String get(String url) {
        // 取 byte[] 而非 String: 部分站点（如浙江）响应头不带 charset,
        // RestClient 会按 ISO-8859-1 解码导致中文乱码, 统一交给 HttpTextUtils 判定编码。
        org.springframework.http.ResponseEntity<byte[]> response = restClient.get()
                .uri(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Accept", MediaType.TEXT_HTML_VALUE + "," + MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .toEntity(byte[].class);
        org.springframework.http.MediaType contentType = response.getHeaders().getContentType();
        String headerCharset = contentType == null || contentType.getCharset() == null
                ? null : contentType.getCharset().name();
        return org.hiylo.scrm.common.util.HttpTextUtils.decode(response.getBody(), headerCharset);
    }

    /**
     * 日志用信用代码脱敏。
     *
     * @param creditCode 统一社会信用代码
     * @return 脱敏值
     */
    private String safeCreditCode(String creditCode) {
        if (creditCode == null || creditCode.length() <= 8) {
            return "****";
        }
        return creditCode.substring(0, 4) + "********" + creditCode.substring(creditCode.length() - 4);
    }
}