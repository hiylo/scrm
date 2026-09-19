/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmForecastScenarioEntity.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 销售预测场景实体。
 * <p>
 * 基于预测模型构建一次预测场景: 关联模型、场景类型 (基线/乐观/悲观/What-If/目标/压力测试)、
 * 目标周期 (如 2026-Q3) 与目标起止日期、粒度、假设条件、JSON 输入参数与调整因子、
 * 适用客群/产品/渠道/地区、运行状态与运行时间、总预测值与置信区间、预估准确度、
 * 风险因素/机会因素/建议、审批与分享信息。状态流转: DRAFT / RUNNING / COMPLETED /
 * FAILED / ARCHIVED。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_forecast_scenario", schema = "scrm", indexes = {
        @Index(name = "idx_forecast_scenario_code", columnList = "scenario_code"),
        @Index(name = "idx_forecast_scenario_model", columnList = "model_id"),
        @Index(name = "idx_forecast_scenario_type", columnList = "scenario_type"),
        @Index(name = "idx_forecast_scenario_status", columnList = "status"),
        @Index(name = "idx_forecast_scenario_period", columnList = "target_period")
})
@Data
public class ScrmForecastScenarioEntity {

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
        if (scenarioType == null) {
            scenarioType = "BASELINE";
        }
        if (granularity == null) {
            granularity = "MONTHLY";
        }
        if (status == null) {
            status = "DRAFT";
        }
        if (runDurationMs == null) {
            runDurationMs = 0;
        }
        if (totalForecastValue == null) {
            totalForecastValue = 0d;
        }
        if (confidenceLevel == null) {
            confidenceLevel = 0.95d;
        }
        if (confidenceLowerBound == null) {
            confidenceLowerBound = 0d;
        }
        if (confidenceUpperBound == null) {
            confidenceUpperBound = 0d;
        }
        if (accuracyEstimate == null) {
            accuracyEstimate = 0d;
        }
        if (isApproved == null) {
            isApproved = false;
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

    /** 场景编码 (唯一) */
    @Column(name = "scenario_code", nullable = false, length = 50, unique = true)
    private String scenarioCode;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 关联模型 ID */
    @Column(name = "model_id", nullable = false)
    private Long modelId;

    /** 关联模型名称 (冗余, 便于查询展示, 可空) */
    @Column(name = "model_name", length = 200)
    private String modelName;

    /** 场景类型: BASELINE / OPTIMISTIC / PESSIMISTIC / WHAT_IF / TARGET / STRESS_TEST (默认 BASELINE) */
    @Column(name = "scenario_type", nullable = false, length = 30)
    private String scenarioType;

    /** 预测周期 (如 2026-Q3) */
    @Column(name = "target_period", nullable = false, length = 20)
    private String targetPeriod;

    /** 目标开始日期 */
    @Column(name = "target_start_date", nullable = false)
    private LocalDate targetStartDate;

    /** 目标结束日期 */
    @Column(name = "target_end_date", nullable = false)
    private LocalDate targetEndDate;

    /** 粒度: DAILY / WEEKLY / MONTHLY / QUARTERLY (默认 MONTHLY) */
    @Column(name = "granularity", nullable = false, length = 20)
    private String granularity;

    /** 假设条件 (可空) */
    @Column(name = "assumptions", length = 2000)
    private String assumptions;

    /** JSON 输入参数: {growthRate, seasonalityFactor, marketCondition, ...} (可空) */
    @Column(name = "input_parameters", columnDefinition = "TEXT")
    private String inputParameters;

    /** JSON 调整因子: [{factorName, value, description}, ...] (可空) */
    @Column(name = "adjustment_factors", columnDefinition = "TEXT")
    private String adjustmentFactors;

    /** 适用客群 (逗号分隔, 可空) */
    @Column(name = "segments", length = 500)
    private String segments;

    /** 适用产品 (逗号分隔, 可空) */
    @Column(name = "products", length = 500)
    private String products;

    /** 适用渠道 (逗号分隔, 可空) */
    @Column(name = "channels", length = 500)
    private String channels;

    /** 适用地区 (逗号分隔, 可空) */
    @Column(name = "regions", length = 500)
    private String regions;

    /** 状态: DRAFT / RUNNING / COMPLETED / FAILED / ARCHIVED (默认 DRAFT) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 运行开始时间 (可空) */
    @Column(name = "run_started_at")
    private LocalDateTime runStartedAt;

    /** 运行完成时间 (可空) */
    @Column(name = "run_completed_at")
    private LocalDateTime runCompletedAt;

    /** 运行耗时 (毫秒, 默认 0) */
    @Column(name = "run_duration_ms")
    private Integer runDurationMs;

    /** 总预测值 (默认 0) */
    @Column(name = "total_forecast_value")
    private Double totalForecastValue;

    /** 置信水平 (默认 0.95) */
    @Column(name = "confidence_level")
    private Double confidenceLevel;

    /** 置信下界 (默认 0) */
    @Column(name = "confidence_lower_bound")
    private Double confidenceLowerBound;

    /** 置信上界 (默认 0) */
    @Column(name = "confidence_upper_bound")
    private Double confidenceUpperBound;

    /** 预估准确度 (默认 0) */
    @Column(name = "accuracy_estimate")
    private Double accuracyEstimate;

    /** 风险因素 (可空) */
    @Column(name = "risk_factors", length = 1000)
    private String riskFactors;

    /** 机会因素 (可空) */
    @Column(name = "opportunities", length = 1000)
    private String opportunities;

    /** 建议 (可空) */
    @Column(name = "recommendations", length = 2000)
    private String recommendations;

    /** 审批人 (可空) */
    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    /** 审批时间 (可空) */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 是否已审批 (默认 FALSE) */
    @Column(name = "is_approved", nullable = false)
    private Boolean isApproved;

    /** 分享给的用户 ID 列表 (逗号分隔, 可空) */
    @Column(name = "shared_with", length = 500)
    private String sharedWith;

    /** 标签 (逗号分隔, 可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
