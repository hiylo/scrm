/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoTagRuleLogDto.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 客户自动标签规则执行日志 DTO。
 * <p>
 * 对应 {@code ScrmAutoTagRuleLogEntity} 的业务字段, 用于日志查询接口的响应结构与
 * 内部日志写入参数传递。matchedConditions 为命中条件的 JSON 详情, actionResult
 * 取值 SUCCESS / FAILED / SKIPPED。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAutoTagRuleLogDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 规则 ID (引用 scrm_auto_tag_rule.id) */
    @NotNull(message = "规则 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;

    /** 客户 ID (引用 scrm_customer.id) */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户昵称 (执行时快照) */
    @Size(max = 200, message = "客户昵称长度不能超过 200")
    private String customerNickname;

    /** 触发事件 */
    @NotBlank(message = "触发事件不能为空")
    @Size(max = 50, message = "触发事件长度不能超过 50")
    private String triggerEvent;

    /** 匹配的条件详情 JSON */
    private String matchedConditions;

    /** 动作类型: ADD_TAG / REMOVE_TAG / SET_LIFECYCLE / NOTIFY */
    @NotBlank(message = "动作类型不能为空")
    @Size(max = 20, message = "动作类型长度不能超过 20")
    private String actionType;

    /** 动作结果: SUCCESS / FAILED / SKIPPED */
    @NotBlank(message = "动作结果不能为空")
    @Size(max = 20, message = "动作结果长度不能超过 20")
    @Pattern(regexp = "SUCCESS|FAILED|SKIPPED",
            message = "动作结果仅支持 SUCCESS/FAILED/SKIPPED")
    private String actionResult;

    /** 动作详情 */
    @Size(max = 500, message = "动作详情长度不能超过 500")
    private String actionDetail;

    /** 执行时间 */
    private LocalDateTime executedAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
