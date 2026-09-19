/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagRuleTestDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * SCRM 标签规则测试 DTO。
 * <p>
 * 规则测试接口入参, 携带规则 ID 与候选客户 ID 列表, Service 层加载规则对每个客户评估
 * 条件 (不实际打标), 返回命中的客户 ID 列表与匹配详情。用于规则配置后的效果验证。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmTagRuleTestDto {

    /** 规则 ID */
    @NotNull(message = "规则 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;

    /** 待测试客户 ID 列表 */
    @NotEmpty(message = "待测试客户 ID 列表不能为空")
    private List<@NotNull(message = "客户 ID 不能为空") Long> customerIds;
}
