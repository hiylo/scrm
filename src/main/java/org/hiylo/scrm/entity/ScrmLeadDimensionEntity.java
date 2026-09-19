/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadDimensionEntity.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 销售线索评分维度实体。
 * <p>
 * 定义可复用的评分维度: 维度类别 {@link #dimensionCategory} (DEMOGRAPHIC 人口统计 / BEHAVIORAL
 * 行为 / ENGAGEMENT 互动 / FIRMOGRAPHIC 企业属性 / TECHNOGRAPHIC 技术 / NEED_BASED 需求 /
 * TIMING 时机), 默认权重 {@link #defaultWeight} 与默认最高分 {@link #defaultMaxScore}。
 * </p>
 * <p>
 * {@link #scoringRules} 为 JSON 数组: [{field, operator, value, score, description}], 描述
 * 该维度下的评分规则。{@link #applicableFields} 标注可用字段 (逗号分隔), {@link #usageCount}
 * 记录被引用次数, 供模型配置时引用。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_lead_dimension", schema = "scrm", indexes = {
        @Index(name = "idx_lead_dimension_code", columnList = "dimension_code"),
        @Index(name = "idx_lead_dimension_category", columnList = "dimension_category"),
        @Index(name = "idx_lead_dimension_enabled", columnList = "enabled")
})
@Data
public class ScrmLeadDimensionEntity {

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

    /** 维度名称 */
    @Column(name = "dimension_name", nullable = false, length = 100)
    private String dimensionName;

    /** 维度编码 (唯一) */
    @Column(name = "dimension_code", nullable = false, length = 50)
    private String dimensionCode;

    /** 维度描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 维度类别: DEMOGRAPHIC / BEHAVIORAL / ENGAGEMENT / FIRMOGRAPHIC / TECHNOGRAPHIC / NEED_BASED / TIMING */
    @Column(name = "dimension_category", nullable = false, length = 50)
    private String dimensionCategory;

    /** 默认权重 (默认 1.0) */
    @Column(name = "default_weight", nullable = false)
    private Double defaultWeight;

    /** 默认最高分 (默认 20) */
    @Column(name = "default_max_score", nullable = false)
    private Integer defaultMaxScore;

    /** 评分规则 JSON: [{field, operator, value, score, description}] */
    @Column(name = "scoring_rules", nullable = false, columnDefinition = "TEXT")
    private String scoringRules;

    /** 可用字段 (逗号分隔, 可空) */
    @Column(name = "applicable_fields", length = 500)
    private String applicableFields;

    /** 是否启用 (默认 true) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 使用次数 (被模型引用累计) */
    @Column(name = "usage_count")
    private Integer usageCount;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
