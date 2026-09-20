/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiAssistantConfigDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM AI 助手配置 DTO。
 * <p>
 * 对应 {@code ScrmAiAssistantConfigEntity} 的业务字段, 创建/更新接口入参。
 * provider 仅支持 OPENAI/AZURE/LOCAL/ZHIPU/QWEN, model 默认 gpt-4o-mini,
 * temperature 默认 0.7, maxTokens 默认 1000。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAiAssistantConfigDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 配置名称 */
    @NotBlank(message = "配置名称不能为空")
    @Size(max = 200, message = "配置名称长度不能超过 200")
    private String configName;

    /** 服务提供方: OPENAI / AZURE / LOCAL / ZHIPU / QWEN */
    @NotBlank(message = "服务提供方不能为空")
    @Pattern(regexp = "OPENAI|AZURE|LOCAL|ZHIPU|QWEN",
            message = "服务提供方仅支持 OPENAI/AZURE/LOCAL/ZHIPU/QWEN")
    private String provider;

    /** 模型名称 (默认 gpt-4o-mini) */
    @Size(max = 100, message = "模型名称长度不能超过 100")
    private String model;

    /** API Key (加密存储) */
    @Size(max = 500, message = "API Key 长度不能超过 500")
    private String apiKey;

    /** API Endpoint */
    @Size(max = 500, message = "API Endpoint 长度不能超过 500")
    private String apiEndpoint;

    /** 系统提示词 */
    private String systemPrompt;

    /** 温度参数 (默认 0.7) */
    private Double temperature;

    /** 最大 token 数 (默认 1000) */
    private Integer maxTokens;

    /** 关联知识库 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeBaseId;

    /** 是否启用 (创建时可选, 默认 true) */
    private Boolean enabled;

    /** 是否为默认配置 (创建时可选, 默认 false) */
    private Boolean isDefault;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 累计请求次数 (查询返回) */
    private Integer requestCount;

    /** 最近使用时间 (查询返回) */
    private LocalDateTime lastUsedAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
