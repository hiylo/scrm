/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : TaxNoticeParserTest.java
 * Date : 2026/10/23 11:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TaxNoticeParser} 单元测试。
 * <p>
 * 覆盖 T03 实证的两类被抓页面: 静态公告列表（标题 + 日期 + 链接）与
 * 查询应用 JSON（企业名称 / 识别号 / 法人 / 欠税金额 / 所属期 / 公告日期）,
 * 以及 HTML 表格行的启发式字段提取。解析器不依赖外部网络, 纯纯文本到模型转换。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("TaxNoticeParser 公告解析")
@ExtendWith(MockitoExtension.class)
class TaxNoticeParserTest {

    /** 静态列表 HTML 样例（仿广东/浙江主站通知公告列表） */
    private static final String STATIC_LIST_HTML =
            "<ul class=\"list\">"
                    + "<li><a href=\"/gdsw/tzgg/2026-10/22/content_1.shtml\">关于XX企业欠税公告(2026-10)</a>"
                    + "<span>2026-10-22</span></li>"
                    + "<li><a href=\"/gdsw/tzgg/2026-10/21/content_2.shtml\">关于YY企业非正常户认定公告</a>"
                    + "<span>2026-10-21</span></li>"
                    + "</ul>";

    /** 查询应用 JSON 样例（仿广东欠税公告查询 SwrdQsggQueryList 返回结构） */
    private static final String QUERY_JSON =
            "{\"code\":\"200\",\"data\":["
                    + "{\"nsrmc\":\"示例企业有限公司\",\"nsrsbh\":\"91330100MA27X00001\","
                    + "\"fddbrxm\":\"张三\",\"qsje\":\"12345.67\",\"ssqq\":\"2026-07\",\"ssqz\":\"2026-09\","
                    + "\"ggrq\":\"2026-10-22\",\"ggjg\":\"国家税务总局杭州市税务局\"}"
                    + "]}";

    /**
     * 静态列表解析: 命中标题、日期与链接。
     */
    @Test
    @DisplayName("静态 HTML 列表: 解析标题/日期/链接")
    void parseStaticList() {
        TaxNoticeParser parser = new TaxNoticeParser(new com.fasterxml.jackson.databind.ObjectMapper());
        List<TaxNoticeRecord> records = parser.parseStaticList(STATIC_LIST_HTML, "通知公告", "GD", null);
        assertThat(records).hasSize(2);
        TaxNoticeRecord first = records.get(0);
        assertThat(first.getProvince()).isEqualTo("GD");
        assertThat(first.getNoticeType()).isEqualTo("通知公告");
        assertThat(first.getEnterpriseName()).contains("欠税公告");
        assertThat(first.getNoticeDate()).isEqualTo("2026-10-22");
        assertThat(first.getSourceUrl()).contains("content_1.shtml");
    }

    /**
     * 查询 JSON 解析: 命中企业/识别号/金额/所属期/公告日期, 唯一键含所属期与税种规则。
     */
    @Test
    @DisplayName("查询应用 JSON: 解析企业明细并生成唯一键")
    void parseQueryJson() {
        TaxNoticeParser parser = new TaxNoticeParser(new com.fasterxml.jackson.databind.ObjectMapper());
        List<TaxNoticeRecord> records = parser.parseQueryJson(QUERY_JSON, "欠税", "ZJ", "http://src");
        assertThat(records).hasSize(1);
        TaxNoticeRecord record = records.get(0);
        assertThat(record.getEnterpriseName()).isEqualTo("示例企业有限公司");
        assertThat(record.getCreditCode()).isEqualTo("91330100MA27X00001");
        assertThat(record.getLegalPerson()).isEqualTo("张三");
        assertThat(record.getTaxAmount()).isEqualTo("12345.67");
        assertThat(record.getTaxPeriodStart()).isEqualTo("2026-07");
        assertThat(record.getTaxPeriodEnd()).isEqualTo("2026-09");
        assertThat(record.getNoticeDate()).isEqualTo("2026-10-22");
        assertThat(record.getNoticeAuthority()).isEqualTo("国家税务总局杭州市税务局");
        assertThat(record.getUniqueKey()).contains("91330100MA27X00001").contains("欠税");
    }

    /**
     * 空/非法输入不抛异常, 返回空列表（解析失败只丢记录, 不中断巡检）。
     */
    @Test
    @DisplayName("非法输入: 返回空列表不抛异常")
    void parseInvalidInput() {
        TaxNoticeParser parser = new TaxNoticeParser(new com.fasterxml.jackson.databind.ObjectMapper());
        assertThat(parser.parseStaticList(null, "通知公告", "GD", null)).isEmpty();
        assertThat(parser.parseStaticList("", "通知公告", "GD", null)).isEmpty();
        assertThat(parser.parseQueryJson("not-json{", "欠税", "GD", null)).isEmpty();
        assertThat(parser.parseQueryJson("{\"data\":\"not-array\"}", "欠税", "GD", null)).isEmpty();
    }

    /**
     * HTML 表格行解析: 按单元格启发式提取（列序因站点而异, 以联调实测为准）。
     */
    @Test
    @DisplayName("查询 HTML 行: 提取企业/法人/金额")
    void parseQueryHtml() {
        TaxNoticeParser parser = new TaxNoticeParser(new com.fasterxml.jackson.databind.ObjectMapper());
        String html = "<tr><td>示例企业有限公司</td><td>91330100MA27X00002</td><td>李四</td>"
                + "<td>2026-07</td><td>8888.00</td><td>2026-10-22</td></tr>";
        List<TaxNoticeRecord> records = parser.parseQueryHtml(html, "欠税", "GD", "http://src");
        assertThat(records).hasSize(1);
        TaxNoticeRecord record = records.get(0);
        assertThat(record.getEnterpriseName()).isEqualTo("示例企业有限公司");
        assertThat(record.getCreditCode()).isEqualTo("91330100MA27X00002");
        assertThat(record.getLegalPerson()).isEqualTo("李四");
        assertThat(record.getTaxAmount()).isEqualTo("8888.00");
    }
}