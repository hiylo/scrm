/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmContractTemplateServiceTest.java
 * Date : 2026/10/28 03:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmContractTemplateDto;
import org.hiylo.scrm.entity.ScrmContractTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmContractRepository;
import org.hiylo.scrm.repository.ScrmContractTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ScrmContractTemplateService} 渲染测试。
 * <p>
 * 重点锁住占位符语法的兼容性。历史问题：渲染器只认 {@code {{xxx}}}，而入库的
 * 「代理记账服务合同」模板写的是 {@code ${customerName}}，导致合同正文原样保留
 * 未替换的占位符进入正式文件，且全程没有任何报错。文档约定与实际录入习惯不一致时，
 * 静默不生效比报错更危险。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("合同模板渲染：两种占位符语法都要生效")
class ScrmContractTemplateServiceTest {

    @Mock
    private ScrmContractTemplateRepository templateRepository;

    @Mock
    private ScrmContractRepository contractRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ScrmContractTemplateService service() {
        return new ScrmContractTemplateService(objectMapper, templateRepository, contractRepository);
    }

    @Test
    @DisplayName("双花括号语法按文档约定正常替换")
    void renderContent_doubleBrace() {
        String result = service().renderContent(
                "客户：{{customerName}}，金额：{{amount}}元",
                Map.of("customerName", "鼎昊环境", "amount", "4800"));

        assertThat(result).isEqualTo("客户：鼎昊环境，金额：4800元");
    }

    @Test
    @DisplayName("美元花括号语法也要替换：实际录入的模板常这么写")
    void renderContent_dollarBrace() {
        String result = service().renderContent(
                "甲方委托乙方提供代理记账服务，合同期限一年。客户：${customerName}",
                Map.of("customerName", "西安鼎昊环境工程有限公司"));

        assertThat(result)
                .isEqualTo("甲方委托乙方提供代理记账服务，合同期限一年。客户：西安鼎昊环境工程有限公司")
                .doesNotContain("${");
    }

    @Test
    @DisplayName("两种语法混用在同一模板里都能替换")
    void renderContent_mixedSyntax() {
        String result = service().renderContent(
                "客户 ${customerName} 合同 {{contractNo}}",
                Map.of("customerName", "张三公司", "contractNo", "HT-001"));

        assertThat(result).isEqualTo("客户 张三公司 合同 HT-001");
    }

    @Test
    @DisplayName("变量缺失时保留原占位符，不静默替换成空")
    void renderContent_keepsPlaceholderWhenMissing() {
        String result = service().renderContent(
                "客户：${customerName}", Map.of("other", "x"));

        assertThat(result).contains("${customerName}");
    }

    @Test
    @DisplayName("录入漏写花括号的 {xxx} 立即报错，而不是等到生成合同才发现")
    void createTemplate_rejectsSingleBrace() {
        ScrmContractTemplateDto dto = new ScrmContractTemplateDto();
        dto.setTemplateName("测试模板");
        dto.setTemplateCode("TPL_BAD_SYNTAX");
        dto.setContractType("AGENCY");
        dto.setTemplateContent("客户：{customerName}，服务期限一年");

        assertThatThrownBy(() -> service().createTemplate(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模板变量写法不正确");
    }

    @Test
    @DisplayName("两种合法写法都放行，正常中文与标点不受影响")
    void createTemplate_acceptsBothValidSyntax() {
        ScrmContractTemplateDto dto = new ScrmContractTemplateDto();
        dto.setTemplateName("测试模板");
        dto.setTemplateCode("TPL_GOOD_SYNTAX");
        dto.setContractType("AGENCY");
        dto.setTemplateContent("客户：{{customerName}}，金额 ${amount} 元。（含标点，无花括号）");
        when(templateRepository.findByTemplateCode("TPL_GOOD_SYNTAX"))
                .thenReturn(java.util.Optional.empty());
        when(templateRepository.save(any(ScrmContractTemplateEntity.class)))
                .thenAnswer(inv -> {
                    ScrmContractTemplateEntity e = inv.getArgument(0);
                    e.setId(7001L);
                    return e;
                });

        assertThat(service().createTemplate(dto)).isNotNull();
    }
}
