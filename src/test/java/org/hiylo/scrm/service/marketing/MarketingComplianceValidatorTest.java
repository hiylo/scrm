/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : MarketingComplianceValidatorTest.java
 * Date : 2026/10/23 16:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.marketing;

import org.hiylo.scrm.config.ScrmMarketingBatchProperties;
import org.hiylo.scrm.dto.ScrmMarketingVariantDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MarketingComplianceValidator} 单元测试。
 * <p>
 * 覆盖 prompts.md §4.1 程序化拦截四道闸门: 占位符完整性、合规禁语扫描、
 * 渠道字数校验与结构完整性, 以及「第一时间」上下文豁免与虚假紧迫/夸大数字正则。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("MarketingComplianceValidator 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketingComplianceValidatorTest {

    /** 被测对象 (默认配置: SMS 67 / EMAIL 300 / 默认 120) */
    private MarketingComplianceValidator validator;

    /**
     * 测试前装配被测对象。
     */
    @BeforeEach
    void setUp() {
        validator = new MarketingComplianceValidator(new ScrmMarketingBatchProperties());
    }

    @Test
    @DisplayName("合规文案: 无违规项, 字数在渠道上限内")
    void validate_cleanPass() {
        ScrmMarketingVariantDto variant = new ScrmMarketingVariantDto();
        variant.setTitle("开业贺礼");
        variant.setContent("恭喜开业！为您提供代理记账服务，详情可咨询财税顾问，具体以合同为准。");
        List<String> violations = validator.validate(variant, "WORK_WECHAT", null);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("占位符残留: {{客户类型}} 未替换即不合格")
    void validate_residualPlaceholder() {
        ScrmMarketingVariantDto variant = new ScrmMarketingVariantDto();
        variant.setTitle("残留占位符");
        variant.setContent("恭喜{{客户类型}}开业！详情以合同为准。");
        List<String> violations = validator.validate(variant, "WORK_WECHAT", null);
        assertThat(violations).contains("残留未替换占位符");
    }

    @Test
    @DisplayName("合规禁语: 保证收益/绝对化用语命中")
    void validate_forbiddenWords() {
        ScrmMarketingVariantDto variant = new ScrmMarketingVariantDto();
        variant.setTitle("禁语");
        variant.setContent("我们保证为您彻底省税，是最好的代账服务，包赚不赔。");
        List<String> violations = validator.validate(variant, "WORK_WECHAT", null);
        assertThat(violations).anyMatch(v -> v.contains("保证"));
        assertThat(violations).anyMatch(v -> v.contains("最好"));
        assertThat(violations).anyMatch(v -> v.contains("包赚"));
    }

    @Test
    @DisplayName("合规禁语: 「第一时间」上下文豁免, 不判为绝对化「第一」")
    void validate_firstTimeExempt() {
        ScrmMarketingVariantDto variant = new ScrmMarketingVariantDto();
        variant.setTitle("豁免");
        variant.setContent("我们会第一时间为您处理申报事项，具体以税务机关答复为准。");
        List<String> violations = validator.validate(variant, "WORK_WECHAT", null);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("虚假紧迫: 「最后 3 个名额」命中正则")
    void validate_fakeUrgency() {
        ScrmMarketingVariantDto variant = new ScrmMarketingVariantDto();
        variant.setTitle("虚假紧迫");
        variant.setContent("仅限今日，最后 3 个名额，错过再等一年！");
        List<String> violations = validator.validate(variant, "WORK_WECHAT", null);
        assertThat(violations).anyMatch(v -> v.contains("虚假紧迫") || v.contains("仅限今日") || v.contains("错过再等一年"));
    }

    @Test
    @DisplayName("夸大数字承诺: 省税 30% 起命中正则")
    void validate_exaggeratedNumber() {
        ScrmMarketingVariantDto variant = new ScrmMarketingVariantDto();
        variant.setTitle("夸大");
        variant.setContent("选择我们，省税 30% 起！");
        List<String> violations = validator.validate(variant, "WORK_WECHAT", null);
        assertThat(violations).anyMatch(v -> v.contains("夸大数字承诺"));
    }

    @Test
    @DisplayName("短信渠道: 超 67 字打回")
    void validate_smsCharLimit() {
        ScrmMarketingVariantDto variant = new ScrmMarketingVariantDto();
        variant.setTitle("短信超长");
        variant.setContent("亲爱的客户您好，我们诚挚地邀请您参加本月的财税优惠活动，届时会有资深顾问一对一为您提供服务，欢迎您届时准时参加活动详情以合同为准请随时联系您的专属财税顾问。");
        List<String> violations = validator.validate(variant, "SMS", null);
        assertThat(violations).anyMatch(v -> v.contains("超过渠道字数上限"));
    }

    @Test
    @DisplayName("涉税内容缺免责行: 硬性校验不拦截, 但给出人工审核建议")
    void validate_taxWithoutDisclaimer() {
        ScrmMarketingVariantDto variant = new ScrmMarketingVariantDto();
        variant.setTitle("无免责");
        variant.setContent("新政策下您可以少缴很多税，详情咨询顾问。");
        List<String> violations = validator.validate(variant, "WORK_WECHAT", null);
        assertThat(violations).isEmpty();
        List<String> advisories = validator.collectAdvisories(variant);
        assertThat(advisories).anyMatch(a -> a.contains("免责"));
    }

    @Test
    @DisplayName("字数统计: 不含标点/空白, 仅字母数字")
    void charCount_excludesPunctuation() {
        assertThat(validator.charCount("你好，world！ 123")).isEqualTo(10);
    }

    @Test
    @DisplayName("渠道字数解析: 入参优先, SMS 走配置 67, 其他走默认 120")
    void resolveCharLimit_priority() {
        assertThat(validator.resolveCharLimit("SMS", null)).isEqualTo(67);
        assertThat(validator.resolveCharLimit("EMAIL", null)).isEqualTo(300);
        assertThat(validator.resolveCharLimit("WORK_WECHAT", null)).isEqualTo(120);
        assertThat(validator.resolveCharLimit("WORK_WECHAT", 99)).isEqualTo(99);
    }
}