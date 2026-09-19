/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCalendarHolidayDto.java
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
 * SCRM 营销日历节日/纪念日 DTO。
 * <p>
 * 对应 {@code ScrmCalendarHolidayEntity} 的业务字段, 用于节日创建/更新接口入参。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCalendarHolidayDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 节日名称 */
    @NotBlank(message = "节日名称不能为空")
    @Size(max = 100, message = "节日名称长度不能超过 100")
    private String holidayName;

    /** 节日类型: PUBLIC_HOLIDAY/TRADITIONAL_FESTIVAL/E_COMMERCE/SEASONAL/CUSTOM */
    @NotBlank(message = "节日类型不能为空")
    @Pattern(regexp = "PUBLIC_HOLIDAY|TRADITIONAL_FESTIVAL|E_COMMERCE|SEASONAL|CUSTOM",
            message = "节日类型仅支持 PUBLIC_HOLIDAY/TRADITIONAL_FESTIVAL/E_COMMERCE/SEASONAL/CUSTOM")
    private String holidayType;

    /** 节日日期: 固定 MM-dd 或具体 yyyy-MM-dd */
    @NotBlank(message = "节日日期不能为空")
    @Size(max = 20, message = "节日日期长度不能超过 20")
    private String holidayDate;

    /** 农历日期 (可空) */
    @Size(max = 20, message = "农历日期长度不能超过 20")
    private String lunarDate;

    /** 是否农历节日 (默认 FALSE) */
    private Boolean isLunar;

    /** 持续天数 (默认 1) */
    private Integer durationDays;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 营销机会: HIGH/MEDIUM/LOW/NONE (默认 MEDIUM) */
    @Pattern(regexp = "HIGH|MEDIUM|LOW|NONE|",
            message = "营销机会仅支持 HIGH/MEDIUM/LOW/NONE")
    private String marketingOpportunity;

    /** 建议营销动作 (可空) */
    @Size(max = 500, message = "建议营销动作长度不能超过 500")
    private String suggestedActions;

    /** 建议渠道 (可空) */
    @Size(max = 500, message = "建议渠道长度不能超过 500")
    private String suggestedChannels;

    /** 国家 (默认 CN) */
    @Size(max = 50, message = "国家长度不能超过 50")
    private String country;

    /** 地区 (可空) */
    @Size(max = 100, message = "地区长度不能超过 100")
    private String region;

    /** 是否启用 (默认 TRUE) */
    private Boolean isActive;

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
