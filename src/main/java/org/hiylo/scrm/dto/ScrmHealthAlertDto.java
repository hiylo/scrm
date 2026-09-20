/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmHealthAlertDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户健康度告警 DTO。
 * <p>
 * 对应 {@code ScrmHealthAlertEntity} 的业务字段, 创建/更新接口入参与查询返回。
 * metadata 为 JSON 字符串, 携带附加数据 (如客户当时评分快照)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmHealthAlertDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 告警名称 */
    @NotBlank(message = "告警名称不能为空")
    @Size(max = 200, message = "告警名称长度不能超过 200")
    private String alertName;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    private String customerName;

    /** 关联健康度评分 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long healthScoreId;

/** 告警类型: SCORE_DROP / LOW_SCORE / INACTIVITY / PAYMENT_ISSUE / CHURN_RISK / SUPPORT_OVERLOAD / RISK_FACTOR /
         * THRESHOLD_BREACH */
    @NotBlank(message = "告警类型不能为空")
    private String alertType;

    /** 严重程度: INFO / WARNING / URGENT / CRITICAL (默认 WARNING) */
    private String severity;

    /** 触发值 */
    private Double triggerValue;

    /** 阈值值 */
    private Double thresholdValue;

    /** 触发条件 (可空) */
    private String condition;

    /** 告警描述 (可空) */
    private String description;

    /** 风险因素 (逗号分隔, 可空) */
    private String riskFactors;

    /** 建议动作 (逗号分隔, 可空) */
    private String recommendedActions;

    /** 状态: ACTIVE / ACKNOWLEDGED / RESOLVED / DISMISSED (默认 ACTIVE) */
    private String status;

    /** 分配给 (可空) */
    private String assignedTo;

    /** 分配时间 (可空) */
    private LocalDateTime assignedAt;

    /** 确认人 (可空) */
    private String acknowledgedBy;

    /** 确认时间 (可空) */
    private LocalDateTime acknowledgedAt;

    /** 解决人 (可空) */
    private String resolvedBy;

    /** 解决时间 (可空) */
    private LocalDateTime resolvedAt;

    /** 解决备注 (可空) */
    private String resolutionNote;

    /** 触发时间 */
    private LocalDateTime triggeredAt;

    /** 附加数据 JSON (可空) */
    private String metadata;

    /** 创建人 (可空) */
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
