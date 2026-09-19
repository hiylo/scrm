/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmHealthCalculateDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SCRM 客户健康度评分计算 DTO。
 * <p>
 * 触发单个客户健康度评分计算的入参, forceRecalculate 为 true 时强制重算 (跳过缓存 /
 * 频率判断, 直接执行完整计算)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmHealthCalculateDto {

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 健康度模型 ID */
    @NotNull(message = "模型 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    /** 是否强制重算 (默认 false, 跳过频率判断) */
    private Boolean forceRecalculate;
}
