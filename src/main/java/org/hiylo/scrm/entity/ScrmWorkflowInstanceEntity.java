/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowInstanceEntity.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 营销自动化工作流执行实例实体。
 * <p>
 * 描述一次工作流执行的运行时状态: 关联工作流与客户 ({@link #workflowId} /
 * {@link #customerId})、触发来源 ({@link #triggerType} / {@link #triggerEvent} /
 * {@link #triggerData})、当前节点 ({@link #currentNodeId} / {@link #currentNodeName} /
 * {@link #currentNodeType})、执行进度 ({@link #status} / {@link #startedAt} /
 * {@link #completedAt} / {@link #durationMs})、执行日志 ({@link #executionLog}) 与
 * 工作流变量 ({@link #variables})。
 * </p>
 * <p>
 * 状态流转: RUNNING (执行中) → PAUSED (已暂停) / WAITING (等待中, 延迟节点) /
 * COMPLETED (已完成) / FAILED (已失败) / CANCELLED (已取消)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_workflow_instance", schema = "scrm", indexes = {
        @Index(name = "idx_wf_instance_workflow", columnList = "workflow_id"),
        @Index(name = "idx_wf_instance_customer", columnList = "customer_id"),
        @Index(name = "idx_wf_instance_status", columnList = "status"),
        @Index(name = "idx_wf_instance_next_exec", columnList = "next_execution_at")
})
@Data
public class ScrmWorkflowInstanceEntity {

    // ==================== 公共字段 ====================

    /** 主键 ID（Snowflake 雪花算法生成） */
    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", strategy = "org.hiylo.scrm.config.SnowflakeIdGenerator")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 创建时间 */
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /** 乐观锁版本号（并发更新保护, 后写入者触发 OptimisticLockException） */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 持久化前回调: 自动填充创建/更新时间与版本号初值
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
    }

    /**
     * 更新前回调: 自动刷新更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // ==================== 业务字段 ====================

    /** 工作流 ID */
    @Column(name = "workflow_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workflowId;

    /** 工作流名称 (可空, 触发时快照) */
    @Column(name = "workflow_name", length = 200)
    private String workflowName;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 触发器类型: EVENT/SCHEDULE/SEGMENT/WEBHOOK/MANUAL */
    @Column(name = "trigger_type", nullable = false, length = 30)
    private String triggerType;

    /** 触发事件 (可空) */
    @Column(name = "trigger_event", length = 200)
    private String triggerEvent;

    /** 触发数据 JSON (可空) */
    @Column(name = "trigger_data", columnDefinition = "TEXT")
    private String triggerData;

    /** 当前节点 ID (可空) */
    @Column(name = "current_node_id", length = 100)
    private String currentNodeId;

    /** 当前节点名称 (可空) */
    @Column(name = "current_node_name", length = 200)
    private String currentNodeName;

    /** 当前节点类型 (可空) */
    @Column(name = "current_node_type", length = 50)
    private String currentNodeType;

    /** 状态: RUNNING/PAUSED/COMPLETED/FAILED/CANCELLED/WAITING (默认 RUNNING) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 开始执行时间 */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** 完成时间 (可空) */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 执行耗时 (毫秒, 可空) */
    @Column(name = "duration_ms")
    private Integer durationMs;

    /** 执行日志 JSON: [{nodeId,nodeName,action,status,timestamp,duration}] (可空) */
    @Column(name = "execution_log", columnDefinition = "TEXT")
    private String executionLog;

    /** 工作流变量 JSON (可空) */
    @Column(name = "variables", columnDefinition = "TEXT")
    private String variables;

    /** 错误信息 (可空) */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /** 重试次数 (默认 0) */
    @Column(name = "retry_count")
    private Integer retryCount;

    /** 下次执行时间 (可空, 用于延迟节点) */
    @Column(name = "next_execution_at")
    private LocalDateTime nextExecutionAt;

    /** 优先级 (默认 0) */
    @Column(name = "priority")
    private Integer priority;
}
