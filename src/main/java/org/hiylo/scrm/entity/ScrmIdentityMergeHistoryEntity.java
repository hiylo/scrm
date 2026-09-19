/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIdentityMergeHistoryEntity.java
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
 * SCRM 客户身份合并历史实体。
 * <p>
 * 记录一次已执行的合并操作的完整审计信息: 合并的身份数 / 交易数 / 标签 / 字段变更详情 /
 * 合并前后 LTV / 数据完整性检查 / 回滚标记。可通过 {@link #rollbackAvailable} 与
 * {@link #rolledBack} 控制回滚能力, 回滚后记录回滚时间与操作人。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_identity_merge_history", schema = "scrm", indexes = {
        @Index(name = "idx_identity_merge_history_task", columnList = "task_id"),
        @Index(name = "idx_identity_merge_history_source", columnList = "source_customer_id"),
        @Index(name = "idx_identity_merge_history_target", columnList = "target_customer_id"),
        @Index(name = "idx_identity_merge_history_time", columnList = "merged_at"),
        @Index(name = "idx_identity_merge_history_rollback", columnList = "rollback_available,rolled_back")
})
@Data
public class ScrmIdentityMergeHistoryEntity {

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

    /** 任务 ID */
    @Column(name = "task_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

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

    /** 合并身份数 */
    @Column(name = "merged_identities_count")
    private Integer mergedIdentitiesCount;

    /** 合并交易数 */
    @Column(name = "merged_transactions_count")
    private Integer mergedTransactionsCount;

    /** 合并的标签 (可空, 逗号分隔) */
    @Column(name = "merged_tags", length = 1000)
    private String mergedTags;

    /** JSON 字段变更详情 (可空): [{field,oldValue,newValue,strategy}] */
    @Column(name = "field_changes", columnDefinition = "TEXT")
    private String fieldChanges;

    /** 合并的身份列表 JSON (可空) */
    @Column(name = "identities_merged", length = 1000)
    private String identitiesMerged;

    /** 合并前 LTV */
    @Column(name = "pre_merge_ltv")
    private Double preMergeLtv;

    /** 合并后 LTV */
    @Column(name = "post_merge_ltv")
    private Double postMergeLtv;

    /** 数据完整性检查 */
    @Column(name = "data_integrity_checked", nullable = false)
    private Boolean dataIntegrityChecked;

    /** 数据完整性检查是否通过 */
    @Column(name = "data_integrity_passed", nullable = false)
    private Boolean dataIntegrityPassed;

    /** 是否可回滚 */
    @Column(name = "rollback_available", nullable = false)
    private Boolean rollbackAvailable;

    /** 是否已回滚 */
    @Column(name = "rolled_back", nullable = false)
    private Boolean rolledBack;

    /** 回滚时间 (可空) */
    @Column(name = "rolled_back_at")
    private LocalDateTime rolledBackAt;

    /** 回滚操作人 (可空) */
    @Column(name = "rolled_back_by", length = 100)
    private String rolledBackBy;

    /** 合并时间 */
    @Column(name = "merged_at", nullable = false)
    private LocalDateTime mergedAt;

    /** 合并操作人 (可空) */
    @Column(name = "merged_by", length = 100)
    private String mergedBy;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;
}
