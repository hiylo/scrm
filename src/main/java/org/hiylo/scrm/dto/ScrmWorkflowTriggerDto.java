/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowTriggerDto.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 营销自动化工作流触发参数 DTO。
 * <p>
 * 触发工作流执行接口入参: 指定工作流 ID 与客户 ID, 可携带触发事件与触发数据。
 * 服务端据此创建执行实例并按节点图流转。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmWorkflowTriggerDto {

    /** 工作流 ID */
    @NotNull(message = "工作流 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workflowId;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 触发事件 (可空, 覆盖工作流默认触发事件) */
    @Size(max = 200, message = "触发事件长度不能超过 200")
    private String triggerEvent;

    /** 触发数据 JSON (可空, 注入工作流变量) */
    private String triggerData;
}
