/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : TaxNoticeParserRealPageTest.java
 * Date : 2026/10/24 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TaxNoticeParser} 真实页面回归测试。
 * <p>
 * <b>为什么单独建这个类</b>：原先 {@code TaxNoticeParserTest} 用的是手写的简化 HTML，
 * 结构与真实站点完全不同——测试全绿但一到真实页面就失效（把导航项当公告、把识别号片段当日期）。
 * 这里用从广东/浙江税务官网**实际抓取**的页面作为 fixture，锁住真实结构。
 * </p>
 * <p>fixture 来源（2026-10-24 抓取，HTTP 200）：
 * <ul>
 *   <li>{@code guangdong-notice-list.html} — 广东省税务局「通知公告」列表页</li>
 *   <li>{@code zhejiang-notice-list.html} — 浙江省税务局「通知公告」列表页</li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("公告解析 · 真实页面回归")
class TaxNoticeParserRealPageTest {

    /**
     * 读取 fixture。
     *
     * @param name 文件名
     * @return 文本内容
     * @throws IOException 读取失败
     */
    private String fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/tax-notice/" + name)) {
            assertThat(in).as("fixture 缺失: " + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("广东真实页面：解析出公告、日期合法、标题不含导航项")
    void guangdongRealPage() throws IOException {
        TaxNoticeParser parser = new TaxNoticeParser(new com.fasterxml.jackson.databind.ObjectMapper());

        List<TaxNoticeRecord> records = parser.parseStaticList(
                fixture("guangdong-notice-list.html"), "通知公告", "GD", null);

        assertThat(records).as("应从真实页面解析出公告").isNotEmpty();
        // 每条都必须有合法日期（导航项没有日期，会被过滤）
        assertThat(records).allSatisfy(r -> {
            assertThat(r.getNoticeDate()).as("日期不能为空").isNotBlank();
            assertThat(r.getEnterpriseName()).as("标题不能为空").isNotBlank();
            assertThat(r.getProvince()).isEqualTo("GD");
            assertThat(r.getNoticeType()).isEqualTo("通知公告");
        });
        // 真实公告标题包含「税务局」等政务字样，导航项（首页/信息公开）不应出现
        assertThat(records).extracting(TaxNoticeRecord::getEnterpriseName)
                .noneMatch(t -> t.equals("首页") || t.equals("信息公开") || t.equals("简体")
                        || t.equals("页面放大") || t.equals("语音模式"));
        assertThat(records).extracting(TaxNoticeRecord::getEnterpriseName)
                .anyMatch(t -> t.contains("税务局") || t.contains("税务"));
        // 详情链接必须存在
        assertThat(records).extracting(TaxNoticeRecord::getSourceUrl).anyMatch(u -> u != null && u.contains(".shtml"));
    }

    @Test
    @DisplayName("浙江真实页面：解析出公告、日期合法、标题不含导航项")
    void zhejiangRealPage() throws IOException {
        TaxNoticeParser parser = new TaxNoticeParser(new com.fasterxml.jackson.databind.ObjectMapper());

        List<TaxNoticeRecord> records = parser.parseStaticList(
                fixture("zhejiang-notice-list.html"), "通知公告", "ZJ", null);

        assertThat(records).as("应从真实页面解析出公告").isNotEmpty();
        assertThat(records).allSatisfy(r -> {
            assertThat(r.getNoticeDate()).isNotBlank();
            assertThat(r.getEnterpriseName()).isNotBlank();
            assertThat(r.getProvince()).isEqualTo("ZJ");
        });
        assertThat(records).extracting(TaxNoticeRecord::getEnterpriseName)
                .noneMatch(t -> t.equals("首页") || t.equals("机构职能") || t.equals("通知公告")
                        || t.equals("人事管理") || t.equals("政府采购"));
        assertThat(records).extracting(TaxNoticeRecord::getEnterpriseName)
                .anyMatch(t -> t.contains("税务局") || t.contains("社会保险费"));
    }

    @Test
    @DisplayName("真实页面：日期全部落在 2000-2099 且可解析")
    void realPageDatesAreValid() throws IOException {
        TaxNoticeParser parser = new TaxNoticeParser(new com.fasterxml.jackson.databind.ObjectMapper());

        for (String[] cfg : new String[][]{
                {"guangdong-notice-list.html", "GD"},
                {"zhejiang-notice-list.html", "ZJ"}}) {
            List<TaxNoticeRecord> records = parser.parseStaticList(
                    fixture(cfg[0]), "通知公告", cfg[1], null);
            assertThat(records).isNotEmpty();
            assertThat(records).allSatisfy(r -> {
                String date = r.getNoticeDate();
                assertThat(date).matches("\\d{4}-\\d{2}-\\d{2}");
                int year = Integer.parseInt(date.substring(0, 4));
                assertThat(year).isBetween(2000, 2099);
            });
        }
    }
}
