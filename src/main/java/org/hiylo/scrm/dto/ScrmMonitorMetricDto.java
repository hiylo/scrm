/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMonitorMetricDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 监控指标 DTO。
 * <p>
 * 对应 {@code ScrmMonitorMetricEntity} 的业务字段, 创建/更新接口入参。
 * metricGroup / metricType / thresholdDirection / collectionMethod 以枚举字符串校验合法性;
 * 各阈值与统计字段缺省时由服务端填充默认值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmMonitorMetricDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 指标名称 */
    @NotBlank(message = "指标名称不能为空")
    @Size(max = 200, message = "指标名称长度不能超过 200")
    private String metricName;

    /** 指标编码 (全局唯一) */
    @NotBlank(message = "指标编码不能为空")
    @Size(max = 50, message = "指标编码长度不能超过 50")
    private String metricCode;

    /** 指标分组: SYSTEM/BUSINESS/PERFORMANCE/AVAILABILITY/SECURITY/RESOURCE/API/DATABASE/CACHE/QUEUE */
    @NotBlank(message = "指标分组不能为空")
    @Pattern(regexp = "SYSTEM|BUSINESS|PERFORMANCE|AVAILABILITY|SECURITY|RESOURCE|API|DATABASE|CACHE|QUEUE",
            message = "指标分组仅支持 SYSTEM/BUSINESS/PERFORMANCE/AVAILABILITY/SECURITY/RESOURCE/API/DATABASE/CACHE/QUEUE")
    private String metricGroup;

    /** 指标类型: GAUGE/COUNTER/HISTOGRAM/TIMER/SUMMARY */
    @NotBlank(message = "指标类型不能为空")
    @Pattern(regexp = "GAUGE|COUNTER|HISTOGRAM|TIMER|SUMMARY",
            message = "指标类型仅支持 GAUGE/COUNTER/HISTOGRAM/TIMER/SUMMARY")
    private String metricType;

    /** 单位 (可空) */
    @Size(max = 50, message = "单位长度不能超过 50")
    private String unit;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 当前值 (查询返回) */
    private Double currentValue;

    /** 最小值 (查询返回) */
    private Double minValue;

    /** 最大值 (查询返回) */
    private Double maxValue;

    /** 平均值 (查询返回) */
    private Double avgValue;

    /** 目标值 (可空) */
    private Double targetValue;

    /** 预警阈值 (可空) */
    private Double warningThreshold;

    /** 严重阈值 (可空) */
    private Double criticalThreshold;

    /** 阈值方向: ABOVE/BELOW/RANGE (默认 ABOVE) */
    @Pattern(regexp = "ABOVE|BELOW|RANGE", message = "阈值方向仅支持 ABOVE/BELOW/RANGE")
    private String thresholdDirection;

    /** 采集间隔秒 (默认 60) */
    private Integer collectionIntervalSeconds;

    /** 最近采集时间 (查询返回) */
    private LocalDateTime lastCollectedAt;

    /** 最近值时间 (查询返回) */
    private LocalDateTime lastValueAt;

    /** 采集方式: POLLING/PUSH/CALCULATED/EXTERNAL (默认 POLLING) */
    @Pattern(regexp = "POLLING|PUSH|CALCULATED|EXTERNAL",
            message = "采集方式仅支持 POLLING/PUSH/CALCULATED/EXTERNAL")
    private String collectionMethod;

    /** 数据源 (可空) */
    @Size(max = 200, message = "数据源长度不能超过 200")
    private String dataSource;

    /** 查询表达式 (可空) */
    @Size(max = 1000, message = "查询表达式长度不能超过 1000")
    private String queryExpression;

    /** 标签 (可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 是否启用 (默认 TRUE) */
    private Boolean enabled;

    /** 是否告警激活 (查询返回) */
    private Boolean isAlertActive;

    /** 最近告警时间 (查询返回) */
    private LocalDateTime lastAlertAt;

    /** 告警次数 (查询返回) */
    private Integer alertCount;

    /** 历史数据 JSON (可空) */
    private String historyData;

    /** 附加数据 JSON (可空) */
    private String metadata;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
