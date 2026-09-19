/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTriggerFireDto.java
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

/**
 * SCRM 触发式营销事件触发入参 DTO。
 * <p>
 * 外部系统或定时任务通过此 DTO 投递事件, 由服务匹配启用规则并生成事件记录。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmTriggerFireDto {

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
    /** 事件类型 */
    private String eventType;

    /** 触发客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户昵称（可空, 用于事件记录展示） */
    @Size(max = 200, message = "客户昵称长度不能超过 200")
    private String customerNickname;

    /** 事件数据 JSON（可空, 携带触发上下文, 如 {platformType, tagIds, lifecycle, ...}） */
    private String eventData;
}
