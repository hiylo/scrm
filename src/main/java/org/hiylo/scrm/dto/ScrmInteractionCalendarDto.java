/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInteractionCalendarDto.java
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
 * SCRM 客户互动日历 DTO。
 * <p>
 * 对应 {@code ScrmInteractionCalendarEntity} 的业务字段, 用于日历创建/更新接口入参。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmInteractionCalendarDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 日历名称 */
    @NotBlank(message = "日历名称不能为空")
    @Size(max = 200, message = "日历名称长度不能超过 200")
    private String calendarName;

    /** 日历编码 (唯一) */
    @NotBlank(message = "日历编码不能为空")
    @Size(max = 50, message = "日历编码长度不能超过 50")
    private String calendarCode;

    /** 日历描述 (可空) */
    @Size(max = 500, message = "日历描述长度不能超过 500")
    private String description;

    /** 日历类型: PERSONAL/TEAM/DEPARTMENT/COMPANY/CUSTOMER (默认 PERSONAL) */
    @Pattern(regexp = "PERSONAL|TEAM|DEPARTMENT|COMPANY|CUSTOMER|",
            message = "日历类型仅支持 PERSONAL/TEAM/DEPARTMENT/COMPANY/CUSTOMER")
    private String calendarType;

    /** 所有者 ID */
    @NotBlank(message = "所有者 ID 不能为空")
    @Size(max = 100, message = "所有者 ID 长度不能超过 100")
    private String ownerId;

    /** 所有者名称 (可空) */
    @Size(max = 100, message = "所有者名称长度不能超过 100")
    private String ownerName;

    /** 共享给用户 ID 列表 (逗号分隔, 可空) */
    @Size(max = 500, message = "共享用户长度不能超过 500")
    private String sharedWith;

    /** 是否公开 (默认 FALSE) */
    private Boolean isPublic;

    /** 日历颜色 (可空) */
    @Size(max = 20, message = "日历颜色长度不能超过 20")
    private String color;

    /** 图标 (可空) */
    @Size(max = 200, message = "图标长度不能超过 200")
    private String icon;

    /** 工作时间开始 (HH:mm, 默认 09:00) */
    @Size(max = 10, message = "工作时间开始长度不能超过 10")
    private String workingHoursStart;

    /** 工作时间结束 (HH:mm, 默认 18:00) */
    @Size(max = 10, message = "工作时间结束长度不能超过 10")
    private String workingHoursEnd;

    /** 工作日 (逗号分隔, 默认 1,2,3,4,5) */
    @Size(max = 20, message = "工作日长度不能超过 20")
    private String workingDays;

    /** 时区 (默认 Asia/Shanghai) */
    @Size(max = 50, message = "时区长度不能超过 50")
    private String timezone;

    /** 默认提前提醒分钟数 (默认 15) */
    private Integer defaultReminderMinutes;

    /** 默认时长分钟数 (默认 60) */
    private Integer defaultDurationMinutes;

    /** 是否启用 (默认 TRUE) */
    private Boolean enabled;

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
