/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ShaanxiTaxFetcherTest.java
 * Date : 2026/10/24 13:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.config.ScrmInspectionProperties;
import org.hiylo.scrm.dto.TaxNoticeFetcher;
import org.hiylo.scrm.dto.TaxNoticeParser;
import org.hiylo.scrm.dto.TaxNoticeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ShaanxiTaxFetcher} 单元测试。
 * <p>
 * 陕西站栏目页被瑞数 WAF 全站拦截（412），只有 {@code dataproxy.jsp} 数据端点可达，
 * 因此适配器走数据端点而非页面解析。本测试覆盖：
 * <ul>
 *   <li><b>开关闸门</b>：未启用时不发请求</li>
 *   <li><b>参数闸门</b>：缺 columnId/unitId/webId 时明确报错（而不是静默返回空）</li>
 *   <li><b>dataproxy 解析</b>：真实数据端点返回的 datastore/record CDATA 结构能被解析</li>
 * </ul>
 * 真实网络请求不在单测范围内（栏目参数需从被 WAF 拦截的页面取得）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("陕西税务抓取适配器")
class ShaanxiTaxFetcherTest {

    private ScrmInspectionProperties properties;

    private TaxNoticeParser parser;

    private ShaanxiTaxFetcher fetcher;

    @BeforeEach
    void setUp() {
        properties = new ScrmInspectionProperties();
        parser = new TaxNoticeParser(new ObjectMapper());
        fetcher = new ShaanxiTaxFetcher(parser, properties);
    }

    @Test
    @DisplayName("未启用：返回 SKIPPED，不发请求")
    void disabled_returnsSkipped() {
        properties.getNotice().setShaanxiEnabled(false);

        TaxNoticeFetcher result = fetcher.fetchNoticeList();

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getErrorMessage()).contains("SKIPPED");
        assertThat(result.isNeedCaptchaRetry()).isFalse();
    }

    @Test
    @DisplayName("已启用但缺栏目参数：明确报错（不静默返回空）")
    void enabledButMissingParams_reportsError() {
        properties.getNotice().setShaanxiEnabled(true);
        // 三个参数都空

        TaxNoticeFetcher result = fetcher.fetchNoticeList();

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getErrorMessage()).contains("缺少");
        assertThat(result.getErrorMessage()).contains("columnId");
    }

    @Test
    @DisplayName("参数只填一部分也报错（防御半配置）")
    void partialParams_reportsError() {
        properties.getNotice().setShaanxiEnabled(true);
        properties.getNotice().setShaanxiColumnId("22104");
        // unitId / webId 仍为空

        TaxNoticeFetcher result = fetcher.fetchNoticeList();

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getErrorMessage()).contains("缺少");
    }

    @Test
    @DisplayName("dataproxy 真实响应结构（datastore/record CDATA）能被解析为公告")
    void dataproxyResponse_parsesToRecords() throws IOException {
        String xml;
        try (InputStream in = getClass().getResourceAsStream(
                "/fixtures/tax-notice/dataproxy-response.xml")) {
            assertThat(in).as("dataproxy fixture 缺失").isNotNull();
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        List<TaxNoticeRecord> records = parser.parseStaticList(xml, "通知公告", "SX", null);

        assertThat(records).as("dataproxy 的 datastore 结构应能解析出公告").isNotEmpty();
        assertThat(records).allSatisfy(r -> {
            assertThat(r.getNoticeDate()).matches("\\d{4}-\\d{2}-\\d{2}");
            assertThat(r.getEnterpriseName()).isNotBlank();
            assertThat(r.getProvince()).isEqualTo("SX");
        });
        assertThat(records).extracting(TaxNoticeRecord::getEnterpriseName)
                .anyMatch(t -> t.contains("税务局"));
    }
}
