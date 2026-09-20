/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFestivalDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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
 * SCRM 节日配置 DTO。
 * <p>
 * 对应 {@code ScrmFestivalEntity} 的业务字段, 创建/更新接口入参。festivalType 标注节日类型
 * (SOLAR/LUNAR/FIXED/CUSTOM), festivalDate 为日期字符串 (公历/农历 MM-dd 或 FIXED 完整日期)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmFestivalDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 节日名称 */
    @NotBlank(message = "节日名称不能为空")
    @Size(max = 100, message = "节日名称长度不能超过 100")
    private String festivalName;

    /** 节日类型: SOLAR 公历 / LUNAR 农历 / FIXED 固定 / CUSTOM 自定义 */
    @NotBlank(message = "节日类型不能为空")
    @Pattern(regexp = "SOLAR|LUNAR|FIXED|CUSTOM", message = "节日类型仅支持 SOLAR/LUNAR/FIXED/CUSTOM")
    private String festivalType;

    /** 节日日期: 公历 MM-dd / 农历 MM-dd / FIXED 完整日期 */
    @NotBlank(message = "节日日期不能为空")
    @Size(max = 20, message = "节日日期长度不能超过 20")
    private String festivalDate;

    /** 农历月 (1-12, 仅 LUNAR 类型使用, 可空) */
    private Integer lunarMonth;

    /** 农历日 (1-30, 仅 LUNAR 类型使用, 可空) */
    private Integer lunarDay;

    /** 节日描述 (可空) */
    @Size(max = 500, message = "节日描述长度不能超过 500")
    private String description;

    /** 默认祝福语 (可空) */
    @Size(max = 500, message = "默认祝福语长度不能超过 500")
    private String defaultGreeting;

    /** 默认动作类型 (可空): SEND_MESSAGE / SEND_COUPON / SEND_GIFT / CALL / CREATE_TASK / NOTIFY_ASSIGNEE */
    @Pattern(regexp = "SEND_MESSAGE|SEND_COUPON|SEND_GIFT|CALL|CREATE_TASK|NOTIFY_ASSIGNEE",
            message = "默认动作类型仅支持 SEND_MESSAGE/SEND_COUPON/SEND_GIFT/CALL/CREATE_TASK/NOTIFY_ASSIGNEE")
    private String defaultActionType;

    /** 默认动作内容 JSON (可空) */
    private String defaultActionContent;

    /** 适用范围: ALL / VIP / CUSTOM (默认 ALL) */
    @Pattern(regexp = "ALL|VIP|CUSTOM", message = "适用范围仅支持 ALL/VIP/CUSTOM")
    private String applicable;

    /** 是否启用 (创建时可选, 默认 true) */
    private Boolean enabled;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
