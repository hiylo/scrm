/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAlertEventDto.java
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
 * SCRM 告警事件 DTO。
 * <p>
 * 对应 {@code ScrmAlertEventEntity} 的业务字段, 主要用于查询返回与手动触发场景。
 * severity / status 以枚举字符串校验合法性。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAlertEventDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 事件编号 (全局唯一) */
    @Size(max = 100, message = "事件编号长度不能超过 100")
    private String eventNo;

    /** 规则 ID (可空) */
    private Long ruleId;

    /** 规则名称 (可空) */
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 规则编码 (可空) */
    @Size(max = 50, message = "规则编码长度不能超过 50")
    private String ruleCode;

    /** 指标 ID (可空) */
    private Long metricId;

    /** 指标名称 (可空) */
    @Size(max = 200, message = "指标名称长度不能超过 200")
    private String metricName;

    /** 指标编码 (可空) */
    @Size(max = 50, message = "指标编码长度不能超过 50")
    private String metricCode;

    /** 严重程度: INFO/WARNING/CRITICAL/FATAL */
    @NotBlank(message = "严重程度不能为空")
    @Pattern(regexp = "INFO|WARNING|CRITICAL|FATAL", message = "严重程度仅支持 INFO/WARNING/CRITICAL/FATAL")
    private String severity;

    /** 状态: FIRING/PENDING/RESOLVED/ACKNOWLEDGED/SUPPRESSED/EXPIRED (默认 FIRING) */
    @Pattern(regexp = "FIRING|PENDING|RESOLVED|ACKNOWLEDGED|SUPPRESSED|EXPIRED",
            message = "状态仅支持 FIRING/PENDING/RESOLVED/ACKNOWLEDGED/SUPPRESSED/EXPIRED")
    private String status;

    /** 触发值 (默认 0) */
    private Double triggerValue;

    /** 阈值 (默认 0) */
    private Double thresholdValue;

    /** 条件操作符 (可空) */
    @Size(max = 20, message = "条件操作符长度不能超过 20")
    private String condition;

    /** 触发时间 */
    private LocalDateTime triggerTime;

    /** 恢复时间 (可空) */
    private LocalDateTime resolvedTime;

    /** 持续秒 (默认 0) */
    private Integer durationSeconds;

    /** 触发次数 (默认 1) */
    private Integer fireCount;

    /** 告警标题 */
    @NotBlank(message = "告警标题不能为空")
    @Size(max = 500, message = "告警标题长度不能超过 500")
    private String title;

    /** 告警消息 (可空) */
    @Size(max = 2000, message = "告警消息长度不能超过 2000")
    private String message;

    /** 详细描述 (可空) */
    private String description;

    /** 根因分析 (可空) */
    @Size(max = 1000, message = "根因分析长度不能超过 1000")
    private String rootCauseAnalysis;

    /** 影响分析 (可空) */
    @Size(max = 1000, message = "影响分析长度不能超过 1000")
    private String impactAnalysis;

    /** 受影响服务 (可空) */
    @Size(max = 500, message = "受影响服务长度不能超过 500")
    private String affectedServices;

    /** 受影响用户数 (默认 0) */
    private Integer affectedUsers;

    /** 确认人 (可空) */
    @Size(max = 100, message = "确认人长度不能超过 100")
    private String acknowledgedBy;

    /** 确认时间 (可空) */
    private LocalDateTime acknowledgedAt;

    /** 确认备注 (可空) */
    @Size(max = 500, message = "确认备注长度不能超过 500")
    private String acknowledgeNote;

    /** 恢复人 (可空) */
    @Size(max = 100, message = "恢复人长度不能超过 100")
    private String resolvedBy;

    /** 恢复备注 (可空) */
    @Size(max = 500, message = "恢复备注长度不能超过 500")
    private String resolvedNote;

    /** 恢复方式: AUTO/MANUAL/MAINTENANCE/SUPPRESSED (可空) */
    @Pattern(regexp = "AUTO|MANUAL|MAINTENANCE|SUPPRESSED",
            message = "恢复方式仅支持 AUTO/MANUAL/MAINTENANCE/SUPPRESSED")
    private String resolutionType;

    /** 通知发送数 (默认 0) */
    private Integer notificationsSent;

    /** 通知失败数 (默认 0) */
    private Integer notificationFailures;

    /** 最近通知时间 (可空) */
    private LocalDateTime lastNotificationAt;

    /** 是否已升级 (默认 FALSE) */
    private Boolean escalated;

    /** 升级时间 (可空) */
    private LocalDateTime escalatedAt;

    /** 升级接收人 (可空) */
    @Size(max = 500, message = "升级接收人长度不能超过 500")
    private String escalatedTo;

    /** 行动项 (可空) */
    @Size(max = 1000, message = "行动项长度不能超过 1000")
    private String actionItems;

    /** 附加数据 JSON (可空) */
    private String metadata;

    /** 相关联事件 (可空) */
    @Size(max = 500, message = "相关联事件长度不能超过 500")
    private String relatedEventIds;

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
