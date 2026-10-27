/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingBatchProperties.java
 * Date : 2026/10/23 16:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 财税营销模板 AI 批量生成配置。
 * <p>
 * 绑定 {@code scrm.marketing-batch.*}: AI 批量生成使用的模型 / 温度 / 默认最大 token 数、
 * 单次批量默认变体数量 (1~10)、短信渠道 67 字硬上限与提示词资源文件路径。
 * 配置由 {@link MarketingBatchApiConfig} 注册, 不修改 application.yml。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@ConfigurationProperties(prefix = "scrm.marketing-batch")
public class ScrmMarketingBatchProperties {

    /** 批量生成使用的 AI 模型名称 (对齐 {@code scrm.ai.model}, 默认 gpt-4o-mini) */
    private String model = "gpt-4o-mini";

    /** 采样温度 (默认 0.7, 批量变体追求多样性建议 0.8~0.9) */
    private double temperature = 0.8;

    /** 生成最大 token 数 (批量变体 JSON 较长, 默认 3000) */
    private int maxTokens = 3000;

    /** 单次批量默认变体数量 (默认 3, 允许范围 1~10) */
    private int defaultVariantCount = 3;

    /** 单次批量最大变体数量 (防滥用上限) */
    private int maxVariantCount = 10;

    /** 企微/微信渠道默认字数上限 (不含标点计, 默认 120) */
    private int defaultCharLimit = 120;

    /** 短信渠道硬字数上限 (prompts.md: 67 字硬上限) */
    private int smsCharLimit = 67;

    /** 邮件渠道字数上限 (prompts.md: 可到 300 字, 三段式) */
    private int emailCharLimit = 300;

    /** 批量生成主指令提示词资源文件 (classpath 相对路径) */
    private String promptResource = "classpath:prompts/marketing-batch.md";
}