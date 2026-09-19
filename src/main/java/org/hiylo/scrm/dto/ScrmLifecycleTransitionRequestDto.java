/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLifecycleTransitionRequestDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 客户生命周期流转请求 DTO。
 * <p>
 * 客户生命周期管理模块统一的阶段流转入参: 携带 {@code customerId} 与目标阶段编码
 * {@code toStage}, 以 {@code trigger} 描述触发原因 (PURCHASE/INACTIVITY/ENGAGEMENT/
 * COMPLAINT/MANUAL/RULE/SYSTEM), {@code notes} 承载补充备注, 由
 * {@code ScrmCustomerLifecycleService.transitionCustomer} 校验后执行流转。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmLifecycleTransitionRequestDto {

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 目标阶段编码 */
    @NotBlank(message = "目标阶段不能为空")
    @Size(max = 50, message = "目标阶段编码长度不能超过 50")
    private String toStage;

    /** 触发原因: PURCHASE/INACTIVITY/ENGAGEMENT/COMPLAINT/MANUAL/RULE/SYSTEM */
    @NotBlank(message = "触发原因不能为空")
    @Size(max = 200, message = "触发原因长度不能超过 200")
    private String trigger;

    /** 补充备注 (可空) */
    @Size(max = 1000, message = "备注长度不能超过 1000")
    private String notes;

    /** 操作人 ID (可空, 用于审计) */
    @Size(max = 100, message = "操作人 ID 长度不能超过 100")
    private String operatorId;

    /** 操作人名称 (可空, 用于审计) */
    @Size(max = 100, message = "操作人名称长度不能超过 100")
    private String operatorName;
}
