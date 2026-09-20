/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCalendarEventDto.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * SCRM 营销日历事件 DTO。
 * <p>
 * 对应 {@code ScrmCalendarEventEntity} 的业务字段, 用于事件创建/更新接口入参。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCalendarEventDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 事件标题 */
    @NotBlank(message = "事件标题不能为空")
    @Size(max = 200, message = "事件标题长度不能超过 200")
    private String eventTitle;

    /** 事件类型: CAMPAIGN/PROMOTION/HOLIDAY/FESTIVAL/ANNIVERSARY/CONTENT_PUBLISH/LIVE_STREAMING/PRODUCT_LAUNCH/SALES_TARGET/MEETING/REMINDER/CUSTOM */
    @NotBlank(message = "事件类型不能为空")
    @Size(max = 30, message = "事件类型长度不能超过 30")
    private String eventType;

    /** 事件描述 (可空) */
    @Size(max = 500, message = "事件描述长度不能超过 500")
    private String description;

    /** 开始日期 */
    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    /** 结束日期 */
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    /** 开始时间 (可空) */
    private LocalTime startTime;

    /** 结束时间 (可空) */
    private LocalTime endTime;

    /** 是否全天事件 (默认 TRUE) */
    private Boolean isAllDay;

    /** 是否重复事件 (默认 FALSE) */
    private Boolean isRecurring;

    /** 重复类型: DAILY/WEEKLY/MONTHLY/YEARLY (可空) */
    @Pattern(regexp = "DAILY|WEEKLY|MONTHLY|YEARLY|",
            message = "重复类型仅支持 DAILY/WEEKLY/MONTHLY/YEARLY")
    private String recurringType;

    /** 重复配置 JSON (可空) */
    private String recurringConfig;

    /** 渠道 (逗号分隔, 可空) */
    @Size(max = 500, message = "渠道长度不能超过 500")
    private String channels;

    /** 关联营销活动 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long campaignId;

    /** 关联内容 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contentId;

    /** 目标客群 (可空) */
    @Size(max = 500, message = "目标客群长度不能超过 500")
    private String targetSegment;

    /** 状态: PLANNED/CONFIRMED/IN_PROGRESS/COMPLETED/CANCELLED/POSTPONED */
    @Pattern(regexp = "PLANNED|CONFIRMED|IN_PROGRESS|COMPLETED|CANCELLED|POSTPONED|",
            message = "状态仅支持 PLANNED/CONFIRMED/IN_PROGRESS/COMPLETED/CANCELLED/POSTPONED")
    private String status;

    /** 优先级 (默认 0) */
    private Integer priority;

    /** 日历颜色 (可空) */
    @Size(max = 20, message = "颜色长度不能超过 20")
    private String color;

    /** 标签 (可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 地点 (可空) */
    @Size(max = 200, message = "地点长度不能超过 200")
    private String location;

    /** 负责人 ID (可空) */
    @Size(max = 100, message = "负责人 ID 长度不能超过 100")
    private String ownerId;

    /** 负责人名称 (可空) */
    @Size(max = 100, message = "负责人名称长度不能超过 100")
    private String ownerName;

    /** 团队 ID (可空) */
    @Size(max = 100, message = "团队 ID 长度不能超过 100")
    private String teamId;

    /** 预算 (默认 0) */
    private Double budget;

    /** 预计触达 (默认 0) */
    private Integer estimatedReach;

    /** 实际触达 (默认 0) */
    private Integer actualReach;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

    /** 提前提醒分钟数 (默认 0) */
    private Integer reminderMinutes;

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
