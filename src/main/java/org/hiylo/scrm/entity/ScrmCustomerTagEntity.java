/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerTagEntity.java
 * Date : 2026/07/27 02:41:22
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
 * SCRM 客户标签实体。
 * <p>
 * 客户标签体系定义: 标签类型 (MANUAL/RULE/DERIVED/SYSTEM/AI)、值类型 (BOOLEAN/ENUM/NUMERIC/STRING/DATE)、
 * 标签分类 (DEMOGRAPHIC/BEHAVIORAL/PSYCHOGRAPHIC/TRANSACTIONAL/SOCIAL/PREFERENCE/LIFECYCLE/RISK/VALUE)、
 * 规则表达式与规则条件 (JSON)、自动应用、评估频率、客户数/覆盖率/趋势统计。支持标签分组、系统标签、
 * 必填/可见/可搜索/多值等元数据。标签编码 (tagCode) 唯一。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_tag_def", schema = "scrm", indexes = {
        @Index(name = "idx_customer_tag_code", columnList = "tag_code"),
        @Index(name = "idx_customer_tag_group", columnList = "group_id"),
        @Index(name = "idx_customer_tag_type", columnList = "tag_type"),
        @Index(name = "idx_customer_tag_category", columnList = "category"),
        @Index(name = "idx_customer_tag_value", columnList = "value_type"),
        @Index(name = "idx_customer_tag_enabled", columnList = "enabled"),
        @Index(name = "idx_customer_tag_auto", columnList = "auto_apply")
})
@Data
public class ScrmCustomerTagEntity {

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
        if (valueType == null) {
            valueType = "BOOLEAN";
        }
        if (isSystem == null) {
            isSystem = false;
        }
        if (isRequired == null) {
            isRequired = false;
        }
        if (isVisible == null) {
            isVisible = true;
        }
        if (isSearchable == null) {
            isSearchable = true;
        }
        if (isMultiple == null) {
            isMultiple = false;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
        if (ruleLogic == null) {
            ruleLogic = "AND";
        }
        if (autoApply == null) {
            autoApply = false;
        }
        if (evaluationFrequency == null) {
            evaluationFrequency = "DAILY";
        }
        if (customerCount == null) {
            customerCount = 0;
        }
        if (coverageRate == null) {
            coverageRate = 0d;
        }
        if (positiveCount == null) {
            positiveCount = 0;
        }
        if (negativeCount == null) {
            negativeCount = 0;
        }
        if (neutralCount == null) {
            neutralCount = 0;
        }
        if (trueCount == null) {
            trueCount = 0;
        }
        if (falseCount == null) {
            falseCount = 0;
        }
        if (avgNumericValue == null) {
            avgNumericValue = 0d;
        }
        if (trend == null) {
            trend = "STABLE";
        }
        if (trendPercent == null) {
            trendPercent = 0d;
        }
        if (priority == null) {
            priority = 0;
        }
        if (enabled == null) {
            enabled = true;
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

    /** 标签名称 */
    @Column(name = "tag_name", nullable = false, length = 200)
    private String tagName;

    /** 标签编码 (唯一) */
    @Column(name = "tag_code", nullable = false, length = 50, unique = true)
    private String tagCode;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 标签分组 ID (可空) */
    @Column(name = "group_id")
    private Long groupId;

    /** 标签分组名称 (可空) */
    @Column(name = "group_name", length = 200)
    private String groupName;

    /** 标签类型: MANUAL/RULE/DERIVED/SYSTEM/AI */
    @Column(name = "tag_type", nullable = false, length = 30)
    private String tagType;

    /** 值类型: BOOLEAN/ENUM/NUMERIC/STRING/DATE (默认 BOOLEAN) */
    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType;

    /** 枚举选项 JSON (可空) */
    @Column(name = "enum_options", length = 1000)
    private String enumOptions;

    /** 默认值 (可空) */
    @Column(name = "default_value", length = 200)
    private String defaultValue;

    /** 标签分类: DEMOGRAPHIC/BEHAVIORAL/PSYCHOGRAPHIC/TRANSACTIONAL/SOCIAL/PREFERENCE/LIFECYCLE/RISK/VALUE (可空) */
    @Column(name = "category", length = 100)
    private String category;

    /** 子分类 (可空) */
    @Column(name = "sub_category", length = 100)
    private String subCategory;

    /** 是否系统标签 (默认 FALSE) */
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem;

    /** 是否必填 (默认 FALSE) */
    @Column(name = "is_required", nullable = false)
    private Boolean isRequired;

    /** 是否可见 (默认 TRUE) */
    @Column(name = "is_visible", nullable = false)
    private Boolean isVisible;

    /** 是否可搜索 (默认 TRUE) */
    @Column(name = "is_searchable", nullable = false)
    private Boolean isSearchable;

    /** 是否多值标签 (默认 FALSE) */
    @Column(name = "is_multiple", nullable = false)
    private Boolean isMultiple;

    /** 颜色 (可空) */
    @Column(name = "color", length = 20)
    private String color;

    /** 图标 (可空) */
    @Column(name = "icon", length = 200)
    private String icon;

    /** 展示顺序 (默认 0) */
    @Column(name = "display_order")
    private Integer displayOrder;

    /** 帮助文本 (可空) */
    @Column(name = "help_text", length = 500)
    private String helpText;

    /** 适用客群 (可空) */
    @Column(name = "applicable_segments", length = 500)
    private String applicableSegments;

    /** 规则表达式 (可空) */
    @Column(name = "rule_expression", length = 2000)
    private String ruleExpression;

    /** 规则条件 JSON (可空): [{field,operator,value,groupBy}] */
    @Column(name = "rule_conditions", columnDefinition = "TEXT")
    private String ruleConditions;

    /** 规则逻辑: AND/OR (默认 AND) */
    @Column(name = "rule_logic", nullable = false, length = 20)
    private String ruleLogic;

    /** 是否自动应用 (默认 FALSE) */
    @Column(name = "auto_apply", nullable = false)
    private Boolean autoApply;

    /** 评估频率: REALTIME/HOURLY/DAILY/WEEKLY/MONTHLY (默认 DAILY) */
    @Column(name = "evaluation_frequency", nullable = false, length = 20)
    private String evaluationFrequency;

    /** 最近评估时间 (可空) */
    @Column(name = "last_evaluated_at")
    private LocalDateTime lastEvaluatedAt;

    /** 客户数 (默认 0) */
    @Column(name = "customer_count")
    private Integer customerCount;

    /** 覆盖率% (默认 0) */
    @Column(name = "coverage_rate")
    private Double coverageRate;

    /** 正向数 (默认 0) */
    @Column(name = "positive_count")
    private Integer positiveCount;

    /** 负向数 (默认 0) */
    @Column(name = "negative_count")
    private Integer negativeCount;

    /** 中性数 (默认 0) */
    @Column(name = "neutral_count")
    private Integer neutralCount;

    /** BOOLEAN 类型 true 数 (默认 0) */
    @Column(name = "true_count")
    private Integer trueCount;

    /** BOOLEAN 类型 false 数 (默认 0) */
    @Column(name = "false_count")
    private Integer falseCount;

    /** NUMERIC 平均值 (默认 0) */
    @Column(name = "avg_numeric_value")
    private Double avgNumericValue;

    /** TOP 值 JSON (可空) */
    @Column(name = "top_values", length = 1000)
    private String topValues;

    /** 趋势: RISING/STABLE/FALLING (默认 STABLE) */
    @Column(name = "trend", nullable = false, length = 20)
    private String trend;

    /** 趋势百分比 (默认 0) */
    @Column(name = "trend_percent")
    private Double trendPercent;

    /** 优先级 (默认 0) */
    @Column(name = "priority")
    private Integer priority;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 标签 (可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
