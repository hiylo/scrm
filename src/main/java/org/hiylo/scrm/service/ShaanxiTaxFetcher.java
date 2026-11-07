/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ShaanxiTaxFetcher.java
 * Date : 2026/10/24 13:00:00
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
 * 陕西电子税务局税务公告抓取适配器。
 * <p>
 * <b>与广东/浙江的关键差异（实测 2026-10-24）</b>：
 * </p>
 * <ul>
 *   <li>陕西站栏目页（{@code /col/colXXXXX/index.html}）<b>全站返回 412</b>，
 *       响应体是瑞数（RiverSecurity）动态防护的 JS 挑战页
 *       （特征：{@code meta r="m"}、{@code $_ts}、{@code $_ts.nsd}）；
 *       连无头浏览器访问都会被直接断开（{@code ERR_EMPTY_RESPONSE}）。</li>
 *   <li>数据端点 {@code /module/web/jpage/dataproxy.jsp} 虽然返回 HTTP 200，
 *       但响应是 <b>WAF 伪造的空 {@code <datastore></datastore>}</b>，而不是真实 CMS 输出。
 *       判定依据（与浙江同构站点对照）：
 *       <ol>
 *         <li>14 个不同 columnId 全部返回<b>完全相同</b>的 24 字节响应；</li>
 *         <li>连 {@code page=abc}、{@code page=-1}、{@code permissiontype=99999}
 *             这类畸形参数也返回同一份响应（真实 CMS 会返回不同的空/错误形态）；</li>
 *         <li>响应头带瑞数特征 cookie（随机名 + 10 年有效期 {@code expires}），
 *             而浙江只有 {@code JSESSIONID}。</li>
 *       </ol>
 *   </li>
 * </ul>
 * <p>
 * <b>结论</b>：陕西当前<b>无法通过纯 HTTP 客户端抓取</b>——不是栏目参数没找对，
 * 而是数据端点本身被 WAF 接管。适配器保留完整实现，待站点解除 WAF 或提供授权数据源后
 * 只需填配置即可工作。
 * </p>
 * <p>
 * 因此本适配器<b>不走页面解析</b>，直接请求 dataproxy 数据端点；返回的
 * {@code <datastore><recordset><record><![CDATA[<li>..</li>]]></record>} 结构与浙江
 * 同属 TRS/hanweb CMS 家族，可复用 {@link TaxNoticeParser#parseStaticList}。
 * </p>
 * <p>
 * <b>配置与开关</b>：{@code webid / columnid / unitid} 走配置
 * （{@code scrm.inspection.notice.shaanxi-*}），默认关闭。缺参数时显式报错，
 * 而不是静默返回空——否则运维只会看到「抓取成功但 0 条」，无法区分
 * 「配置错」与「真的没数据」。若端点返回空 datastore，同样给出明确提示。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShaanxiTaxFetcher {

    /** 省份代码 */
    public static final String PROVINCE = "SX";

    /** dataproxy 数据端点（WAF 未拦截, 实测 HTTP 200） */
    private static final String DATAPROXY_URL =
            "https://shaanxi.chinatax.gov.cn/module/web/jpage/dataproxy.jsp";

    /** 浏览器 UA */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/131.0.0.0 Safari/537.36";

    /** 公告解析器 */
    private final TaxNoticeParser parser;

    /** 巡检调度配置（公告源开关 + 陕西栏目参数） */
    private final ScrmInspectionProperties inspectionProperties;

    /** HTTP 客户端 */
    private final RestClient restClient = RestClient.builder()
            .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
            .build();

    /**
     * 抓取陕西税务「通知公告」列表（经 dataproxy 数据端点）。
     *
     * @return 抓取结果（记录列表 + 错误描述）
     */
    public TaxNoticeFetcher fetchNoticeList() {
        ScrmInspectionProperties.NoticeSources notice = inspectionProperties.getNotice();
        if (!notice.isShaanxiEnabled()) {
            log.info("[陕税抓取] 已按配置关闭陕西公告源, 跳过");
            return new TaxNoticeFetcher(new ArrayList<>(), false, "SKIPPED: 陕西公告源已关闭");
        }
        if (isBlank(notice.getShaanxiColumnId()) || isBlank(notice.getShaanxiUnitId())
                || isBlank(notice.getShaanxiWebId())) {
            // 缺参数时 dataproxy 会返回空 datastore（静默无数据），这里显式报错更利于定位
            log.warn("[陕税抓取] 缺少栏目参数, 无法查询: columnId={}, unitId={}, webId={}",
                    notice.getShaanxiColumnId(), notice.getShaanxiUnitId(), notice.getShaanxiWebId());
            return new TaxNoticeFetcher(new ArrayList<>(), false,
                    "陕西公告源缺少 columnId/unitId/webId 配置");
        }
        try {
            String url = DATAPROXY_URL + "?page=1&appid=1"
                    + "&webid=" + notice.getShaanxiWebId()
                    + "&path=/"
                    + "&columnid=" + notice.getShaanxiColumnId()
                    + "&unitid=" + notice.getShaanxiUnitId()
                    + "&permissiontype=0";
            String xml = restClient.get()
                    .uri(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Accept", MediaType.APPLICATION_XML_VALUE + "," + MediaType.TEXT_HTML_VALUE)
                    .retrieve()
                    .body(String.class);
            // dataproxy 返回 <datastore><record><![CDATA[<li>..</li>]]></record></datastore>,
            // 与浙江同构, 直接复用静态列表解析器
            List<TaxNoticeRecord> records = parser.parseStaticList(xml, "通知公告", PROVINCE, null);
            // dataproxy 在栏目参数不正确时会返回空的 <datastore></datastore>（HTTP 200）。
            // 若不识别这种情况, 上层只会看到「抓取成功但 0 条」, 无法定位是配置问题还是真的没数据。
            if (records.isEmpty() && isEmptyDatastore(xml)) {
                log.warn("[陕税抓取] dataproxy 返回空 datastore, 栏目参数可能不正确: "
                        + "columnId={}, unitId={}, webId={}",
                        notice.getShaanxiColumnId(), notice.getShaanxiUnitId(), notice.getShaanxiWebId());
                return new TaxNoticeFetcher(new ArrayList<>(), false,
                        "陕西 dataproxy 返回空 datastore（栏目参数 webId/columnId/unitId 可能不正确）");
            }
            log.info("[陕税抓取] 通知公告列表抓取完成: count={}", records.size());
            return new TaxNoticeFetcher(records, false, null);
        } catch (Exception e) {
            log.warn("[陕税抓取] 通知公告列表抓取失败: {}", e.getMessage());
            return new TaxNoticeFetcher(new ArrayList<>(), false, e.getMessage());
        }
    }

    /**
     * 判断响应是否为「空 datastore」（参数正确但栏目无数据 / 参数不匹配）。
     *
     * @param xml dataproxy 响应
     * @return true 表示为空 datastore
     */
    private boolean isEmptyDatastore(String xml) {
        if (xml == null) {
            return true;
        }
        String compact = xml.replaceAll("\\s+", "");
        return compact.contains("<datastore></datastore>")
                || compact.contains("<datastore/>")
                || compact.isEmpty();
    }

    /**
     * 判断字符串是否为空。
     *
     * @param s 字符串
     * @return true 表示为空
     */
    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
