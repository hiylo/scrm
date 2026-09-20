/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalFlowEntity.java
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
 * SCRM 审批工作流定义实体。
 * <p>
 * 描述一条通用审批流程定义: 基本信息 ({@link #flowName} / {@link #flowCode} /
 * {@link #description} / {@link #flowType})、节点图 ({@link #nodes} /
 * {@link #conditionRules} / {@link #startNode} / {@link #endNodes})、
 * 流程控制 ({@link #status} / {@link #isDefault} / {@link #version} /
 * {@link #usageCount} / {@link #lastUsedAt}) 与可执行能力
 * ({@link #approverFallback} / {@link #allowDelegation} / {@link #allowCountersign} /
 * {@link #allowUrgent} / {@link #maxDurationDays})。
 * </p>
 * <p>
 * 流程类型: CONTRACT / EXPENSE / LEAVE / REFUND / DISCOUNT / PRICE_CHANGE /
 * CUSTOMER_MERGE / CONTENT / PURCHASE / OTHER / CUSTOM。
 * 状态: ACTIVE / INACTIVE / DRAFT。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_approval_flow", schema = "scrm", indexes = {
        @Index(name = "idx_approval_flow_code", columnList = "flow_code", unique = true),
        @Index(name = "idx_approval_flow_type", columnList = "flow_type"),
        @Index(name = "idx_approval_flow_status", columnList = "status"),
        @Index(name = "idx_approval_flow_default", columnList = "is_default")
})
@Data
public class ScrmApprovalFlowEntity {

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

    /** 流程名称 */
    @Column(name = "flow_name", nullable = false, length = 200)
    private String flowName;

    /** 流程编码 (全局唯一) */
    @Column(name = "flow_code", nullable = false, length = 50)
    private String flowCode;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 流程类型: CONTRACT/EXPENSE/LEAVE/REFUND/DISCOUNT/PRICE_CHANGE/CUSTOMER_MERGE/CONTENT/PURCHASE/OTHER/CUSTOM */
    @Column(name = "flow_type", nullable = false, length = 30)
    private String flowType;

    /** 适用模块 (可空) */
    @Column(name = "applicable_module", length = 100)
    private String applicableModule;

/** 节点定义 JSON:
         * [{nodeId,nodeName,nodeType,approverType,approverIds,ccUserIds,condition,actions,autoApprove
             ,timeoutHours,order}]
         * */
    @Column(name = "nodes", nullable = false, columnDefinition = "TEXT")
    private String nodes;

    /** 条件路由规则 JSON: [{nodeId,conditions,routes:[{toNode,condition}]}] (可空) */
    @Column(name = "condition_rules", columnDefinition = "TEXT")
    private String conditionRules;

    /** 起始节点 ID (可空) */
    @Column(name = "start_node", length = 100)
    private String startNode;

    /** 结束节点 ID 列表 (可空, 逗号分隔) */
    @Column(name = "end_nodes", length = 500)
    private String endNodes;

    /** 流程版本号 (默认 1) */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /** 状态: ACTIVE/INACTIVE/DRAFT (默认 ACTIVE) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 是否为默认流程 (默认 FALSE) */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    /** 使用次数 (默认 0) */
    @Column(name = "usage_count")
    private Integer usageCount;

    /** 最近使用时间 (可空) */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** 审批人缺失时的备选 (可空, 逗号分隔用户 ID) */
    @Column(name = "approver_fallback", length = 200)
    private String approverFallback;

    /** 允许转交 (默认 TRUE) */
    @Column(name = "allow_delegation", nullable = false)
    private Boolean allowDelegation;

    /** 允许加签 (默认 FALSE) */
    @Column(name = "allow_countersign", nullable = false)
    private Boolean allowCountersign;

    /** 允许加急 (默认 TRUE) */
    @Column(name = "allow_urgent", nullable = false)
    private Boolean allowUrgent;

    /** 最大审批时长 (天, 默认 30) */
    @Column(name = "max_duration_days")
    private Integer maxDurationDays;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
