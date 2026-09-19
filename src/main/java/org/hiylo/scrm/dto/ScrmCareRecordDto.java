/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCareRecordDto.java
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
 * SCRM 客户关怀记录 DTO。
 * <p>
 * 对应 {@code ScrmCareRecordEntity} 的业务字段, 创建关怀记录接口入参。careResult 标注关怀结果
 * (SUCCESS/NO_RESPONSE/REJECTED/FAILED), sentiment 标注情感倾向 (POSITIVE/NEUTRAL/NEGATIVE)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCareRecordDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

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

    /** 关怀日期 */
    @NotNull(message = "关怀日期不能为空")
    private LocalDate careDate;

    /** 关怀动作: SEND_MESSAGE / SEND_COUPON / SEND_GIFT / CALL / CREATE_TASK / NOTIFY_ASSIGNEE */
    @NotBlank(message = "关怀动作不能为空")
    @Pattern(regexp = "SEND_MESSAGE|SEND_COUPON|SEND_GIFT|CALL|CREATE_TASK|NOTIFY_ASSIGNEE",
            message = "关怀动作仅支持 SEND_MESSAGE/SEND_COUPON/SEND_GIFT/CALL/CREATE_TASK/NOTIFY_ASSIGNEE")
    private String actionType;

    /** 动作详情 (可空) */
    @Size(max = 500, message = "动作详情长度不能超过 500")
    private String actionDetail;

    /** 关怀结果: SUCCESS / NO_RESPONSE / REJECTED / FAILED */
    @NotBlank(message = "关怀结果不能为空")
    @Pattern(regexp = "SUCCESS|NO_RESPONSE|REJECTED|FAILED",
            message = "关怀结果仅支持 SUCCESS/NO_RESPONSE/REJECTED/FAILED")
    private String careResult;

    /** 客户回应 (可空) */
    @Size(max = 500, message = "客户回应长度不能超过 500")
    private String customerResponse;

    /** 客户回应时长 (小时, 可空) */
    private Integer responseTimeHours;

    /** 情感倾向: POSITIVE / NEUTRAL / NEGATIVE (可空) */
    @Pattern(regexp = "POSITIVE|NEUTRAL|NEGATIVE", message = "情感倾向仅支持 POSITIVE/NEUTRAL/NEGATIVE")
    private String sentiment;

    /** 负责人 ID (可空) */
    @Size(max = 100, message = "负责人 ID 长度不能超过 100")
    private String assigneeId;

    /** 执行时间 (可空, 未填则取当前时间) */
    private LocalDateTime executedAt;

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
