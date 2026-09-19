/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProfileComparisonEntity.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 画像对比实体。
 * <p>
 * 记录两个客户画像的对比结果, 包含逐维度相似度 / 总体相似度 /
 * 共同特征 / 关键差异 / 对比建议, 用于客群洞察与营销策略制定。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_profile_comparison", schema = "scrm", indexes = {
        @Index(name = "idx_profile_comparison_customer1", columnList = "customer_id1"),
        @Index(name = "idx_profile_comparison_customer2", columnList = "customer_id2"),
        @Index(name = "idx_profile_comparison_type", columnList = "comparison_type")
})
@Data
public class ScrmProfileComparisonEntity {

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

    /** 客户 ID 1 */
    @Column(name = "customer_id1", nullable = false)
    private Long customerId1;

    /** 客户名称 1 (可空, 对比时快照) */
    @Column(name = "customer_name1", length = 200)
    private String customerName1;

    /** 客户 ID 2 */
    @Column(name = "customer_id2", nullable = false)
    private Long customerId2;

    /** 客户名称 2 (可空, 对比时快照) */
    @Column(name = "customer_name2", length = 200)
    private String customerName2;

    /** 对比类型: INDIVIDUAL / SEGMENT / TEMPLATE */
    @Column(name = "comparison_type", nullable = false, length = 30)
    private String comparisonType;

    /** 对比维度结果 JSON: [{dimension,similarity,differences}] */
    @Column(name = "dimensions", nullable = false, columnDefinition = "TEXT")
    private String dimensions;

    /** 总体相似度 (0-1) */
    @Column(name = "overall_similarity")
    private Double overallSimilarity;

    /** 共同特征 (可空) */
    @Column(name = "common_traits", length = 1000)
    private String commonTraits;

    /** 关键差异 (可空) */
    @Column(name = "key_differences", length = 1000)
    private String keyDifferences;

    /** 对比建议 (可空) */
    @Column(name = "recommendation", length = 500)
    private String recommendation;

    /** 对比时间 */
    @Column(name = "compared_at", nullable = false)
    private LocalDateTime comparedAt;

    /** 对比人 (可空) */
    @Column(name = "compared_by", length = 100)
    private String comparedBy;
}
