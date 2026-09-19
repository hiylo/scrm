/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingTriggerEventDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 触发式营销事件记录 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmMarketingTriggerEventDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 触发器 ID */
    @NotNull(message = "触发器 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long triggerId;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户昵称 */
    @Size(max = 200, message = "客户昵称长度不能超过 200")
    private String customerNickname;

    /** 事件类型 */
    @NotBlank(message = "事件类型不能为空")
    @Size(max = 50, message = "事件类型长度不能超过 50")
    private String eventType;

    /** 事件数据 JSON */
    private String eventData;

    /** 状态: PENDING / EXECUTING / SUCCESS / FAILED / SKIPPED / COOLDOWN */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "PENDING|EXECUTING|SUCCESS|FAILED|SKIPPED|COOLDOWN",
            message = "状态仅支持 PENDING/EXECUTING/SUCCESS/FAILED/SKIPPED/COOLDOWN")
    private String status;

    /** 动作类型 */
    @NotBlank(message = "动作类型不能为空")
    @Size(max = 30, message = "动作类型长度不能超过 30")
    private String actionType;

    /** 动作执行结果 */
    @Size(max = 500, message = "动作执行结果长度不能超过 500")
    private String actionResult;

    /** 错误信息 */
    @Size(max = 500, message = "错误信息长度不能超过 500")
    private String errorMessage;

    /** 计划执行时间 */
    private LocalDateTime scheduledAt;

    /** 实际执行时间 */
    private LocalDateTime executedAt;

    /** 该客户已触发次数 */
    private Integer triggerCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
