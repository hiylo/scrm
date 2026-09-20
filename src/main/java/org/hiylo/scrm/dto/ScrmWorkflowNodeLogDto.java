/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowNodeLogDto.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 营销自动化工作流节点执行日志 DTO。
 * <p>
 * 对应 {@code ScrmWorkflowNodeLogEntity} 的业务字段, 用于节点日志查询接口。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmWorkflowNodeLogDto {

    /** 主键 ID (查询返回) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 工作流实例 ID */
    @NotNull(message = "工作流实例 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long instanceId;

    /** 工作流 ID */
    @NotNull(message = "工作流 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workflowId;

    /** 节点 ID */
    @NotBlank(message = "节点 ID 不能为空")
    @Size(max = 100, message = "节点 ID 长度不能超过 100")
    private String nodeId;

    /** 节点名称 (可空) */
    @Size(max = 200, message = "节点名称长度不能超过 200")
    private String nodeName;

    /** 节点类型: START/END/ACTION/CONDITION/DELAY/LOOP/SWITCH/PARALLEL/WAIT/SUB_WORKFLOW */
    @NotBlank(message = "节点类型不能为空")
    @Pattern(regexp = "START|END|ACTION|CONDITION|DELAY|LOOP|SWITCH|PARALLEL|WAIT|SUB_WORKFLOW",
            message = "节点类型仅支持 START/END/ACTION/CONDITION/DELAY/LOOP/SWITCH/PARALLEL/WAIT/SUB_WORKFLOW")
    private String nodeType;

    /** 动作类型 (可空): SEND_MESSAGE/SEND_EMAIL/SEND_SMS/ADD_TAG/REMOVE_TAG/UPDATE_FIELD/CREATE_TASK/NOTIFY/WEBHOOK/CALL_API/ADD_TO_SEGMENT/REMOVE_FROM_SEGMENT/ASSIGN_OWNER/CREATE_TICKET */
    @Pattern(regexp = "SEND_MESSAGE|SEND_EMAIL|SEND_SMS|ADD_TAG|REMOVE_TAG|UPDATE_FIELD|CREATE_TASK|NOTIFY|WEBHOOK|CALL_API|ADD_TO_SEGMENT|REMOVE_FROM_SEGMENT|ASSIGN_OWNER|CREATE_TICKET|",
            message = "动作类型非法")
    private String actionType;

    /** 动作配置 JSON (可空) */
    private String actionConfig;

    /** 输入变量 JSON (查询返回) */
    private String inputVariables;

    /** 输出结果 JSON (查询返回) */
    private String outputResult;

    /** 状态: PENDING/RUNNING/SUCCESS/FAILED/SKIPPED/WAITING (查询返回) */
    @Pattern(regexp = "PENDING|RUNNING|SUCCESS|FAILED|SKIPPED|WAITING",
            message = "状态仅支持 PENDING/RUNNING/SUCCESS/FAILED/SKIPPED/WAITING")
    private String status;

    /** 开始时间 (查询返回) */
    private LocalDateTime startedAt;

    /** 完成时间 (查询返回) */
    private LocalDateTime completedAt;

    /** 执行耗时 (毫秒, 查询返回) */
    private Integer durationMs;

    /** 错误信息 (查询返回) */
    private String errorMessage;

    /** 条件结果 (查询返回): TRUE/FALSE */
    @Pattern(regexp = "TRUE|FALSE|", message = "条件结果仅支持 TRUE/FALSE")
    private String conditionResult;

    /** 重试次数 (查询返回) */
    private Integer retryCount;

    /** 执行顺序 (默认 0) */
    private Integer sequence;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
