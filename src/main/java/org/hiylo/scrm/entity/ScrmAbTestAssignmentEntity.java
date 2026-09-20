/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAbTestAssignmentEntity.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM A/B 测试客户分配实体。
 * <p>
 * 记录单个客户在某次测试中被分配到的变体及转化轨迹: {@link #variantId} 为分配目标,
 * {@link #assignmentMethod} 描述分配方法 (RANDOM/STRATIFIED/BLOCKED), {@link #converted}
 * 标记是否转化, {@link #conversionValue} 记录转化价值, {@link #engagementData} (JSON)
 * 承载互动数据。分配记录是测试结果统计与显著性检验的数据基础。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_ab_test_assignment", schema = "scrm", indexes = {
        @Index(name = "idx_ab_assign_test", columnList = "test_id"),
        @Index(name = "idx_ab_assign_variant", columnList = "variant_id"),
        @Index(name = "idx_ab_assign_customer", columnList = "customer_id"),
        @Index(name = "idx_ab_assign_unique", columnList = "test_id,customer_id", unique = true),
        @Index(name = "idx_ab_assign_converted", columnList = "converted")
})
@Data
public class ScrmAbTestAssignmentEntity {

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

    /** 测试 ID */
    @Column(name = "test_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long testId;

    /** 变体 ID */
    @Column(name = "variant_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long variantId;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (冗余, 便于列表展示, 可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 分配时间 */
    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    /** 分配方法: RANDOM / STRATIFIED / BLOCKED (默认 RANDOM) */
    @Column(name = "assignment_method", nullable = false, length = 20)
    private String assignmentMethod;

    /** 是否转化 (默认 FALSE) */
    @Column(name = "converted", nullable = false)
    private Boolean converted;

    /** 转化时间 (可空) */
    @Column(name = "converted_at")
    private LocalDateTime convertedAt;

    /** 转化价值 (默认 0) */
    @Column(name = "conversion_value")
    private Double conversionValue;

    /** 互动数据 JSON (可空) */
    @Column(name = "engagement_data", columnDefinition = "TEXT")
    private String engagementData;

    /** 会话 ID (可空) */
    @Column(name = "session_id", length = 200)
    private String sessionId;

    /** 附加数据 JSON (可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
