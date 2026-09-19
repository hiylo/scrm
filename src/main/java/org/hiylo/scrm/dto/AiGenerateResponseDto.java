/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AiGenerateResponseDto.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 回复生成回调响应 DTO，封装 scrm-server 生成的回复内容与元信息返回给行为流脚本。
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGenerateResponseDto {

    /** AI 生成的回复内容（AI 不可用时为兜底回复） */
    private String reply;

    /** 实际生效的人设 ID（默认人设时为 "default"） */
    private String personaId;

    /** 实际生成使用的模型名称 */
    private String model;

    /** 生成耗时（毫秒） */
    private Long latencyMs;
}
