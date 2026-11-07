/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmMarketingBatchSaveDto.java
 * Date : 2026/10/23 16:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 财税营销 AI 生成变体落库请求 DTO。
 * <p>
 * 前端经 {@code POST /scrm/message-template-center/marketing/generate} 预览通过后,
 * 携带源模板编码与相同生成参数确认落库。服务端基于源模板重新调用 AI 生成并执行
 * 合规校验（防止绕过校验直接入库）, 通过项保存为 DRAFT 进入人工审核队列。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmMarketingBatchSaveDto {

    /** 源模板编码 (V12 入库的 TAX_MKT_* 标准模板) */
    @NotBlank(message = "源模板编码不能为空")
    @Size(max = 50, message = "源模板编码长度不能超过 50")
    private String sourceTemplateCode;

    /** 占位符取值 (与生成请求一致, 渲染与提示词注入用) */
    @NotNull(message = "占位符取值不能为空")
    private Map<String, Object> variables;

    /** 语气档位 (与生成请求一致) */
    @Size(max = 20, message = "语气档位长度不能超过 20")
    private String tone;

    /** 目标渠道 (与生成请求一致) */
    @Size(max = 20, message = "渠道长度不能超过 20")
    private String channel;

    /** 变体数量 (与生成请求一致, 默认 3) */
    @Min(value = 1, message = "变体数量最小为 1")
    @Max(value = 10, message = "变体数量最大为 10")
    private Integer variantCount;

    /** 渠道字数上限 (可空) */
    @Min(value = 20, message = "字数上限最小为 20")
    @Max(value = 300, message = "字数上限最大为 300")
    private Integer charLimit;

    /** 生成人/操作人 (审计与 createdBy, 可空) */
    @Size(max = 100, message = "生成人长度不能超过 100")
    private String generatedBy;
}