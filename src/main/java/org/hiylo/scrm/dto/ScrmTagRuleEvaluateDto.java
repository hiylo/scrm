/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagRuleEvaluateDto.java
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

import java.util.Map;

/**
 * SCRM 客户自动标签规则评估 DTO。
 * <p>
 * 手动评估接口入参, 携带待评估客户 ID、触发事件与客户上下文。customerContext 为
 * 客户属性快照 (如 lifecycle / platformType / lastInteractionDays / tags / ownerAccountId),
 * Service 层按 conditions 中的 field 从中取值, 按 operator 进行条件匹配。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmTagRuleEvaluateDto {

    /** 待评估客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

/** 触发事件: CUSTOMER_CREATED / CUSTOMER_UPDATED / MESSAGE_RECEIVED / LIFECYCLE_CHANGED / TAG_ADDED /
         * INTERACTION_TIMEOUT */
    @NotBlank(message = "触发事件不能为空")
    @Size(max = 50, message = "触发事件长度不能超过 50")
    @Pattern(regexp =
            "CUSTOMER_CREATED|CUSTOMER_UPDATED|MESSAGE_RECEIVED|LIFECYCLE_CHANGED|TAG_ADDED|INTERACTION_TIMEOUT",
            message = "触发事件仅支持 CUSTOMER_CREATED/CUSTOMER_UPDATED/MESSAGE_RECEIVED/LIFECYCLE_CHANGED/TAG_ADDED/INTERACTION_TIMEOUT")
    private String triggerEvent;

    /**
     * 客户上下文 (客户属性快照)。
     * <p>
     * 键为字段名 (如 lifecycle / platformType / lastInteractionDays / tags / ownerAccountId),
     * 值为对应取值。tags 字段值为标签键集合 (List&lt;String&gt;); lastInteractionDays 为整数;
     * 其他字段为字符串。由调用方在调用评估前构建, Service 层不主动查询客户实体。
     * </p>
     */
    private Map<String, Object> customerContext;
}
