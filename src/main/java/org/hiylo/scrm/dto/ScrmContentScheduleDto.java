/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContentScheduleDto.java
 * Date : 2026/08/04 08:40:58
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

import java.time.LocalDateTime;

/**
 * SCRM 内容排期 DTO。
 * <p>
 * 对应 {@code ScrmContentScheduleEntity} 的业务字段, 排期任务创建/更新入参。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmContentScheduleDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 内容 ID */
    @NotNull(message = "内容 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contentId;

    /** 排期名称 */
    @NotBlank(message = "排期名称不能为空")
    @Size(max = 200, message = "排期名称长度不能超过 200")
    private String scheduleName;

    /** 渠道 (逗号分隔) */
    @NotBlank(message = "渠道不能为空")
    @Size(max = 500, message = "渠道长度不能超过 500")
    private String channels;

    /** 计划发布时间 */
    @NotNull(message = "计划发布时间不能为空")
    private LocalDateTime scheduledAt;

    /** 时区 (默认 Asia/Shanghai) */
    @Size(max = 50, message = "时区长度不能超过 50")
    private String timezone;

    /** 重复类型: NONE / DAILY / WEEKLY / MONTHLY (默认 NONE) */
    @Pattern(regexp = "NONE|DAILY|WEEKLY|MONTHLY",
            message = "重复类型仅支持 NONE/DAILY/WEEKLY/MONTHLY")
    private String repeatType;

    /** 重复配置 JSON (可空) */
    private String repeatConfig;

    /** 状态: PENDING / EXECUTING / COMPLETED / FAILED / CANCELLED (查询返回) */
    @Pattern(regexp = "PENDING|EXECUTING|COMPLETED|FAILED|CANCELLED",
            message = "排期状态仅支持 PENDING/EXECUTING/COMPLETED/FAILED/CANCELLED")
    private String status;

    /** 执行时间 (查询返回) */
    private LocalDateTime executedAt;

    /** 错误信息 (查询返回) */
    private String errorMessage;

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
