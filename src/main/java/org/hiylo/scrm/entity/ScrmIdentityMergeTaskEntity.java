/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIdentityMergeTaskEntity.java
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
 * SCRM 客户身份合并任务实体。
 * <p>
 * 记录一次跨平台客户身份合并任务, 包含源客户 / 目标客户 / 合并类型 / 状态机 /
 * 匹配信息 / 审核流程 / 执行时间戳。任务支持审核 (REVIEWING → APPROVED/REJECTED),
 * 执行 (PENDING/APPROVED → IN_PROGRESS → COMPLETED/FAILED) 与取消。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_identity_merge_task", schema = "scrm", indexes = {
        @Index(name = "idx_identity_merge_task_status", columnList = "status"),
        @Index(name = "idx_identity_merge_task_type", columnList = "merge_type"),
        @Index(name = "idx_identity_merge_task_source", columnList = "source_customer_id"),
        @Index(name = "idx_identity_merge_task_target", columnList = "target_customer_id"),
        @Index(name = "idx_identity_merge_task_time", columnList = "create_time")
})
@Data
public class ScrmIdentityMergeTaskEntity {

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

    /** 任务名称 */
    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    /** 源客户 ID (被合并的客户) */
    @Column(name = "source_customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceCustomerId;

    /** 源客户名称 (可空) */
    @Column(name = "source_customer_name", length = 200)
    private String sourceCustomerName;

    /** 目标客户 ID (合并后保留的客户) */
    @Column(name = "target_customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetCustomerId;

    /** 目标客户名称 (可空) */
    @Column(name = "target_customer_name", length = 200)
    private String targetCustomerName;

    /** 合并类型: MANUAL/AUTO/SUGGESTED */
    @Column(name = "merge_type", nullable = false, length = 20)
    private String mergeType;

    /** 状态: PENDING/REVIEWING/APPROVED/IN_PROGRESS/COMPLETED/FAILED/CANCELLED/REJECTED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 匹配原因 (可空, 逗号分隔) */
    @Column(name = "match_reasons", length = 1000)
    private String matchReasons;

    /** 匹配分数 (0~1) */
    @Column(name = "match_score")
    private Double matchScore;

    /** 匹配字段 JSON (可空) */
    @Column(name = "matched_fields", length = 500)
    private String matchedFields;

    /** JSON 合并配置 (可空): {fieldStrategy:[{field,strategy:KEEP_TARGET/KEEP_SOURCE/MERGE/CONCAT}]} */
    @Column(name = "merge_config", columnDefinition = "TEXT")
    private String mergeConfig;

    /** 冲突字段 JSON (可空) */
    @Column(name = "conflict_fields", length = 1000)
    private String conflictFields;

    /** 身份数量 */
    @Column(name = "identity_count")
    private Integer identityCount;

    /** 交易数量 */
    @Column(name = "transaction_count")
    private Integer transactionCount;

    /** 审核人 (可空) */
    @Column(name = "review_by", length = 100)
    private String reviewBy;

    /** 审核时间 (可空) */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 审核意见 (可空) */
    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    /** 批准人 (可空) */
    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    /** 批准时间 (可空) */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 开始执行时间 (可空) */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 完成时间 (可空) */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 失败原因 (可空) */
    @Column(name = "failed_reason", length = 1000)
    private String failedReason;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
