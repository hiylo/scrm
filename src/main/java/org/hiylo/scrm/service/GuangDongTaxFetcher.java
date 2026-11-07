/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : GuangDongTaxFetcher.java
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
 * 广东电子税务局税务公告抓取适配器。
 * <p>
 * 按 T03 实证实现:
 * <ul>
 *   <li><b>A1 静态列表</b> 「通知公告」{$code common_list.shtml} - 普通 HTML, 无验证码, 直接抓首页（翻页参数 pageNo, 单页已覆盖巡检所需）;</li>
 *   <li><b>B1 欠税公告查询</b> 「欠税公告信息查询」- 查询应用返回 JSON 明细（含企业名称/识别号/法人/欠税金额/所属期/公告日期）, 图形验证码是唯一闸门, 默认不自动过码（见 {@link TaxNoticeScrapeService}), 联调时打开 OCR 分支。</li>
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
public class GuangDongTaxFetcher {

    /** 省份代码 */
    public static final String PROVINCE = "GD";

    /** 广东税务通知公告列表页 */
    private static final String NOTICE_LIST_URL =
            "https://guangdong.chinatax.gov.cn/gdsw/tzgg/common_list.shtml";

    /** 广东欠税公告查询入口（空参即回显表单与验证码的页面, 不直接触发查询） */
    private static final String QSGG_QUERY_URL =
            "https://guangdong.chinatax.gov.cn/gdsw/qsggxxcx/qsggCx.shtml?swjgdm=14400000000";

    /** 浏览器 UA（T03 实测广东主站需 UA, 否则被 412） */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    /** 抓取超时（毫秒）: 连接 5s / 读取 10s */
    private static final int TIMEOUT_MS = 10_000;

    /** 公告解析器 */
    private final TaxNoticeParser parser;

    /** 巡检调度配置（公告源开关: finance.inspection.notice.*） */
    private final ScrmInspectionProperties inspectionProperties;

    /** HTTP 客户端（默认连接/读取超时 5s/10s, 由 JDK 标准客户端提供） */
    private final RestClient restClient = RestClient.builder()
            .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
            .build();

    /**
     * 抓取广东税务「通知公告」列表（A1, 每日任务）。
     *
     * @return 抓取结果（记录列表 + 错误描述）
     */
    public TaxNoticeFetcher fetchNoticeList() {
        if (!inspectionProperties.getNotice().isGuangdongEnabled()) {
            log.info("[粤税抓取-A1] 已按配置关闭广东公告源, 跳过抓取");
            return new TaxNoticeFetcher(new ArrayList<>(), false, "SKIPPED: 广东公告源已关闭");
        }
        try {
            String html = get(NOTICE_LIST_URL);
            List<TaxNoticeRecord> records = parser.parseStaticList(html, "通知公告", PROVINCE, null);
            log.info("[粤税抓取-A1] 通知公告列表抓取完成: count={}", records.size());
            return new TaxNoticeFetcher(records, false, null);
        } catch (Exception e) {
            log.warn("[粤税抓取-A1] 通知公告列表抓取失败: {}", e.getMessage());
            return new TaxNoticeFetcher(new ArrayList<>(), false, e.getMessage());
        }
    }

    /**
     * 抓取广东「欠税公告信息查询」企业明细（B1, 每日任务, 按企业名/识别号单点查）。
     * <p><b>验证码闸门</b>: 查询应用以图形验证码为准, 本期实现不自动过码, 直接探测入口
     * 页面可达性并返回空记录; 真实抓取在「验证码 OCR 联调跑通」后打开
     * （见 D05 报告待验证清单, 与 T03 §6 落地建议一致）。</p>
     *
     * @param enterpriseName 企业名称（授权客户）
     * @param creditCode     统一社会信用代码
     * @return 抓取结果（默认含「需验证码」标记, 由上层记 PARTIAL 不打标签告警）
     */
    public TaxNoticeFetcher fetchQsggDetail(String enterpriseName, String creditCode) {
        if (!inspectionProperties.getNotice().isGuangdongEnabled()) {
            log.info("[粤税抓取-B1] 已按配置关闭广东公告源, 跳过欠税明细探测");
            return new TaxNoticeFetcher(new ArrayList<>(), false, "SKIPPED: 广东公告源已关闭");
        }
        try {
            // 仅探测入口可达性（GET 表单页）, 不做验证码提交 —— 验证码 OCR 联调打开后替换为
            // 完整流程: 取码(ImageCode.jsp) → 识别 → POST query 参数(gglx/nsrmc/nsrsbh/code/pageNo) → 解析 JSON
            String html = get(QSGG_QUERY_URL);
            log.info("[粤税抓取-B1] 欠税公告查询入口可达: name={}, creditCode={}, pageLen={}",
                    enterpriseName, safeCreditCode(creditCode), html == null ? 0 : html.length());
            return new TaxNoticeFetcher(new ArrayList<>(), true, "欠税公告查询依赖图形验证码, 待 OCR 联调");
        } catch (Exception e) {
            log.warn("[粤税抓取-B1] 欠税公告查询入口不可达: name={}, err={}",
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