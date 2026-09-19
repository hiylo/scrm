/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowNodeLogEntity.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 营销自动化工作流节点执行日志实体。
 * <p>
 * 描述工作流实例中单个节点的执行明细: 节点信息 ({@link #nodeId} / {@link #nodeName} /
 * {@link #nodeType})、动作 ({@link #actionType} / {@link #actionConfig})、输入输出
 * ({@link #inputVariables} / {@link #outputResult})、执行结果 ({@link #status} /
 * {@link #startedAt} / {@link #completedAt} / {@link #durationMs} / {@link #errorMessage})、
 * 条件结果 ({@link #conditionResult}) 与执行顺序 ({@link #sequence})。
 * </p>
 * <p>
 * 节点类型: START / END / ACTION / CONDITION / DELAY / LOOP / SWITCH / PARALLEL / WAIT /
 * SUB_WORKFLOW。
 * 动作类型: SEND_MESSAGE / SEND_EMAIL / SEND_SMS / ADD_TAG / REMOVE_TAG / UPDATE_FIELD /
 * CREATE_TASK / NOTIFY / WEBHOOK / CALL_API / ADD_TO_SEGMENT / REMOVE_FROM_SEGMENT /
 * ASSIGN_OWNER / CREATE_TICKET。
 * 状态: PENDING / RUNNING / SUCCESS / FAILED / SKIPPED / WAITING。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_workflow_node_log", schema = "scrm", indexes = {
        @Index(name = "idx_wf_nodelog_instance", columnList = "instance_id"),
        @Index(name = "idx_wf_nodelog_workflow", columnList = "workflow_id"),
        @Index(name = "idx_wf_nodelog_status", columnList = "status"),
        @Index(name = "idx_wf_nodelog_node", columnList = "workflow_id,node_id")
})
@Data
public class ScrmWorkflowNodeLogEntity {

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

    /** 工作流实例 ID */
    @Column(name = "instance_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long instanceId;

    /** 工作流 ID */
    @Column(name = "workflow_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workflowId;

    /** 节点 ID */
    @Column(name = "node_id", nullable = false, length = 100)
    private String nodeId;

    /** 节点名称 (可空) */
    @Column(name = "node_name", length = 200)
    private String nodeName;

    /** 节点类型: START/END/ACTION/CONDITION/DELAY/LOOP/SWITCH/PARALLEL/WAIT/SUB_WORKFLOW */
    @Column(name = "node_type", nullable = false, length = 50)
    private String nodeType;

    /** 动作类型 (可空): SEND_MESSAGE/SEND_EMAIL/SEND_SMS/ADD_TAG/REMOVE_TAG/UPDATE_FIELD/CREATE_TASK/NOTIFY/WEBHOOK/CALL_API/ADD_TO_SEGMENT/REMOVE_FROM_SEGMENT/ASSIGN_OWNER/CREATE_TICKET */
    @Column(name = "action_type", length = 50)
    private String actionType;

    /** 动作配置 JSON (可空) */
    @Column(name = "action_config", columnDefinition = "TEXT")
    private String actionConfig;

    /** 输入变量 JSON (可空) */
    @Column(name = "input_variables", columnDefinition = "TEXT")
    private String inputVariables;

    /** 输出结果 JSON (可空) */
    @Column(name = "output_result", columnDefinition = "TEXT")
    private String outputResult;

    /** 状态: PENDING/RUNNING/SUCCESS/FAILED/SKIPPED/WAITING (默认 PENDING) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 开始时间 (可空) */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 完成时间 (可空) */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 执行耗时 (毫秒, 可空) */
    @Column(name = "duration_ms")
    private Integer durationMs;

    /** 错误信息 (可空) */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /** 条件结果 (可空): TRUE/FALSE */
    @Column(name = "condition_result", length = 20)
    private String conditionResult;

    /** 重试次数 (默认 0) */
    @Column(name = "retry_count")
    private Integer retryCount;

    /** 执行顺序 (默认 0, 同实例内递增) */
    @Column(name = "sequence", nullable = false)
    private Integer sequence;
}
