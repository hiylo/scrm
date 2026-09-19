/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyTestDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SCRM 消息自动回复规则匹配测试 DTO。
 * <p>
 * 用于规则配置后的效果验证: 传入 ruleId 与测试消息, 由
 * {@code ScrmAutoReplyService.testMatch} 评估规则是否命中并返回匹配详情
 * (匹配结果、匹配关键词、匹配分数), 不实际发送回复、不记录日志。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAutoReplyTestDto {

    /** 规则 ID */
    @NotNull(message = "规则 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;

    /** 测试消息 */
    @NotBlank(message = "测试消息不能为空")
    private String testMessage;
}
