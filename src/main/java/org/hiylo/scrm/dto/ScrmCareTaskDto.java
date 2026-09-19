/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCareTaskDto.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 客户关怀任务 DTO。
 * <p>
 * 对应 {@code ScrmCareTaskEntity} 的业务字段, 创建/更新接口入参。ruleId 为空表示手动创建;
 * careDate 为关怀日期 (生日/节日/纪念日), scheduledAt 为计划执行时间;
 * status 标注任务状态 (PENDING/EXECUTING/SUCCESS/FAILED/CANCELLED)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCareTaskDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 关联规则 ID (可空, 为空表示手动创建) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 关怀类型: BIRTHDAY / FESTIVAL / ANNIVERSARY / MEMBERSHIP_EXPIRY / INACTIVITY_REMINDER / CUSTOM */
    @NotBlank(message = "关怀类型不能为空")
    @Pattern(regexp = "BIRTHDAY|FESTIVAL|ANNIVERSARY|MEMBERSHIP_EXPIRY|INACTIVITY_REMINDER|CUSTOM",
            message = "关怀类型仅支持 BIRTHDAY/FESTIVAL/ANNIVERSARY/MEMBERSHIP_EXPIRY/INACTIVITY_REMINDER/CUSTOM")
    private String careType;

    /** 关怀日期 (生日/节日/纪念日等) */
    @NotNull(message = "关怀日期不能为空")
    private LocalDate careDate;

    /** 计划执行时间 */
    @NotNull(message = "计划执行时间不能为空")
    private LocalDateTime scheduledAt;

    /** 关怀动作: SEND_MESSAGE / SEND_COUPON / SEND_GIFT / CALL / CREATE_TASK / NOTIFY_ASSIGNEE */
    @NotBlank(message = "关怀动作不能为空")
    @Pattern(regexp = "SEND_MESSAGE|SEND_COUPON|SEND_GIFT|CALL|CREATE_TASK|NOTIFY_ASSIGNEE",
            message = "关怀动作仅支持 SEND_MESSAGE/SEND_COUPON/SEND_GIFT/CALL/CREATE_TASK/NOTIFY_ASSIGNEE")
    private String actionType;

    /** 动作内容 JSON (可空) */
    private String actionContent;

    /** 任务状态: PENDING / EXECUTING / SUCCESS / FAILED / CANCELLED (创建时可选, 默认 PENDING) */
    @Pattern(regexp = "PENDING|EXECUTING|SUCCESS|FAILED|CANCELLED",
            message = "任务状态仅支持 PENDING/EXECUTING/SUCCESS/FAILED/CANCELLED")
    private String status;

    /** 负责人 ID (可空) */
    @Size(max = 100, message = "负责人 ID 长度不能超过 100")
    private String assigneeId;

    /** 负责人名称 (可空) */
    @Size(max = 100, message = "负责人名称长度不能超过 100")
    private String assigneeName;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
