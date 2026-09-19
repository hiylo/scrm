/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVisitPlanDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 客户回访计划 DTO。
 * <p>
 * 对应 {@code ScrmVisitPlanEntity} 的业务字段, 用于计划创建/更新接口入参。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmVisitPlanDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 计划名称 */
    @NotBlank(message = "计划名称不能为空")
    @Size(max = 200, message = "计划名称长度不能超过 200")
    private String planName;

    /** 计划编码 (唯一) */
    @NotBlank(message = "计划编码不能为空")
    @Size(max = 50, message = "计划编码长度不能超过 50")
    private String planCode;

    /** 计划描述 (可空) */
    @Size(max = 500, message = "计划描述长度不能超过 500")
    private String description;

    /** 回访类型: REGULAR/FOLLOW_UP/SATISFACTION/RENEWAL/UPSELL/CROSS_SELL/CARE/COMPLAINT_FOLLOWUP/CUSTOM */
    @NotBlank(message = "回访类型不能为空")
    @Size(max = 30, message = "回访类型长度不能超过 30")
    private String planType;

    /** 目标类型: CUSTOMER/CUSTOMER_LEVEL/SEGMENT/ALL */
    @NotBlank(message = "目标类型不能为空")
    @Pattern(regexp = "CUSTOMER|CUSTOMER_LEVEL|SEGMENT|ALL",
            message = "目标类型仅支持 CUSTOMER/CUSTOMER_LEVEL/SEGMENT/ALL")
    private String targetType;

    /** 目标条件 JSON (可空, {customerLevel,segment,tags,region}) */
    private String targetCriteria;

    /** 回访频率: ONCE/DAILY/WEEKLY/BIWEEKLY/MONTHLY/QUARTERLY/SEMIANNUALLY/ANNUALLY */
    @NotBlank(message = "回访频率不能为空")
    @Pattern(regexp = "ONCE|DAILY|WEEKLY|BIWEEKLY|MONTHLY|QUARTERLY|SEMIANNUALLY|ANNUALLY",
            message = "回访频率仅支持 ONCE/DAILY/WEEKLY/BIWEEKLY/MONTHLY/QUARTERLY/SEMIANNUALLY/ANNUALLY")
    private String visitFrequency;

    /** 频率配置 JSON (可空, {dayOfWeek,dayOfMonth,time}) */
    private String frequencyConfig;

    /** 回访方式: PHONE/ON_SITE/VIDEO/WECHAT/EMAIL/SMS/MIXED (默认 PHONE) */
    @Pattern(regexp = "PHONE|ON_SITE|VIDEO|WECHAT|EMAIL|SMS|MIXED|",
            message = "回访方式仅支持 PHONE/ON_SITE/VIDEO/WECHAT/EMAIL/SMS/MIXED")
    private String visitMethod;

    /** 关联回访模板 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    /** 默认负责人 (可空) */
    @Size(max = 100, message = "默认负责人长度不能超过 100")
    private String assignedTo;

    /** 团队 ID (可空) */
    @Size(max = 100, message = "团队 ID 长度不能超过 100")
    private String teamId;

    /** 开始日期 */
    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    /** 结束日期 (可空, 为空表示长期计划) */
    private LocalDate endDate;

    /** 状态: ACTIVE/PAUSED/COMPLETED/EXPIRED */
    @Pattern(regexp = "ACTIVE|PAUSED|COMPLETED|EXPIRED|",
            message = "状态仅支持 ACTIVE/PAUSED/COMPLETED/EXPIRED")
    private String status;

    /** 优先级 (默认 0) */
    private Integer priority;

    /** 标签 (逗号分隔, 可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 是否自动生成任务 (默认 FALSE) */
    private Boolean autoGenerate;

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
