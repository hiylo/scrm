/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmMarketingVariantDto.java
 * Date : 2026/10/23 16:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 财税营销 AI 批量生成结果 DTO（单套变体）。
 * <p>
 * 对应 prompts/marketing-batch.md 输出 JSON 数组元素结构:
 * {@code {title, content, tone, charCount, riskNotes}}。AI 返回内容反序列化
 * 失败字段静默跳过, 仅 title/content 必填, 其余缺省。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScrmMarketingVariantDto {

    /** 变体标题 (对应模板 subject 渲染或 LLM 变体标题) */
    @NotBlank(message = "变体标题不能为空")
    @Size(max = 200, message = "变体标题长度不能超过 200")
    private String title;

    /** 变体正文 (完整成文, 不得残留 {{占位符}}) */
    @NotBlank(message = "变体正文不能为空")
    private String content;

    /** 语气档位 (回显) */
    @Size(max = 20, message = "语气档位长度不能超过 20")
    private String tone;

    /** 字数 (渠道字数校验用, 可为空由服务层重算) */
    private Integer charCount;

    /** 需人工重点审核的点 (无则空数组) */
    private List<String> riskNotes = new ArrayList<>();
}