/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowInstanceDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 营销自动化工作流执行实例 DTO。
 * <p>
 * 对应 {@code ScrmWorkflowInstanceEntity} 的业务字段, 用于实例查询与状态变更接口。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmWorkflowInstanceDto {

    /** 主键 ID (查询返回) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 工作流 ID */
    @NotNull(message = "工作流 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workflowId;

    /** 工作流名称 (查询返回) */
    private String workflowName;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 触发器类型: EVENT/SCHEDULE/SEGMENT/WEBHOOK/MANUAL */
    @Pattern(regexp = "EVENT|SCHEDULE|SEGMENT|WEBHOOK|MANUAL",
            message = "触发器类型仅支持 EVENT/SCHEDULE/SEGMENT/WEBHOOK/MANUAL")
    private String triggerType;

    /** 触发事件 (可空) */
    @Size(max = 200, message = "触发事件长度不能超过 200")
    private String triggerEvent;

    /** 触发数据 JSON (可空) */
    private String triggerData;

    /** 当前节点 ID (查询返回) */
    private String currentNodeId;

    /** 当前节点名称 (查询返回) */
    private String currentNodeName;

    /** 当前节点类型 (查询返回) */
    private String currentNodeType;

    /** 状态: RUNNING/PAUSED/COMPLETED/FAILED/CANCELLED/WAITING (查询返回) */
    @Pattern(regexp = "RUNNING|PAUSED|COMPLETED|FAILED|CANCELLED|WAITING",
            message = "状态仅支持 RUNNING/PAUSED/COMPLETED/FAILED/CANCELLED/WAITING")
    private String status;

    /** 开始执行时间 (查询返回) */
    private LocalDateTime startedAt;

    /** 完成时间 (查询返回) */
    private LocalDateTime completedAt;

    /** 执行耗时 (毫秒, 查询返回) */
    private Integer durationMs;

    /** 执行日志 JSON (查询返回) */
    private String executionLog;

    /** 工作流变量 JSON (查询返回) */
    private String variables;

    /** 错误信息 (查询返回) */
    private String errorMessage;

    /** 重试次数 (查询返回) */
    private Integer retryCount;

    /** 下次执行时间 (查询返回, 用于延迟节点) */
    private LocalDateTime nextExecutionAt;

    /** 优先级 (查询返回) */
    private Integer priority;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
