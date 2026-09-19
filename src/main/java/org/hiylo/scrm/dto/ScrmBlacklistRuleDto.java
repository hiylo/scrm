/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBlacklistRuleDto.java
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
 * SCRM 黑名单风控规则 DTO。
 * <p>
 * 用于规则增删改查接口入参与返回。ruleName / ruleCode / ruleType / riskCategory /
 * conditionField / conditionOperator / conditionValue 必填, severity / action /
 * enabled / priority 缺省时由 Service 填默认值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmBlacklistRuleDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 规则代码 (唯一) */
    @NotBlank(message = "规则代码不能为空")
    @Size(max = 50, message = "规则代码长度不能超过 50")
    private String ruleCode;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

/** 规则类型: FREQUENCY / AMOUNT / BEHAVIOR / PATTERN / BLACKLIST_MATCH / COMPOSITE / TIME / LOCATION / DEVICE /
         * TRANSACTION */
    @NotBlank(message = "规则类型不能为空")
    @Size(max = 30, message = "规则类型长度不能超过 30")
    private String ruleType;

    /** 风险类别: FRAUD / ABUSE / SPAM / HARASSMENT / FAKE / VIOLATION / POLICY / SECURITY */
    @NotBlank(message = "风险类别不能为空")
    @Size(max = 50, message = "风险类别长度不能超过 50")
    private String riskCategory;

    /** 检查字段 */
    @NotBlank(message = "检查字段不能为空")
    @Size(max = 100, message = "检查字段长度不能超过 100")
    private String conditionField;

    /** 条件操作符: GT / GTE / LT / LTE / EQ / NE / CONTAINS / NOT_CONTAINS / IN / NOT_IN / REGEX / MATCH */
    @NotBlank(message = "条件操作符不能为空")
    @Pattern(regexp = "GT|GTE|LT|LTE|EQ|NE|CONTAINS|NOT_CONTAINS|IN|NOT_IN|REGEX|MATCH",
            message = "条件操作符仅支持 GT/GTE/LT/LTE/EQ/NE/CONTAINS/NOT_CONTAINS/IN/NOT_IN/REGEX/MATCH")
    private String conditionOperator;

    /** 条件值 */
    @NotBlank(message = "条件值不能为空")
    @Size(max = 1000, message = "条件值长度不能超过 1000")
    private String conditionValue;

    /** 第二条件值 (可空) */
    @Size(max = 500, message = "第二条件值长度不能超过 500")
    private String conditionValue2;

    /** 时间窗口分钟 (可空) */
    private Integer timeWindowMinutes;

    /** 阈值次数 (可空) */
    private Integer thresholdCount;

    /** 阈值金额 (可空) */
    private Double thresholdAmount;

    /** 严重程度: LOW / MEDIUM / HIGH / CRITICAL */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL",
            message = "严重程度仅支持 LOW/MEDIUM/HIGH/CRITICAL")
    private String severity;

    /** 执行动作: ALERT / BLOCK / REVIEW / QUARANTINE / AUTO_BLACKLIST / NOTIFY */
    @Pattern(regexp = "ALERT|BLOCK|REVIEW|QUARANTINE|AUTO_BLACKLIST|NOTIFY",
            message = "执行动作仅支持 ALERT/BLOCK/REVIEW/QUARANTINE/AUTO_BLACKLIST/NOTIFY")
    private String action;

    /** JSON 执行参数 (可空) */
    @Size(max = 1000, message = "执行参数长度不能超过 1000")
    private String actionParams;

    /** 适用模块 (可空) */
    @Size(max = 500, message = "适用模块长度不能超过 500")
    private String applicableModules;

    /** 适用场景 (可空) */
    @Size(max = 500, message = "适用场景长度不能超过 500")
    private String applicableScenarios;

    /** 匹配后加入名单类型 (可空): BLACKLIST / GRAYLIST / WATCHLIST */
    @Pattern(regexp = "BLACKLIST|GRAYLIST|WATCHLIST",
            message = "匹配名单类型仅支持 BLACKLIST/GRAYLIST/WATCHLIST")
    private String targetListType;

    /** 通知渠道 (可空) */
    @Size(max = 500, message = "通知渠道长度不能超过 500")
    private String notificationChannels;

    /** 通知接收人 (可空) */
    @Size(max = 500, message = "通知接收人长度不能超过 500")
    private String notificationRecipients;

    /** 是否启用 (默认 TRUE) */
    private Boolean enabled;

    /** 优先级 (默认 0) */
    private Integer priority;

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
