/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerMergeRecordEntity.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.hibernate.annotations.GenericGenerator;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户合并记录实体。
 * <p>
 * 记录客户合并操作, 包含主客户 ID、被合并客户 ID 列表、合并策略与匹配条件。
 * 合并完成后可回滚, 回滚时恢复被合并客户。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_merge_record", schema = "scrm", indexes = {
        @Index(name = "idx_merge_record_primary", columnList = "primary_customer_id"),
        @Index(name = "idx_merge_record_status", columnList = "status"),
        @Index(name = "idx_merge_record_merged_at", columnList = "merged_at")
})
@Data
public class ScrmCustomerMergeRecordEntity {

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

    /** 乐观锁版本号（并发更新保护，后写入者触发 OptimisticLockException） */
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

    /** 主客户 ID (合并后保留的客户) */
    @Column(name = "primary_customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long primaryCustomerId;

    /** 被合并客户 ID 列表 (逗号分隔) */
    @Column(name = "merged_customer_ids", nullable = false, length = 500)
    private String mergedCustomerIds;

    /** 合并策略: MANUAL(手动) / AUTO_MERGE(自动) */
    @Column(name = "merge_strategy", nullable = false, length = 20)
    private String mergeStrategy;

    /** 匹配条件描述 (如: exact:nickname+phone) */
    @Column(name = "match_criteria", nullable = false, length = 200)
    private String matchCriteria;

    /** 合并状态: COMPLETED(已完成) / REVERTED(已回滚) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 合并时间 */
    @Column(name = "merged_at")
    private LocalDateTime mergedAt;

    /** 回滚时间 */
    @Column(name = "reverted_at")
    private LocalDateTime revertedAt;

    /** 合并操作人用户名 */
    @Column(name = "merged_by", length = 100)
    private String mergedBy;

    /** 回滚操作人用户名 */
    @Column(name = "reverted_by", length = 100)
    private String revertedBy;
}
