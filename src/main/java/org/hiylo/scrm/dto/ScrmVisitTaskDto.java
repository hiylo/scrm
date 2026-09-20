/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVisitTaskDto.java
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
 * SCRM 客户回访任务 DTO。
 * <p>
 * 对应 {@code ScrmVisitTaskEntity} 的业务字段, 用于任务创建/更新接口入参。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmVisitTaskDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 任务编号 (创建时可空, 由系统生成; 更新时不可修改) */
    @Size(max = 100, message = "任务编号长度不能超过 100")
    private String taskNo;

    /** 关联计划 ID (可空, 手动创建的任务无计划) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long planId;

    /** 关联计划名称 (冗余, 可空) */
    @Size(max = 200, message = "计划名称长度不能超过 200")
    private String planName;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 客户等级 (可空) */
    @Size(max = 50, message = "客户等级长度不能超过 50")
    private String customerLevel;

    /** 客户电话 (可空) */
    @Size(max = 50, message = "客户电话长度不能超过 50")
    private String customerPhone;

    /** 回访类型: REGULAR/FOLLOW_UP/SATISFACTION/RENEWAL/UPSELL/CROSS_SELL/CARE/COMPLAINT_FOLLOWUP/CUSTOM */
    @NotBlank(message = "回访类型不能为空")
    @Size(max = 30, message = "回访类型长度不能超过 30")
    private String visitType;

    /** 回访方式: PHONE/ON_SITE/VIDEO/WECHAT/EMAIL/SMS/MIXED */
    @NotBlank(message = "回访方式不能为空")
    @Pattern(regexp = "PHONE|ON_SITE|VIDEO|WECHAT|EMAIL|SMS|MIXED",
            message = "回访方式仅支持 PHONE/ON_SITE/VIDEO/WECHAT/EMAIL/SMS/MIXED")
    private String visitMethod;

    /** 计划回访日期 */
    @NotNull(message = "计划回访日期不能为空")
    private LocalDate scheduledDate;

    /** 计划回访时间 (可空) */
    private LocalTime scheduledTime;

    /** 负责人 ID (可空) */
    @Size(max = 100, message = "负责人 ID 长度不能超过 100")
    private String assignedTo;

    /** 状态: PENDING/ASSIGNED/IN_PROGRESS/COMPLETED/CANCELLED/RESCHEDULED/OVERDUE/FAILED */
    @Pattern(regexp = "PENDING|ASSIGNED|IN_PROGRESS|COMPLETED|CANCELLED|RESCHEDULED|OVERDUE|FAILED|",
            message = "状态仅支持 PENDING/ASSIGNED/IN_PROGRESS/COMPLETED/CANCELLED/RESCHEDULED/OVERDUE/FAILED")
    private String status;

    /** 上门地址 (可空) */
    @Size(max = 200, message = "上门地址长度不能超过 200")
    private String location;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

    /** 录音 URL (可空) */
    @Size(max = 500, message = "录音 URL 长度不能超过 500")
    private String recordingUrl;

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
