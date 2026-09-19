/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 营销自动化工作流 DTO。
 * <p>
 * 对应 {@code ScrmWorkflowEntity} 的业务字段, 创建/更新接口入参。
 * workflowType / triggerType / status 以枚举字符串校验合法性;
 * priority / cooldownHours / maxConcurrentInstances 缺省时由服务端填充默认值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmWorkflowDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 工作流名称 */
    @NotBlank(message = "工作流名称不能为空")
    @Size(max = 200, message = "工作流名称长度不能超过 200")
    private String workflowName;

    /** 工作流编码 (全局唯一) */
    @NotBlank(message = "工作流编码不能为空")
    @Size(max = 50, message = "工作流编码长度不能超过 50")
    private String workflowCode;

    /** 工作流描述 (可空) */
    @Size(max = 500, message = "工作流描述长度不能超过 500")
    private String description;

    /** 工作流类型: MARKETING/ONBOARDING/RETENTION/RE_ENGAGEMENT/POST_PURCHASE/ABANDONED_CART/BIRTHDAY/ANNIVERSARY/CUSTOM */
    @NotBlank(message = "工作流类型不能为空")
    @Pattern(regexp =
            "MARKETING|ONBOARDING|RETENTION|RE_ENGAGEMENT|POST_PURCHASE|ABANDONED_CART|BIRTHDAY|ANNIVERSARY|CUSTOM",
            message = "工作流类型仅支持 MARKETING/ONBOARDING/RETENTION/RE_ENGAGEMENT/POST_PURCHASE/ABANDONED_CART/BIRTHDAY/ANNIVERSARY/CUSTOM")
    private String workflowType;

    /** 触发器类型: EVENT/SCHEDULE/SEGMENT/WEBHOOK/MANUAL */
    @NotBlank(message = "触发器类型不能为空")
    @Pattern(regexp = "EVENT|SCHEDULE|SEGMENT|WEBHOOK|MANUAL",
            message = "触发器类型仅支持 EVENT/SCHEDULE/SEGMENT/WEBHOOK/MANUAL")
    private String triggerType;

    /** 触发器配置 JSON: {event,schedule,cron,segmentId,webhookUrl} */
    @NotBlank(message = "触发器配置不能为空")
    private String triggerConfig;

    /** 节点定义 JSON: [{id,type,name,config,next}] */
    @NotBlank(message = "节点定义不能为空")
    private String nodes;

    /** 连接定义 JSON: [{from,to,condition}] (可空) */
    private String edges;

    /** 入口节点 ID (可空) */
    @Size(max = 100, message = "入口节点 ID 长度不能超过 100")
    private String entryNode;

    /** 状态: DRAFT/ACTIVE/PAUSED/ARCHIVED (查询返回) */
    @Pattern(regexp = "DRAFT|ACTIVE|PAUSED|ARCHIVED",
            message = "状态仅支持 DRAFT/ACTIVE/PAUSED/ARCHIVED")
    private String status;

    /** 发布版本号 (查询返回) */
    private Integer versionNumber;

    /** 优先级 (默认 0) */
    private Integer priority;

    /** 累计执行次数 (查询返回) */
    private Integer executionCount;

    /** 累计成功次数 (查询返回) */
    private Integer successCount;

    /** 累计失败次数 (查询返回) */
    private Integer failureCount;

    /** 活跃实例数 (查询返回) */
    private Integer activeInstanceCount;

    /** 平均执行耗时 (毫秒, 查询返回) */
    private Integer avgExecutionTimeMs;

    /** 最近触发时间 (查询返回) */
    private LocalDateTime lastTriggeredAt;

    /** 目标客群 (可空) */
    @Size(max = 500, message = "目标客群长度不能超过 500")
    private String targetSegment;

    /** 排除客群 (可空) */
    @Size(max = 500, message = "排除客群长度不能超过 500")
    private String exclusionSegment;

    /** 最大并发实例数 (默认 1000) */
    private Integer maxConcurrentInstances;

    /** 冷却时间 (小时, 默认 0) */
    private Integer cooldownHours;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
