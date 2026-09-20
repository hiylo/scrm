/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAlertRuleDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 告警规则 DTO。
 * <p>
 * 对应 {@code ScrmAlertRuleEntity} 的业务字段, 创建/更新接口入参。
 * condition / severity 以枚举字符串校验合法性;
 * durationSeconds / evaluationPeriods / cooldownMinutes / escalationAfterMinutes
 * 缺省时由服务端填充默认值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAlertRuleDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 规则编码 (全局唯一) */
    @NotBlank(message = "规则编码不能为空")
    @Size(max = 50, message = "规则编码长度不能超过 50")
    private String ruleCode;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 关联指标 ID */
    @NotNull(message = "关联指标 ID 不能为空")
    private Long metricId;

    /** 指标名称 (可空, 快照) */
    @Size(max = 200, message = "指标名称长度不能超过 200")
    private String metricName;

    /** 指标编码 (可空, 快照) */
    @Size(max = 50, message = "指标编码长度不能超过 50")
    private String metricCode;

    /** 条件操作符: GT/GTE/LT/LTE/EQ/NE/CONTAINS/NOT_CONTAINS */
    @NotBlank(message = "条件操作符不能为空")
    @Pattern(regexp = "GT|GTE|LT|LTE|EQ|NE|CONTAINS|NOT_CONTAINS",
            message = "条件操作符仅支持 GT/GTE/LT/LTE/EQ/NE/CONTAINS/NOT_CONTAINS")
    private String condition;

    /** 阈值 */
    @NotNull(message = "阈值不能为空")
    private Double thresholdValue;

    /** 第二阈值 (范围用, 默认 0) */
    private Double thresholdValue2;

    /** 严重程度: INFO/WARNING/CRITICAL/FATAL (默认 WARNING) */
    @Pattern(regexp = "INFO|WARNING|CRITICAL|FATAL", message = "严重程度仅支持 INFO/WARNING/CRITICAL/FATAL")
    private String severity;

    /** 持续时间秒 (0=立即, 默认 0) */
    private Integer durationSeconds;

    /** 连续触发次数 (默认 1) */
    private Integer evaluationPeriods;

    /** 冷却分钟 (默认 30) */
    private Integer cooldownMinutes;

    /** 通知渠道 (逗号分隔): EMAIL/SMS/WECHAT/WEBHOOK/APP_PUSH/PHONE */
    @NotBlank(message = "通知渠道不能为空")
    @Size(max = 500, message = "通知渠道长度不能超过 500")
    private String notificationChannels;

    /** 通知模板 ID (可空) */
    private Long notificationTemplateId;

    /** 接收人 (可空, 逗号分隔) */
    @Size(max = 1000, message = "接收人长度不能超过 1000")
    private String recipients;

    /** 升级接收人 (可空) */
    @Size(max = 1000, message = "升级接收人长度不能超过 1000")
    private String escalationRecipients;

    /** 升级时间分钟 (默认 60) */
    private Integer escalationAfterMinutes;

    /** 自动恢复 (默认 TRUE) */
    private Boolean autoResolve;

    /** 自动恢复消息 (可空) */
    @Size(max = 500, message = "自动恢复消息长度不能超过 500")
    private String autoResolveMessage;

    /** 是否启用 (默认 TRUE) */
    private Boolean enabled;

    /** 触发次数 (查询返回) */
    private Integer triggerCount;

    /** 最近触发时间 (查询返回) */
    private LocalDateTime lastTriggeredAt;

    /** 最近恢复时间 (查询返回) */
    private LocalDateTime lastResolvedAt;

    /** 标签 (可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

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
