/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingTriggerDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 触发式自动营销规则 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmMarketingTriggerDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 触发器名称 */
    @NotBlank(message = "触发器名称不能为空")
    @Size(max = 200, message = "触发器名称长度不能超过 200")
    private String triggerName;

    /** 描述 */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /**
     * 事件类型: CUSTOMER_ADDED / CUSTOMER_TAGGED / LIFECYCLE_CHANGED /
     * CONVERSATION_STARTED / OPPORTUNITY_STAGE_CHANGED / MASS_SEND_COMPLETED /
     * CART_ABANDONED / INTERACTION_TIMEOUT / BIRTHDAY / ANNIVERSARY
     */
    @NotBlank(message = "事件类型不能为空")
    @Size(max = 50, message = "事件类型长度不能超过 50")
    @Pattern(regexp = "CUSTOMER_ADDED|CUSTOMER_TAGGED|LIFECYCLE_CHANGED|CONVERSATION_STARTED|"
            + "OPPORTUNITY_STAGE_CHANGED|MASS_SEND_COMPLETED|CART_ABANDONED|INTERACTION_TIMEOUT|"
            + "BIRTHDAY|ANNIVERSARY",
            message = "事件类型非法")
    private String eventType;

    /** 触发条件 JSON (如 {platformType:"wework", tagIds:[], lifecycle:""}) */
    private String eventCondition;

    /** 条件类型: ALL(全部满足) / ANY(任一满足) */
    @Size(max = 20, message = "条件类型长度不能超过 20")
    @Pattern(regexp = "ALL|ANY", message = "条件类型仅支持 ALL/ANY")
    private String conditionType;

    /** 同一客户冷却期小时数, 0=不限 */
    @PositiveOrZero(message = "冷却期小时数必须 >= 0")
    private Integer cooldownHours;

    /** 每客户最大触发次数, 0=不限 */
    @PositiveOrZero(message = "每客户最大触发次数必须 >= 0")
    private Integer maxTriggersPerCustomer;

    /**
     * 动作类型: SEND_MESSAGE / ADD_TAG / SET_LIFECYCLE /
     * ENROLL_JOURNEY / NOTIFY_USER / TRIGGER_MASS_SEND
     */
    @NotBlank(message = "动作类型不能为空")
    @Size(max = 30, message = "动作类型长度不能超过 30")
    @Pattern(regexp = "SEND_MESSAGE|ADD_TAG|SET_LIFECYCLE|ENROLL_JOURNEY|NOTIFY_USER|TRIGGER_MASS_SEND",
            message = "动作类型仅支持 SEND_MESSAGE/ADD_TAG/SET_LIFECYCLE/ENROLL_JOURNEY/NOTIFY_USER/TRIGGER_MASS_SEND")
    private String actionType;

    /** 动作参数 JSON: {messageTemplateId, tagIds, lifecycle, journeyId, notifyUserId, massSendTaskId} */
    @NotBlank(message = "动作参数不能为空")
    private String actionParams;

    /** 延迟执行分钟数 */
    @PositiveOrZero(message = "延迟执行分钟数必须 >= 0")
    private Integer actionDelayMinutes;

    /** 优先级 */
    private Integer priority;

    /** 是否启用 */
    private Boolean enabled;

    /** 已触发次数 */
    private Integer triggerCount;

    /** 最近触发时间 */
    private LocalDateTime lastTriggerAt;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
