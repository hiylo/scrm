/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowEntity.java
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
 * SCRM 营销自动化工作流实体。
 * <p>
 * 描述一条可视化营销自动化工作流定义: 触发器 ({@link #triggerType} /
 * {@link #triggerConfig})、节点图 ({@link #nodes} / {@link #edges} / {@link #entryNode})、
 * 生命周期 ({@link #status}) 与执行统计 ({@link #executionCount} / {@link #successCount} /
 * {@link #failureCount} / {@link #avgExecutionTimeMs})。
 * </p>
 * <p>
 * 状态流转: DRAFT (草稿) → ACTIVE (已激活) → PAUSED (已暂停) / ARCHIVED (已归档)。
 * 工作流类型支持 MARKETING / ONBOARDING / RETENTION / RE_ENGAGEMENT / POST_PURCHASE /
 * ABANDONED_CART / BIRTHDAY / ANNIVERSARY / CUSTOM。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_workflow", schema = "scrm", indexes = {
        @Index(name = "idx_workflow_code", columnList = "workflow_code", unique = true),
        @Index(name = "idx_workflow_type", columnList = "workflow_type"),
        @Index(name = "idx_workflow_trigger", columnList = "trigger_type"),
        @Index(name = "idx_workflow_status", columnList = "status")
})
@Data
public class ScrmWorkflowEntity {

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

    /** 工作流名称 */
    @Column(name = "workflow_name", nullable = false, length = 200)
    private String workflowName;

    /** 工作流编码 (全局唯一) */
    @Column(name = "workflow_code", nullable = false, length = 50)
    private String workflowCode;

    /** 工作流描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

/** 工作流类型: MARKETING/ONBOARDING/RETENTION/RE_ENGAGEMENT/POST_PURCHASE/ABANDONED_CART/BIRTHDAY/ANNIVERSARY/CUSTOM (默认
         * MARKETING) */
    @Column(name = "workflow_type", nullable = false, length = 30)
    private String workflowType;

    /** 触发器类型: EVENT/SCHEDULE/SEGMENT/WEBHOOK/MANUAL */
    @Column(name = "trigger_type", nullable = false, length = 30)
    private String triggerType;

    /** 触发器配置 JSON: {event,schedule,cron,segmentId,webhookUrl} */
    @Column(name = "trigger_config", nullable = false, columnDefinition = "TEXT")
    private String triggerConfig;

    /** 节点定义 JSON: [{id,type,name,config,next}] */
    @Column(name = "nodes", nullable = false, columnDefinition = "TEXT")
    private String nodes;

    /** 连接定义 JSON: [{from,to,condition}] (可空) */
    @Column(name = "edges", columnDefinition = "TEXT")
    private String edges;

    /** 入口节点 ID (可空) */
    @Column(name = "entry_node", length = 100)
    private String entryNode;

    /** 状态: DRAFT/ACTIVE/PAUSED/ARCHIVED (默认 DRAFT) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 工作流发布版本号 (默认 1, publishVersion 时递增) */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /** 优先级 (默认 0, 数值越大越优先) */
    @Column(name = "priority")
    private Integer priority;

    /** 累计执行次数 (默认 0) */
    @Column(name = "execution_count")
    private Integer executionCount;

    /** 累计成功次数 (默认 0) */
    @Column(name = "success_count")
    private Integer successCount;

    /** 累计失败次数 (默认 0) */
    @Column(name = "failure_count")
    private Integer failureCount;

    /** 活跃实例数 (默认 0) */
    @Column(name = "active_instance_count")
    private Integer activeInstanceCount;

    /** 平均执行耗时 (毫秒, 默认 0) */
    @Column(name = "avg_execution_time_ms")
    private Integer avgExecutionTimeMs;

    /** 最近触发时间 (可空) */
    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    /** 目标客群 (可空) */
    @Column(name = "target_segment", length = 500)
    private String targetSegment;

    /** 排除客群 (可空) */
    @Column(name = "exclusion_segment", length = 500)
    private String exclusionSegment;

    /** 最大并发实例数 (默认 1000) */
    @Column(name = "max_concurrent_instances", nullable = false)
    private Integer maxConcurrentInstances;

    /** 冷却时间 (小时, 默认 0) */
    @Column(name = "cooldown_hours")
    private Integer cooldownHours;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
