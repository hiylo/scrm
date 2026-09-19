/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoTagRuleDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 客户自动标签规则 DTO。
 * <p>
 * 对应 {@code ScrmAutoTagRuleEntity} 的业务字段, 不含公共字段 (id/createTime/
 * updateTime/version/matchCount/lastMatchAt)。创建/更新接口入参, 校验注解保证必填字段
 * 与取值约束。conditions 与 actionParams 为 JSON 字符串, 由 Service 层用 ObjectMapper 解析。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAutoTagRuleDto {

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 规则描述 */
    @Size(max = 500, message = "规则描述长度不能超过 500")
    private String description;

/** 触发事件: CUSTOMER_CREATED / CUSTOMER_UPDATED / MESSAGE_RECEIVED / LIFECYCLE_CHANGED / TAG_ADDED /
         * INTERACTION_TIMEOUT */
    @NotBlank(message = "触发事件不能为空")
    @Size(max = 50, message = "触发事件长度不能超过 50")
    @Pattern(regexp =
            "CUSTOMER_CREATED|CUSTOMER_UPDATED|MESSAGE_RECEIVED|LIFECYCLE_CHANGED|TAG_ADDED|INTERACTION_TIMEOUT",
            message = "触发事件仅支持 CUSTOMER_CREATED/CUSTOMER_UPDATED/MESSAGE_RECEIVED/LIFECYCLE_CHANGED/TAG_ADDED/INTERACTION_TIMEOUT")
    private String triggerEvent;

    /** 条件类型: ALL 所有条件满足 / ANY 任一满足 */
    @NotBlank(message = "条件类型不能为空")
    @Size(max = 20, message = "条件类型长度不能超过 20")
    @Pattern(regexp = "ALL|ANY", message = "条件类型仅支持 ALL/ANY")
    private String conditionType;

    /** 条件 JSON 数组: [{field, operator, value}] */
    @NotBlank(message = "条件 JSON 不能为空")
    private String conditions;

    /** 动作类型: ADD_TAG / REMOVE_TAG / SET_LIFECYCLE / NOTIFY */
    @NotBlank(message = "动作类型不能为空")
    @Size(max = 20, message = "动作类型长度不能超过 20")
    @Pattern(regexp = "ADD_TAG|REMOVE_TAG|SET_LIFECYCLE|NOTIFY",
            message = "动作类型仅支持 ADD_TAG/REMOVE_TAG/SET_LIFECYCLE/NOTIFY")
    private String actionType;

    /** 动作参数 JSON: {tagIds:[], lifecycle:"", notifyUserId:""} */
    @NotBlank(message = "动作参数不能为空")
    private String actionParams;

    /** 优先级（数字越小越优先, 默认 0） */
    private Integer priority;

    /** 是否启用（创建时可选, 默认 true） */
    private Boolean enabled;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
