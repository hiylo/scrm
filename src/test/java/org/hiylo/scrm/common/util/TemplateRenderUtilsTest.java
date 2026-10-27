/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : TemplateRenderUtilsTest.java
 * Date : 2026/10/28 04:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TemplateRenderUtils} 单元测试。
 * <p>
 * 锁住三条核心约定：三种占位符语法都要生效、缺失变量保留原占位符、
 * 单次扫描不会被变量值里的占位符二次替换。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("模板渲染工具：统一三种占位符语法")
class TemplateRenderUtilsTest {

    @Test
    @DisplayName("单花括号 {var}：通知模板与话术模板的写法")
    void render_singleBrace() {
        String result = TemplateRenderUtils.render(
                "报税提醒: {customerName} {taxTypeLabel}",
                Map.of("customerName", "鼎昊环境", "taxTypeLabel", "增值税"));

        assertThat(result).isEqualTo("报税提醒: 鼎昊环境 增值税");
    }

    @Test
    @DisplayName("双花括号 {{var}}：消息模板与模板中心的写法")
    void render_doubleBrace() {
        String result = TemplateRenderUtils.render(
                "客户：{{customerName}}，金额：{{amount}}元",
                Map.of("customerName", "鼎昊环境", "amount", "4800"));

        assertThat(result).isEqualTo("客户：鼎昊环境，金额：4800元");
    }

    @Test
    @DisplayName("美元花括号 ${var}：欢迎语与合同模板的写法")
    void render_dollarBrace() {
        String result = TemplateRenderUtils.render(
                "客户：${customerName}", Map.of("customerName", "西安鼎昊"));

        assertThat(result).isEqualTo("客户：西安鼎昊");
    }

    @Test
    @DisplayName("三种语法混在同一模板里都能替换")
    void render_mixedSyntax() {
        String result = TemplateRenderUtils.render(
                "{a} {{b}} ${c}", Map.of("a", "1", "b", "2", "c", "3"));

        assertThat(result).isEqualTo("1 2 3");
    }

    @Test
    @DisplayName("缺失变量保留原占位符，不静默替换成空串")
    void render_keepsPlaceholderWhenMissing() {
        String result = TemplateRenderUtils.render(
                "客户：{{customerName}}，金额：${amount}",
                Map.of("customerName", "鼎昊环境"));

        assertThat(result).contains("鼎昊环境").contains("${amount}");
    }

    @Test
    @DisplayName("变量值本身含占位符时不会被二次替换")
    void render_noDoubleSubstitution() {
        // 变量值里恰好写了 {amount}，若实现按变量逐个 replace，会被再替换一次
        String result = TemplateRenderUtils.render(
                "{note} / {amount}",
                Map.of("note", "见{amount}栏", "amount", "100"));

        assertThat(result).isEqualTo("见{amount}栏 / 100");
    }

    @Test
    @DisplayName("提取变量名去重且保持出现顺序")
    void extractVariables_dedupInOrder() {
        List<String> vars = TemplateRenderUtils.extractVariables(
                "{a} {{b}} ${a} {c}");

        assertThat(vars).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("能检出渲染后残留的占位符")
    void hasUnresolvedPlaceholder_detects() {
        assertThat(TemplateRenderUtils.hasUnresolvedPlaceholder("客户：{{customerName}}")).isTrue();
        assertThat(TemplateRenderUtils.hasUnresolvedPlaceholder("客户：鼎昊环境")).isFalse();
        assertThat(TemplateRenderUtils.hasUnresolvedPlaceholder(null)).isFalse();
    }

    @Test
    @DisplayName("不含占位符的中文与标点原样返回")
    void render_plainTextUntouched() {
        String text = "甲方委托乙方提供代理记账服务。（含标点，无变量）";
        assertThat(TemplateRenderUtils.render(text, Map.of("x", "1"))).isEqualTo(text);
    }

    @Test
    @DisplayName("模板为 null 或变量为空时原样返回")
    void render_nullSafety() {
        assertThat(TemplateRenderUtils.render(null, Map.of("a", "1"))).isNull();
        assertThat(TemplateRenderUtils.render("{a}", null)).isEqualTo("{a}");
        assertThat(TemplateRenderUtils.render("{a}", Map.of())).isEqualTo("{a}");
    }

    @Test
    @DisplayName("中文变量名也能识别：营销模板里存在 {{客户类型}} 这种写法")
    void render_chineseVariableName() {
        String result = TemplateRenderUtils.render(
                "恭喜{{客户类型}}开业！", Map.of("客户类型", "餐饮店"));

        assertThat(result).isEqualTo("恭喜餐饮店开业！");
    }

    @Test
    @DisplayName("中文占位符未替换时能被检出")
    void hasUnresolvedPlaceholder_chinese() {
        assertThat(TemplateRenderUtils.hasUnresolvedPlaceholder("恭喜{{客户类型}}开业！")).isTrue();
        assertThat(TemplateRenderUtils.extractVariables("{{客户类型}}")).containsExactly("客户类型");
    }
}
