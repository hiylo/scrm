/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInspectionExecuteDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SCRM 单会话质检执行 DTO。
 * <p>
 * 单会话即时质检接口入参, 携带目标会话 ID 与参与评估的规则 ID 列表 JSON。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmInspectionExecuteDto {

    /** 目标会话 ID */
    @NotNull(message = "会话 ID 不能为空")
    private Long conversationId;

    /** 质检规则 ID 列表 JSON 数组 (如 [123, 456]), 为空时使用全部启用规则 */
    @NotBlank(message = "规则 ID 列表不能为空")
    private String ruleIds;
}
