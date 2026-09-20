/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMonitorMetricEntity.java
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
 * SCRM 监控指标实体。
 * <p>
 * 描述一项系统/业务监控指标的定义、当前值与统计、阈值配置、采集方式与历史数据,
 * 用于系统健康监控与告警触发。指标分组 (metricGroup) 标识所属域, 指标类型
 * (metricType) 标识数值语义, 阈值方向 (thresholdDirection) 决定告警判定方式。
 * </p>
 * <p>
 * 指标分组: SYSTEM / BUSINESS / PERFORMANCE / AVAILABILITY / SECURITY /
 * RESOURCE / API / DATABASE / CACHE / QUEUE。
 * 指标类型: GAUGE / COUNTER / HISTOGRAM / TIMER / SUMMARY。
 * 阈值方向: ABOVE / BELOW / RANGE。
 * 采集方式: POLLING / PUSH / CALCULATED / EXTERNAL。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_monitor_metric", schema = "scrm", indexes = {
        @Index(name = "idx_monitor_metric_code", columnList = "metric_code", unique = true),
        @Index(name = "idx_monitor_metric_group", columnList = "metric_group"),
        @Index(name = "idx_monitor_metric_type", columnList = "metric_type"),
        @Index(name = "idx_monitor_metric_enabled", columnList = "enabled"),
        @Index(name = "idx_monitor_metric_alert_active", columnList = "is_alert_active")
})
@Data
public class ScrmMonitorMetricEntity {

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

    /** 指标名称 */
    @Column(name = "metric_name", nullable = false, length = 200)
    private String metricName;

    /** 指标编码 (全局唯一) */
    @Column(name = "metric_code", nullable = false, length = 50)
    private String metricCode;

    /** 指标分组: SYSTEM/BUSINESS/PERFORMANCE/AVAILABILITY/SECURITY/RESOURCE/API/DATABASE/CACHE/QUEUE */
    @Column(name = "metric_group", nullable = false, length = 100)
    private String metricGroup;

    /** 指标类型: GAUGE/COUNTER/HISTOGRAM/TIMER/SUMMARY */
    @Column(name = "metric_type", nullable = false, length = 30)
    private String metricType;

    /** 单位 (可空) */
    @Column(name = "unit", length = 50)
    private String unit;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 当前值 (默认 0) */
    @Column(name = "current_value")
    private Double currentValue;

    /** 最小值 (默认 0) */
    @Column(name = "min_value")
    private Double minValue;

    /** 最大值 (默认 0) */
    @Column(name = "max_value")
    private Double maxValue;

    /** 平均值 (默认 0) */
    @Column(name = "avg_value")
    private Double avgValue;

    /** 目标值 (默认 0) */
    @Column(name = "target_value")
    private Double targetValue;

    /** 预警阈值 (默认 0) */
    @Column(name = "warning_threshold")
    private Double warningThreshold;

    /** 严重阈值 (默认 0) */
    @Column(name = "critical_threshold")
    private Double criticalThreshold;

    /** 阈值方向: ABOVE/BELOW/RANGE (默认 ABOVE) */
    @Column(name = "threshold_direction", nullable = false, length = 20)
    private String thresholdDirection;

    /** 采集间隔秒 (默认 60) */
    @Column(name = "collection_interval_seconds")
    private Integer collectionIntervalSeconds;

    /** 最近采集时间 (可空) */
    @Column(name = "last_collected_at")
    private LocalDateTime lastCollectedAt;

    /** 最近值时间 (可空) */
    @Column(name = "last_value_at")
    private LocalDateTime lastValueAt;

    /** 采集方式: POLLING/PUSH/CALCULATED/EXTERNAL (默认 POLLING) */
    @Column(name = "collection_method", nullable = false, length = 30)
    private String collectionMethod;

    /** 数据源 (可空) */
    @Column(name = "data_source", length = 200)
    private String dataSource;

    /** 查询表达式 (可空) */
    @Column(name = "query_expression", length = 1000)
    private String queryExpression;

    /** 标签 (可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 是否告警激活 (默认 FALSE) */
    @Column(name = "is_alert_active", nullable = false)
    private Boolean isAlertActive;

    /** 最近告警时间 (可空) */
    @Column(name = "last_alert_at")
    private LocalDateTime lastAlertAt;

    /** 告警次数 (默认 0) */
    @Column(name = "alert_count")
    private Integer alertCount;

    /** 历史数据 JSON: [{timestamp,value}] (可空) */
    @Column(name = "history_data", columnDefinition = "TEXT")
    private String historyData;

    /** 附加数据 JSON (可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
