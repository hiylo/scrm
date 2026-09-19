/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInteractionPlanDto.java
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
 * SCRM 客户互动计划 DTO。
 * <p>
 * 对应 {@code ScrmInteractionPlanEntity} 的业务字段, 用于计划创建/更新接口入参。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmInteractionPlanDto {

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

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 互动类型: CALL/EMAIL/WECHAT/MEETING/VISIT/FOLLOW_UP/REVIEW/GREETING/GIFT/OTHER */
    @NotBlank(message = "互动类型不能为空")
    @Pattern(regexp = "CALL|EMAIL|WECHAT|MEETING|VISIT|FOLLOW_UP|REVIEW|GREETING|GIFT|OTHER",
            message = "互动类型仅支持 CALL/EMAIL/WECHAT/MEETING/VISIT/FOLLOW_UP/REVIEW/GREETING/GIFT/OTHER")
    private String interactionType;

    /** 互动方式: PHONE/VIDEO/ONSITE/ONLINE/MESSAGE/MAIL */
    @NotBlank(message = "互动方式不能为空")
    @Pattern(regexp = "PHONE|VIDEO|ONSITE|ONLINE|MESSAGE|MAIL",
            message = "互动方式仅支持 PHONE/VIDEO/ONSITE/ONLINE/MESSAGE/MAIL")
    private String interactionMethod;

    /** 互动主题 */
    @NotBlank(message = "互动主题不能为空")
    @Size(max = 500, message = "互动主题长度不能超过 500")
    private String title;

    /** 互动内容 (可空) */
    @Size(max = 2000, message = "互动内容长度不能超过 2000")
    private String content;

    /** 互动目标 (可空) */
    @Size(max = 1000, message = "互动目标长度不能超过 1000")
    private String objectives;

    /** 准备材料 (可空) */
    @Size(max = 1000, message = "准备材料长度不能超过 1000")
    private String prepareMaterials;

    /** 计划开始时间 */
    @NotNull(message = "计划开始时间不能为空")
    private LocalDateTime scheduledStart;

    /** 计划结束时间 (可空) */
    private LocalDateTime scheduledEnd;

    /** 时区 (默认 Asia/Shanghai) */
    @Size(max = 50, message = "时区长度不能超过 50")
    private String timezone;

    /** 互动地点 (可空) */
    @Size(max = 500, message = "互动地点长度不能超过 500")
    private String location;

    /** 地点类型: OFFICE/CUSTOMER_SITE/ONLINE/PHONE/OTHER (可空) */
    @Pattern(regexp = "OFFICE|CUSTOMER_SITE|ONLINE|PHONE|OTHER|",
            message = "地点类型仅支持 OFFICE/CUSTOMER_SITE/ONLINE/PHONE/OTHER")
    private String locationType;

    /** 负责人 ID */
    @NotBlank(message = "负责人 ID 不能为空")
    @Size(max = 100, message = "负责人 ID 长度不能超过 100")
    private String ownerId;

    /** 负责人名称 (可空) */
    @Size(max = 100, message = "负责人名称长度不能超过 100")
    private String ownerName;

    /** 参与人 ID 列表 (逗号分隔, 可空) */
    @Size(max = 500, message = "参与人长度不能超过 500")
    private String participantIds;

    /** 客户联系人 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerContactId;

    /** 客户联系人名称 (可空) */
    @Size(max = 200, message = "客户联系人名称长度不能超过 200")
    private String customerContactName;

    /** 提醒类型: NONE/NOTIFICATION/EMAIL/SMS/ALL (默认 NOTIFICATION) */
    @Pattern(regexp = "NONE|NOTIFICATION|EMAIL|SMS|ALL|",
            message = "提醒类型仅支持 NONE/NOTIFICATION/EMAIL/SMS/ALL")
    private String reminderType;

    /** 提前提醒分钟数 (默认 15) */
    private Integer reminderMinutesBefore;

    /** 重复类型: NONE/DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY/CUSTOM (默认 NONE) */
    @Pattern(regexp = "NONE|DAILY|WEEKLY|MONTHLY|QUARTERLY|YEARLY|CUSTOM|",
            message = "重复类型仅支持 NONE/DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY/CUSTOM")
    private String repeatType;

    /** 重复间隔 (默认 1) */
    private Integer repeatInterval;

    /** 重复结束日期 (可空) */
    private LocalDate repeatEndDate;

    /** 最大重复次数 (默认 0, 0 表示无限) */
    private Integer maxRepeatCount;

    /** 周几重复 (如 1,3,5, 可空) */
    @Size(max = 20, message = "周几重复长度不能超过 20")
    private String weekDays;

    /** 每月几号重复 (可空) */
    private Integer monthDay;

    /** 优先级: LOW/MEDIUM/HIGH/URGENT (默认 MEDIUM) */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT|",
            message = "优先级仅支持 LOW/MEDIUM/HIGH/URGENT")
    private String priority;

    /** 状态: PLANNED/CONFIRMED/IN_PROGRESS/COMPLETED/CANCELLED/RESCHEDULED/NO_SHOW */
    @Pattern(regexp = "PLANNED|CONFIRMED|IN_PROGRESS|COMPLETED|CANCELLED|RESCHEDULED|NO_SHOW|",
            message = "状态仅支持 PLANNED/CONFIRMED/IN_PROGRESS/COMPLETED/CANCELLED/RESCHEDULED/NO_SHOW")
    private String status;

    /** 标签 (逗号分隔, 可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 日历颜色 (可空) */
    @Size(max = 20, message = "日历颜色长度不能超过 20")
    private String color;

    /** 是否全天事件 (默认 FALSE) */
    private Boolean isAllDay;

    /** 是否置顶 (默认 FALSE) */
    private Boolean isPinned;

    /** 附件 JSON (可空) */
    @Size(max = 1000, message = "附件长度不能超过 1000")
    private String attachments;

    /** 关联计划 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long relatedPlanId;

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
