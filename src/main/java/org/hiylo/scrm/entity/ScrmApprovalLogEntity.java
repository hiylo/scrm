/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalLogEntity.java
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
 * SCRM 审批操作日志实体。
 * <p>
 * 描述审批实例中单次操作的明细: 节点信息 ({@link #instanceId} / {@link #nodeId} /
 * {@link #nodeName} / {@link #nodeType})、操作 ({@link #actionType} /
 * {@link #operatorId} / {@link #operatorName} / {@link #operatorRole} /
 * {@link #operatorType})、内容 ({@link #comment} / {@link #actionData})、
 * 流转 ({@link #previousNodeId} / {@link #nextNodeId}) 与时间
 * ({@link #actedAt} / {@link #isAutoAction} / {@link #sequence})。
 * </p>
 * <p>
 * 操作类型: SUBMIT / APPROVE / REJECT / TRANSFER / COUNTERSIGN / CC /
 * WITHDRAW / RESUBMIT / TIMEOUT / URGE / COMMENT / AUTO_APPROVE。
 * 操作人类型: APPLICANT / APPROVER / CC / SYSTEM。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_approval_log", schema = "scrm", indexes = {
        @Index(name = "idx_approval_log_instance", columnList = "instance_id"),
        @Index(name = "idx_approval_log_operator", columnList = "operator_id"),
        @Index(name = "idx_approval_log_action", columnList = "action_type"),
        @Index(name = "idx_approval_log_acted", columnList = "acted_at")
})
@Data
public class ScrmApprovalLogEntity {

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

    /** 审批实例 ID */
    @Column(name = "instance_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long instanceId;

    /** 节点 ID */
    @Column(name = "node_id", nullable = false, length = 100)
    private String nodeId;

    /** 节点名称 (可空) */
    @Column(name = "node_name", length = 200)
    private String nodeName;

    /** 节点类型 (可空): START/APPROVE/CC/CONDITION/END */
    @Column(name = "node_type", length = 50)
    private String nodeType;

    /** 操作类型: SUBMIT/APPROVE/REJECT/TRANSFER/COUNTERSIGN/CC/WITHDRAW/RESUBMIT/TIMEOUT/URGE/COMMENT/AUTO_APPROVE */
    @Column(name = "action_type", nullable = false, length = 20)
    private String actionType;

    /** 操作人 ID */
    @Column(name = "operator_id", nullable = false, length = 100)
    private String operatorId;

    /** 操作人名称 (可空) */
    @Column(name = "operator_name", length = 100)
    private String operatorName;

    /** 操作人角色 (可空) */
    @Column(name = "operator_role", length = 100)
    private String operatorRole;

    /** 操作人类型: APPLICANT/APPROVER/CC/SYSTEM (默认 APPROVER) */
    @Column(name = "operator_type", nullable = false, length = 20)
    private String operatorType;

    /** 审批意见 (可空) */
    @Column(name = "comment", length = 2000)
    private String comment;

    /** 操作数据 JSON: {transferredTo,countersignUsers,ccUsers} (可空) */
    @Column(name = "action_data", columnDefinition = "TEXT")
    private String actionData;

    /** 上一节点 ID (可空) */
    @Column(name = "previous_node_id", length = 100)
    private String previousNodeId;

    /** 下一节点 ID (可空) */
    @Column(name = "next_node_id", length = 100)
    private String nextNodeId;

    /** 附件 (可空, 逗号分隔 URL) */
    @Column(name = "attachments", length = 1000)
    private String attachments;

    /** 操作时间 */
    @Column(name = "acted_at", nullable = false)
    private LocalDateTime actedAt;

    /** 是否自动操作 (默认 FALSE) */
    @Column(name = "is_auto_action", nullable = false)
    private Boolean isAutoAction;

    /** 操作顺序 (默认 0, 同实例内递增) */
    @Column(name = "sequence", nullable = false)
    private Integer sequence;
}
