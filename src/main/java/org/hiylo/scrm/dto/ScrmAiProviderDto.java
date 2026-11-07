/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmAiProviderDto.java
 * Date : 2026/11/07 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI Provider 配置 DTO。
 * <p>
 * apiKey 采用「写入不读」语义：新增/更新时提交明文，服务端加密落库；
 * 读取列表/详情时返回掩码（已配置则只显示后 4 位）与 hasApiKey 标记，
 * 不回显明文，避免前端来回传造成密文污染。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAiProviderDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** Provider 名称（唯一） */
    @NotBlank(message = "Provider 名称不能为空")
    @Size(max = 100, message = "Provider 名称长度不能超过 100")
    private String providerName;

    /** Provider 类型：OPENAI_LLM / OLLAMA / MAGPIE / OPENAI_VISION / LOCAL_LLM */
    @NotBlank(message = "Provider 类型不能为空")
    @Size(max = 30, message = "Provider 类型长度不能超过 30")
    private String providerType;

    /** API 基础地址（OpenAI 兼容） */
    @NotBlank(message = "API 基础地址不能为空")
    @Size(max = 500, message = "API 基础地址长度不能超过 500")
    private String apiBaseUrl;

    /** 对话补全路径（默认 /v1/chat/completions） */
    @Size(max = 100, message = "对话补全路径长度不能超过 100")
    private String chatPath = "/v1/chat/completions";

    /** 健康/模型探测路径（默认 /v1/models） */
    @Size(max = 100, message = "健康探测路径长度不能超过 100")
    private String healthPath = "/v1/models";

    /** API 密钥（新增/更新时提交明文；响应中为掩码或空） */
    @Size(max = 500, message = "API 密钥长度不能超过 500")
    private String apiKey;

    /** 是否已配置密钥（响应辅助标记） */
    private Boolean hasApiKey = false;

    /** 能力位串：逗号分隔 text,image,embedding */
    @NotBlank(message = "能力不能为空")
    @Size(max = 100, message = "能力长度不能超过 100")
    private String capability = "text";

    /** 默认模型名（空则由 /v1/models 自动推荐） */
    @Size(max = 100, message = "默认模型名长度不能超过 100")
    private String defaultModel;

    /** 采样温度 */
    private Double temperature = 0.7;

    /** 单次生成最大 token 数 */
    private Integer maxTokens = 500;

    /** 是否启用 */
    private Boolean enabled = true;

    /** 是否默认文本 provider */
    private Boolean defaultText = false;

    /** 是否默认多模态 provider */
    private Boolean defaultImage = false;

    /** 路由优先级（数值小优先） */
    private Integer priority = 100;

    /** 连接状态：CONNECTED / DISCONNECTED / UNKNOWN */
    private String connectionStatus = "UNKNOWN";

    /** 最后连通测试时间 */
    private LocalDateTime lastTestedAt;

    /** 最近模型清单缓存（从 /v1/models 拉取，仅展示） */
    private List<AiModelInfo> models;

    /** 创建人 */
    private String createdBy;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;

    /**
     * 模型能力摘要（从 provider /v1/models 拉取，仅用于管理端展示与默认模型选择）。
     */
    @Data
    public static class AiModelInfo {
        /** 模型 ID（如 group/sensenova-pool / qwen3:8b） */
        private String name;
        /** 是否支持文本对话 */
        private boolean text;
        /** 是否支持图像输入（多模态，可用于证照 OCR） */
        private boolean image;
        /** 是否支持 embedding（预留） */
        private boolean embedding;
    }
}