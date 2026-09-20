/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerDuplicateEntity.java
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SCRM 客户重复检测实体。
 * <p>
 * 记录重复客户检测的成对结果, 包含匹配类型 (精确/模糊)、匹配分数与处理状态。
 * 检测结果经人工确认后可触发合并操作, 或标记为忽略。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_duplicate", schema = "scrm", indexes = {
        @Index(name = "idx_customer_dup_customer", columnList = "customer_id"),
        @Index(name = "idx_customer_dup_dup_customer", columnList = "duplicate_customer_id"),
        @Index(name = "idx_customer_dup_status", columnList = "status")
})
@Data
public class ScrmCustomerDuplicateEntity {

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

    /** 原始客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 重复客户 ID */
    @Column(name = "duplicate_customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long duplicateCustomerId;

    /** 匹配类型: EXACT(精确) / FUZZY(模糊) */
    @Column(name = "match_type", nullable = false, length = 20)
    private String matchType;

    /** 匹配分数 (0~100, 越高越相似) */
    @Column(name = "match_score", precision = 5, scale = 2)
    private BigDecimal matchScore;

    /** 匹配条件描述 (如: nickname+phone) */
    @Column(name = "match_criteria", length = 200)
    private String matchCriteria;

    /** 处理状态: PENDING(待处理) / CONFIRMED(已确认) / IGNORED(已忽略) / MERGED(已合并) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 检测时间 */
    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    /** 处理时间 */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** 处理人用户名 */
    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;
}
