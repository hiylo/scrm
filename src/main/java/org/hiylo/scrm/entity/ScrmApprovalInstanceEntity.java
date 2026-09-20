/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalInstanceEntity.java
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
 * SCRM 审批工作流实例实体。
 * <p>
 * 描述一次审批的运行时状态: 关联流程 ({@link #flowId} / {@link #flowName} /
 * {@link #flowType})、业务关联 ({@link #businessType} / {@link #businessId} /
 * {@link #businessTitle} / {@link #businessData})、申请人 ({@link #applicantId} /
 * {@link #applicantName} / {@link #applicantDept} / {@link #applicantRole})、
 * 当前节点 ({@link #currentNodeId} / {@link #currentNodeName} /
 * {@link #currentNodeType} / {@link #currentApproverIds})、状态与时间
 * ({@link #status} / {@link #startedAt} / {@link #completedAt} /
 * {@link #approvedAt} / {@link #approvedBy} / {@link #rejectedBy} /
 * {@link #rejectedReason} / {@link #withdrawnAt})、历史与变量
 * ({@link #nodeHistory} / {@link #variables} / {@link #attachmentUrls} /
 * {@link #ccUsers}) 与加急/超时控制 ({@link #priority} / {@link #isUrgent} /
 * {@link #urgentReason} / {@link #currentTimeoutAt} / {@link #isOverdue})。
 * </p>
 * <p>
 * 业务类型: CONTRACT / EXPENSE / LEAVE / REFUND / DISCOUNT / OTHER。
 * 状态: PENDING / APPROVING / APPROVED / REJECTED / CANCELLED /
 * TRANSFERRED / TIMEOUT / WITHDRAWN。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_approval_instance", schema = "scrm", indexes = {
        @Index(name = "idx_approval_instance_no", columnList = "instance_no", unique = true),
        @Index(name = "idx_approval_instance_flow", columnList = "flow_id"),
        @Index(name = "idx_approval_instance_business", columnList = "business_type,business_id"),
        @Index(name = "idx_approval_instance_status", columnList = "status"),
        @Index(name = "idx_approval_instance_applicant", columnList = "applicant_id"),
        @Index(name = "idx_approval_instance_started", columnList = "started_at")
})
@Data
public class ScrmApprovalInstanceEntity {

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

    /** 实例编号 (全局唯一) */
    @Column(name = "instance_no", nullable = false, length = 100)
    private String instanceNo;

    /** 流程 ID */
    @Column(name = "flow_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long flowId;

    /** 流程名称 (可空, 提交时快照) */
    @Column(name = "flow_name", length = 200)
    private String flowName;

    /** 流程类型 (可空, 提交时快照) */
    @Column(name = "flow_type", length = 30)
    private String flowType;

    /** 业务类型: CONTRACT/EXPENSE/LEAVE/REFUND/DISCOUNT/OTHER */
    @Column(name = "business_type", nullable = false, length = 50)
    private String businessType;

    /** 业务 ID (可空) */
    @Column(name = "business_id", length = 200)
    private String businessId;

    /** 业务标题 (可空) */
    @Column(name = "business_title", length = 500)
    private String businessTitle;

    /** 业务数据 JSON 快照 (可空) */
    @Column(name = "business_data", columnDefinition = "TEXT")
    private String businessData;

    /** 申请人 ID */
    @Column(name = "applicant_id", nullable = false, length = 100)
    private String applicantId;

    /** 申请人名称 (可空) */
    @Column(name = "applicant_name", length = 100)
    private String applicantName;

    /** 申请人部门 (可空) */
    @Column(name = "applicant_dept", length = 200)
    private String applicantDept;

    /** 申请人角色 (可空) */
    @Column(name = "applicant_role", length = 100)
    private String applicantRole;

    /** 当前节点 ID (可空) */
    @Column(name = "current_node_id", length = 100)
    private String currentNodeId;

    /** 当前节点名称 (可空) */
    @Column(name = "current_node_name", length = 200)
    private String currentNodeName;

    /** 当前节点类型 (可空) */
    @Column(name = "current_node_type", length = 50)
    private String currentNodeType;

    /** 当前审批人 ID 列表 (可空, 逗号分隔) */
    @Column(name = "current_approver_ids", length = 500)
    private String currentApproverIds;

    /** 状态: PENDING/APPROVING/APPROVED/REJECTED/CANCELLED/TRANSFERRED/TIMEOUT/WITHDRAWN (默认 PENDING) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 优先级 (默认 0) */
    @Column(name = "priority")
    private Integer priority;

    /** 是否加急 (默认 FALSE) */
    @Column(name = "is_urgent", nullable = false)
    private Boolean isUrgent;

    /** 加急原因 (可空) */
    @Column(name = "urgent_reason", length = 500)
    private String urgentReason;

    /** 开始时间 */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** 完成时间 (可空) */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 审批时长 (小时, 可空) */
    @Column(name = "duration_hours")
    private Integer durationHours;

    /** 最终审批时间 (可空) */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 最终审批人 (可空) */
    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    /** 驳回人 (可空) */
    @Column(name = "rejected_by", length = 100)
    private String rejectedBy;

    /** 驳回原因 (可空) */
    @Column(name = "rejected_reason", length = 500)
    private String rejectedReason;

    /** 撤回时间 (可空) */
    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    /** 节点历史 JSON: [{nodeId,nodeName,approverId,approverName,action,comment,timestamp}] (可空) */
    @Column(name = "node_history", columnDefinition = "TEXT")
    private String nodeHistory;

    /** 流程变量 JSON (可空) */
    @Column(name = "variables", columnDefinition = "TEXT")
    private String variables;

    /** 附件 URL (可空) */
    @Column(name = "attachment_urls", length = 1000)
    private String attachmentUrls;

    /** 抄送人 (可空, 逗号分隔) */
    @Column(name = "cc_users", length = 500)
    private String ccUsers;

    /** 通知时间 (可空) */
    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    /** 最后活动时间 (可空) */
    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    /** 当前节点超时时间 (可空) */
    @Column(name = "current_timeout_at")
    private LocalDateTime currentTimeoutAt;

    /** 是否逾期 (默认 FALSE) */
    @Column(name = "is_overdue", nullable = false)
    private Boolean isOverdue;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
