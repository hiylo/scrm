/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmMarketingBatchGenerateDto.java
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
 * 财税营销文案 AI 批量生成请求 DTO。
 * <p>
 * 对应「文案工作台: 选择场景 + 模板 + 占位符参数 + 语气」入口, 指定模板编码、
 * 占位符取值与语气档位, 批量生成多套变体。{@code variantCount} 超出
 * {@code ScrmMarketingBatchProperties} 上限时由服务层收敛, 不入库。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmMarketingBatchGenerateDto {

    /** 模板编码 (V12 入库的 TAX_MKT_* 标准模板, 或任意已入库模板) */
    @NotBlank(message = "模板编码不能为空")
    @Size(max = 50, message = "模板编码长度不能超过 50")
    private String templateCode;

    /** 占位符取值: {{客户类型}}/{{服务内容}}/{{活动信息}}/{{期限}} 等 (渲染与提示词注入用) */
    @NotNull(message = "占位符取值不能为空")
    private Map<String, Object> variables;

    /** 语气档位: 正式专业 / 亲切自然 / 简洁干练 / 节日温情 (默认 亲切自然) */
    @Size(max = 20, message = "语气档位长度不能超过 20")
    private String tone;

    /** 目标渠道: WORK_WECHAT/WECHAT/SMS/EMAIL (默认取模板首个适用渠道) */
    @Size(max = 20, message = "渠道长度不能超过 20")
    private String channel;

    /** 变体数量 (默认 3, 上限由配置 maxVariantCount 收敛) */
    @Min(value = 1, message = "变体数量最小为 1")
    @Max(value = 10, message = "变体数量最大为 10")
    private Integer variantCount;

    /** 渠道字数上限 (可空, 空则按渠道取配置默认) */
    @Min(value = 20, message = "字数上限最小为 20")
    @Max(value = 300, message = "字数上限最大为 300")
    private Integer charLimit;

    /** 生成人/操作人 (审计与素材 uploadedBy, 可空) */
    @Size(max = 100, message = "生成人长度不能超过 100")
    private String generatedBy;
}