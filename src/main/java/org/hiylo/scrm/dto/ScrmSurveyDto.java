/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSurveyDto.java
 * Date : 2026/08/04 08:40:58
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 满意度调查问卷 DTO。
 * <p>
 * 对应 {@code ScrmSurveyEntity} 的业务字段, 创建/更新接口入参。surveyType 标注调查类型
 * (NPS/CSAT/CES/CUSTOM); questions 为 JSON 字符串描述题目列表
 * (结构: {@code [{id,type,text,options,required,scale}]}); status 标注问卷生命周期
 * (DRAFT/ACTIVE/PAUSED/COMPLETED/ARCHIVED)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmSurveyDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 调查名称 */
    @NotBlank(message = "调查名称不能为空")
    @Size(max = 200, message = "调查名称长度不能超过 200")
    private String surveyName;

    /** 调查类型: NPS 净推荐值 / CSAT 满意度 / CES 客户费力指数 / CUSTOM 自定义 */
    @NotBlank(message = "调查类型不能为空")
    @Pattern(regexp = "NPS|CSAT|CES|CUSTOM",
            message = "调查类型仅支持 NPS/CSAT/CES/CUSTOM")
    private String surveyType;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 调查标题 */
    @NotBlank(message = "调查标题不能为空")
    @Size(max = 200, message = "调查标题长度不能超过 200")
    private String title;

    /** 介绍文案 (可空) */
    private String introText;

    /** 结束文案 (可空) */
    private String outroText;

    /** JSON 问题列表: [{id,type,text,options,required,scale}] */
    @NotBlank(message = "问题列表不能为空")
    private String questions;

    /** 量表类型: NPS_0_10 / CSAT_1_5 / CES_1_7 (可空) */
    @Pattern(regexp = "NPS_0_10|CSAT_1_5|CES_1_7",
            message = "量表类型仅支持 NPS_0_10/CSAT_1_5/CES_1_7")
    private String scaleType;

    /** 触发事件: PURCHASE / SERVICE_TICKET / FIRST_CONTACT / MANUAL (可空) */
    @Pattern(regexp = "PURCHASE|SERVICE_TICKET|FIRST_CONTACT|MANUAL",
            message = "触发事件仅支持 PURCHASE/SERVICE_TICKET/FIRST_CONTACT/MANUAL")
    private String triggerEvent;

    /** 触发延迟小时 (可空, 默认 0) */
    private Integer triggerDelayHours;

    /** 目标客群条件 JSON (可空) */
    @Size(max = 500, message = "目标客群条件长度不能超过 500")
    private String targetSegment;

    /** 分发渠道: IN_APP / SMS / EMAIL / WECHAT (可空) */
    @Size(max = 200, message = "分发渠道长度不能超过 200")
    private String channels;

    /** 预计完成时间分钟 (可空, 默认 2) */
    private Integer estimatedTimeMinutes;

    /** 开始日期 (可空) */
    private LocalDate startDate;

    /** 结束日期 (可空) */
    private LocalDate endDate;

    /** 状态: DRAFT / ACTIVE / PAUSED / COMPLETED / ARCHIVED (创建时可选, 默认 DRAFT) */
    @Pattern(regexp = "DRAFT|ACTIVE|PAUSED|COMPLETED|ARCHIVED",
            message = "状态仅支持 DRAFT/ACTIVE/PAUSED/COMPLETED/ARCHIVED")
    private String status;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 回复数 (查询返回) */
    private Integer responseCount;

    /** 完成率 (查询返回) */
    private Double completionRate;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
