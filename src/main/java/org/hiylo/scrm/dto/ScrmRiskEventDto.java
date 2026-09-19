/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRiskEventDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 风险事件 DTO。
 * <p>
 * 用于风险事件录入与查询接口入参与返回。eventNo 缺省时由 Service 自动生成,
 * targetType / targetValue / riskCategory / riskLevel / triggerReason 必填,
 * action / actionStatus / status 缺省时由 Service 填默认值。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmRiskEventDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 事件编号 (可空, 缺省自动生成) */
    @Size(max = 100, message = "事件编号长度不能超过 100")
    private String eventNo;

    /** 触发规则 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;

    /** 触发规则名称 (可空) */
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 触发规则代码 (可空) */
    @Size(max = 50, message = "规则代码长度不能超过 50")
    private String ruleCode;

    /** 客户 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 目标类型: CUSTOMER / PHONE / EMAIL / IP / DEVICE / ID_CARD / BANK_CARD / ADDRESS / WECHAT_ID / COMPANY */
    @NotBlank(message = "目标类型不能为空")
    @Size(max = 30, message = "目标类型长度不能超过 30")
    private String targetType;

    /** 目标值 */
    @NotBlank(message = "目标值不能为空")
    @Size(max = 500, message = "目标值长度不能超过 500")
    private String targetValue;

    /** 风险类别: FRAUD / ABUSE / SPAM / HARASSMENT / FAKE / VIOLATION / POLICY / SECURITY */
    @NotBlank(message = "风险类别不能为空")
    @Size(max = 50, message = "风险类别长度不能超过 50")
    private String riskCategory;

    /** 风险等级: LOW / MEDIUM / HIGH / CRITICAL */
    @NotBlank(message = "风险等级不能为空")
    @Size(max = 20, message = "风险等级长度不能超过 20")
    private String riskLevel;

    /** 风险评分 0-100 (可空) */
    private Double riskScore;

    /** 触发原因 */
    @NotBlank(message = "触发原因不能为空")
    @Size(max = 1000, message = "触发原因长度不能超过 1000")
    private String triggerReason;

    /** JSON 触发数据 (可空) */
    private String triggerData;

    /** 触发时间 (可空, 缺省取当前时间) */
    private LocalDateTime triggerTime;

    /** 检测者 (可空) */
    @Size(max = 100, message = "检测者长度不能超过 100")
    private String detectedBy;

    /** 检测方式 (可空): RULE / AUTO / MANUAL / EXTERNAL */
    @Size(max = 50, message = "检测方式长度不能超过 50")
    private String detectionMethod;

    /** 执行动作: ALERT / BLOCK / REVIEW / QUARANTINE / AUTO_BLACKLIST / NOTIFY */
    @Size(max = 30, message = "执行动作长度不能超过 30")
    private String action;

    /** 状态: OPEN / INVESTIGATING / CONFIRMED / FALSE_POSITIVE / RESOLVED / ESCALATED */
    @Size(max = 20, message = "状态长度不能超过 20")
    private String status;

    /** 受影响实体 (可空) */
    @Size(max = 500, message = "受影响实体长度不能超过 500")
    private String affectedEntities;

    /** 影响评估 (可空) */
    @Size(max = 1000, message = "影响评估长度不能超过 1000")
    private String impactAssessment;

    /** 标签 (可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** JSON 附加数据 (可空) */
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
