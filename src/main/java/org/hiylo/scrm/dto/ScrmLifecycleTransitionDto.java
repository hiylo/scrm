/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLifecycleTransitionDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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
 * SCRM 客户生命周期阶段流转规则 DTO。
 * <p>
 * 对应 {@code ScrmLifecycleTransitionEntity} 的业务字段, 创建/更新接口入参。
 * transitionType 以枚举字符串校验合法性; fromStageId 可空表示新客户进入;
 * priority / cooldownDays / isEnabled 缺省时由服务端填充默认值。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmLifecycleTransitionDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 源阶段 ID (可空, null 表示新客户进入) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromStageId;

    /** 源阶段编码 (可空) */
    @Size(max = 50, message = "源阶段编码长度不能超过 50")
    private String fromStageCode;

    /** 目标阶段 ID */
    @NotNull(message = "目标阶段 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toStageId;

    /** 目标阶段编码 */
    @NotBlank(message = "目标阶段编码不能为空")
    @Size(max = 50, message = "目标阶段编码长度不能超过 50")
    private String toStageCode;

    /** 转换名称 */
    @NotBlank(message = "转换名称不能为空")
    @Size(max = 200, message = "转换名称长度不能超过 200")
    private String transitionName;

    /** 转换类型: AUTO/MANUAL/SYSTEM (默认 AUTO) */
    @Pattern(regexp = "AUTO|MANUAL|SYSTEM", message = "转换类型仅支持 AUTO/MANUAL/SYSTEM")
    private String transitionType;

    /** 触发条件 JSON (可空) */
    private String triggerCondition;

    /** 触发事件逗号分隔: PURCHASE/LOGIN/INACTIVE_DAYS/FIRST_CONTACT/REFUND/CUSTOM (可空) */
    @Size(max = 500, message = "触发事件长度不能超过 500")
    private String triggerEvents;

    /** 优先级 (值越大越优先, 默认 0) */
    private Integer priority;

    /** 冷却天数 (默认 0, 防止短期重复转换) */
    private Integer cooldownDays;

    /** 是否启用 (默认 TRUE) */
    private Boolean isEnabled;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 触发次数 (查询返回) */
    private Integer triggerCount;

    /** 最近触发时间 (查询返回) */
    private LocalDateTime lastTriggeredAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
