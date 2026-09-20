/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLifecycleBulkTransitionDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * SCRM 客户阶段批量转换 DTO。
 * <p>
 * 批量将多个客户转换到同一目标阶段, 内部循环调用单客户转换逻辑,
 * 命中转换规则后统一记录历史与统计。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmLifecycleBulkTransitionDto {

    /** 客户 ID 列表 */
    @NotEmpty(message = "客户 ID 列表不能为空")
    private List<Long> customerIds;

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
}
