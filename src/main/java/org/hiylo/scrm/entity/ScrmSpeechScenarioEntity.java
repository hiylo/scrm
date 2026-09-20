/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechScenarioEntity.java
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
 * SCRM 销售话术场景实体。
 * <p>
 * 描述一类销售对话场景: {@link #scenarioCategory} 区分场景类别
 * (GREETING/INQUIRY/PITCH/OBJECTION/CLOSING/FOLLOW_UP/CROSS_SELL/UP_SELL/
 * RETENTION/RECOVERY/APPOINTMENT/REFERRAL/THANK_YOU/APOLOGY),
 * {@link #triggerConditions} (JSON) 承载触发条件, {@link #applicableProducts} /
 * {@link #applicableChannels} / {@link #customerStage} 描述适用范围,
 * {@link #priority} 控制场景排序, {@link #speechCount} / {@link #avgRating} /
 * {@link #usageCount} / {@link #successRate} 为场景级统计指标。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_speech_scenario", schema = "scrm", indexes = {
        @Index(name = "idx_speech_scenario_code", columnList = "scenario_code", unique = true),
        @Index(name = "idx_speech_scenario_category", columnList = "scenario_category"),
        @Index(name = "idx_speech_scenario_stage", columnList = "customer_stage"),
        @Index(name = "idx_speech_scenario_enabled", columnList = "enabled")
})
@Data
public class ScrmSpeechScenarioEntity {

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

    /** 场景名称 */
    @Column(name = "scenario_name", nullable = false, length = 200)
    private String scenarioName;

    /** 场景编码 (全局唯一) */
    @Column(name = "scenario_code", nullable = false, length = 50)
    private String scenarioCode;

    /** 场景类别: GREETING/INQUIRY/PITCH/OBJECTION/CLOSING/FOLLOW_UP/CROSS_SELL/UP_SELL/RETENTION/RECOVERY/APPOINTMENT/REFERRAL/THANK_YOU/APOLOGY */
    @Column(name = "scenario_category", nullable = false, length = 50)
    private String scenarioCategory;

    /** 场景描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 触发条件 JSON: {customerStage,productCategory,channel,timeOfDay,sentiment} (可空) */
    @Column(name = "trigger_conditions", columnDefinition = "TEXT")
    private String triggerConditions;

    /** 适用产品 (逗号分隔, 可空) */
    @Column(name = "applicable_products", length = 500)
    private String applicableProducts;

    /** 适用渠道 (逗号分隔, 可空) */
    @Column(name = "applicable_channels", length = 500)
    private String applicableChannels;

    /** 客户阶段: NEW/ACTIVE/AT_RISK/CHURNED/VIP/PROSPECT (可空) */
    @Column(name = "customer_stage", length = 50)
    private String customerStage;

    /** 优先级 (默认 0, 数字越大越优先) */
    @Column(name = "priority")
    private Integer priority;

    /** 话术数 (默认 0) */
    @Column(name = "speech_count")
    private Integer speechCount;

    /** 平均评分 (默认 0) */
    @Column(name = "avg_rating")
    private Double avgRating;

    /** 使用次数 (默认 0) */
    @Column(name = "usage_count")
    private Integer usageCount;

    /** 成功率 (默认 0) */
    @Column(name = "success_rate")
    private Double successRate;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
