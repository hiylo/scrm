/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLifecycleTransitionActionDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 客户阶段转换动作 DTO。
 * <p>
 * 单客户阶段转换接口入参: 指定目标阶段编码 + 触发事件 + 描述,
 * 服务端校验转换规则合法性后执行转换 (验证转换规则 → 更新当前阶段 → 记录历史 → 更新统计)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmLifecycleTransitionActionDto {

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空, 用于历史记录冗余) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 目标阶段编码 */
    @NotBlank(message = "目标阶段编码不能为空")
    @Size(max = 50, message = "目标阶段编码长度不能超过 50")
    private String toStageCode;

    /** 触发事件 (可空): PURCHASE/LOGIN/INACTIVE_DAYS/FIRST_CONTACT/REFUND/CUSTOM */
    @Size(max = 100, message = "触发事件长度不能超过 100")
    private String triggerEvent;

    /** 触发描述 (可空) */
    @Size(max = 500, message = "触发描述长度不能超过 500")
    private String description;

    /** 操作人 ID (可空) */
    @Size(max = 100, message = "操作人 ID 长度不能超过 100")
    private String operatorId;

    /** 操作人名称 (可空) */
    @Size(max = 100, message = "操作人名称长度不能超过 100")
    private String operatorName;

    /** 附加数据 JSON (可空) */
    private String metadata;
}
