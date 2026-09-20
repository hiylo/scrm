/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCareRuleDto.java
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
 * SCRM 客户关怀规则 DTO。
 * <p>
 * 对应 {@code ScrmCareRuleEntity} 的业务字段, 创建/更新接口入参。
 * triggerCondition 为 JSON 字符串: {@code {daysBefore:3, time:"09:00", segment:"VIP"}},
 * 描述触发条件; actionContent 为 JSON 字符串:
 * {@code {messageTemplateId, couponTemplateId, giftId}}, 描述动作内容。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCareRuleDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 关怀类型: BIRTHDAY / FESTIVAL / ANNIVERSARY / MEMBERSHIP_EXPIRY / INACTIVITY_REMINDER / CUSTOM */
    @NotBlank(message = "关怀类型不能为空")
    @Pattern(regexp = "BIRTHDAY|FESTIVAL|ANNIVERSARY|MEMBERSHIP_EXPIRY|INACTIVITY_REMINDER|CUSTOM",
            message = "关怀类型仅支持 BIRTHDAY/FESTIVAL/ANNIVERSARY/MEMBERSHIP_EXPIRY/INACTIVITY_REMINDER/CUSTOM")
    private String careType;

    /** 规则描述 (可空) */
    @Size(max = 500, message = "规则描述长度不能超过 500")
    private String description;

    /** 触发条件 JSON: {daysBefore, time, segment} */
    @NotBlank(message = "触发条件不能为空")
    private String triggerCondition;

    /** 关怀动作: SEND_MESSAGE / SEND_COUPON / SEND_GIFT / CALL / CREATE_TASK / NOTIFY_ASSIGNEE */
    @NotBlank(message = "关怀动作不能为空")
    @Pattern(regexp = "SEND_MESSAGE|SEND_COUPON|SEND_GIFT|CALL|CREATE_TASK|NOTIFY_ASSIGNEE",
            message = "关怀动作仅支持 SEND_MESSAGE/SEND_COUPON/SEND_GIFT/CALL/CREATE_TASK/NOTIFY_ASSIGNEE")
    private String actionType;

    /** 动作内容 JSON: {messageTemplateId, couponTemplateId, giftId} */
    @NotBlank(message = "动作内容不能为空")
    private String actionContent;

    /** 优先级 (数字越小越优先, 默认 0) */
    private Integer priority;

    /** 适用客群 (可空, 逗号分隔) */
    @Size(max = 500, message = "适用客群长度不能超过 500")
    private String applicableSegments;

    /** 适用等级 (可空, 逗号分隔) */
    @Size(max = 200, message = "适用等级长度不能超过 200")
    private String applicableLevels;

    /** 是否启用 (创建时可选, 默认 true) */
    private Boolean enabled;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 执行次数 (查询返回) */
    private Integer executionCount;

    /** 最近执行时间 (查询返回) */
    private LocalDateTime lastExecutedAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
