/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : KnowledgeTemplateParserTest.java
 * Date : 2026/10/23 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.parser;

import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.service.finance.model.KnowledgeTemplateEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T06 模板解析器单元测试。
 * <p>
 * 覆盖: 完整版模板通用字段提取 / 业务字段提取 / 行内多字段（全角空格分隔）/
 * 「最后人工复核日期：—（待审核）」null 兜底 / 标题与规则编号缺失打回 /
 * 问答对提取 / 文件名规则编号兜底 / 列表项追加到业务字段。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("KnowledgeTemplateParser 单元测试")
class KnowledgeTemplateParserTest {

    @Test
    @DisplayName("parse_fullTemplate: 完整版模板通用字段 + 业务字段 + 问答对提取")
    void parse_fullTemplate() throws ScrmException {
        String markdown = """
                # 注册公司需要什么材料（新设有限责任公司）

                - 分类：`营业执照办理`
                - 规则编号：`R-BIZ-0001`
                - 适用地区：全国通用（部分省/市有线上系统差异）
                - 适用对象：新设有限责任公司（内资）
                - 来源：MANUAL（AI 起草）
                - 状态：草稿　审查状态：`待人工审核`　最后人工复核日期：—（待审核）
                - 版本号：1　录入人：AI 起草　录入日期：2026-10-22
                - 关联条目：`R-BIZ-0002`（办理流程）
                - 问答对：
                  - 问：注册公司需要什么材料
                  - 答：需要申请书、章程、住所证明等

                ## 办理条件

                - 股东 1-50 名，自然人/法人均可

                ## 所需材料清单

                1. 《公司登记（备案）申请书》：原件
                2. 公司章程：全体股东签署

                ## 政策依据

                - 《市场主体登记管理条例》（国务院令第 746 号）

                ## 附注

                - 待确认事项：住所申报承诺制适用地区范围
                """;

        KnowledgeTemplateEntry entry = KnowledgeTemplateParser.parse("R-BIZ-0001-注册公司需要什么材料.md", markdown);

        assertThat(entry.getTitle()).isEqualTo("注册公司需要什么材料（新设有限责任公司）");
        assertThat(entry.getCategory()).isEqualTo("营业执照办理");
        assertThat(entry.getArticleCode()).isEqualTo("R-BIZ-0001");
        assertThat(entry.getApplicableRegion()).contains("全国通用");
        assertThat(entry.getApplicableObject()).contains("新设有限责任公司");
        assertThat(entry.getSourceType()).isEqualTo("MANUAL");
        assertThat(entry.getStatus()).isEqualTo("草稿");
        assertThat(entry.getReviewStatus()).isEqualTo("待人工审核");
        assertThat(entry.getLastReviewedDate()).isNull();
        assertThat(entry.getVersionNumber()).isEqualTo(1);
        assertThat(entry.getCreatedBy()).isEqualTo("AI 起草");
        assertThat(entry.getCreatedDate()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(entry.getRelatedArticles()).isEqualTo("R-BIZ-0002（办理流程）");
        assertThat(entry.getQaPairs()).isNotEmpty();
        assertThat(entry.getQaPairs().get(0)).contains("注册公司需要什么材料").contains("申请书、章程、住所证明");
        // 业务字段
        assertThat(entry.getBusinessFields()).containsKey("办理条件");
        assertThat(entry.getBusinessFields().get("办理条件")).contains("股东 1-50 名");
        // 列表项追加
        assertThat(entry.getBusinessFields()).containsKey("所需材料清单");
        assertThat(entry.getBusinessFields().get("所需材料清单")).contains("《公司登记（备案）申请书》");
        assertThat(entry.getBusinessFields().get("所需材料清单")).contains("公司章程");
        assertThat(entry.getBusinessFields().get("政策依据")).contains("国务院令第 746 号");
        // 附注
        assertThat(entry.getNotes()).contains("住所申报承诺制适用地区范围");
    }

    @Test
    @DisplayName("parse_reviewedDate: 已审核条目最后人工复核日期解析为 LocalDate")
    void parse_reviewedDate() throws ScrmException {
        String markdown = """
                # 简易注销流程
                - 分类：`注销流程`
                - 规则编号：`R-CANCEL-0001`
                - 适用地区：全国
                - 适用对象：未开业/无债权债务企业
                - 来源：IMPORT
                - 状态：已发布　审查状态：`已审核`　最后人工复核日期：2026-10-01
                - 版本号：2　录入人：张三　录入日期：2026-09-01
                """;

        KnowledgeTemplateEntry entry = KnowledgeTemplateParser.parse("R-CANCEL-0001.md", markdown);
        assertThat(entry.getReviewStatus()).isEqualTo("已审核");
        assertThat(entry.getLastReviewedDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(entry.getVersionNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("parse_missingTitle: 缺标题抛 ScrmException")
    void parse_missingTitle() {
        String markdown = "- 分类：`注销流程`\n- 规则编号：`R-CANCEL-0001`\n";
        assertThatThrownBy(() -> KnowledgeTemplateParser.parse("R-CANCEL-0001.md", markdown))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("标题");
    }

    @Test
    @DisplayName("parse_missingCode: 缺规则编号且文件名无编号抛 ScrmException")
    void parse_missingCode() {
        String markdown = "# 简易注销流程\n- 分类：`注销流程`\n";
        assertThatThrownBy(() -> KnowledgeTemplateParser.parse("简易注销流程.md", markdown))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("规则编号");
    }

    @Test
    @DisplayName("parse_codeFromFileName: 规则编号缺失时从文件名提取")
    void parse_codeFromFileName() throws ScrmException {
        String markdown = "# 简易注销流程\n- 分类：`注销流程`\n- 适用地区：全国\n";
        KnowledgeTemplateEntry entry = KnowledgeTemplateParser.parse("R-CANCEL-0001-简易注销流程.md", markdown);
        assertThat(entry.getArticleCode()).isEqualTo("R-CANCEL-0001");
    }

    @Test
    @DisplayName("parse_badDate: 复核日期格式非法抛 ScrmException")
    void parse_badDate() {
        String markdown = """
                # 简易注销流程
                - 分类：`注销流程`
                - 规则编号：`R-CANCEL-0001`
                - 最后人工复核日期：2026-13-99
                """;
        assertThatThrownBy(() -> KnowledgeTemplateParser.parse("R-CANCEL-0001.md", markdown))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("日期格式非法");
    }
}
