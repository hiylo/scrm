/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderAssignDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 工单分配请求 DTO。
 * <p>
 * 用于将工单分配给处理人或处理部门。assigneeId 与 department 至少传其一;
 * assigneeName 可选, 缺省由服务端原值保留。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmWorkOrderAssignDto {

    /** 工单 ID */
    @NotNull(message = "工单 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 处理人 ID (与 department 至少传其一) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long assigneeId;

    /** 处理人名称 (可空) */
    @Size(max = 100, message = "处理人名称长度不能超过 100")
    private String assigneeName;

    /** 处理部门 (与 assigneeId 至少传其一) */
    @Size(max = 100, message = "处理部门长度不能超过 100")
    private String department;
}
