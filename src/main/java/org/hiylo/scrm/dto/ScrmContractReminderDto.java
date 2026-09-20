/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractReminderDto.java
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
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * SCRM 合同提醒 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmContractReminderDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 合同 ID */
    @NotNull(message = "合同 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 提醒类型: EXPIRY / PAYMENT / RENEWAL / REVIEW / CUSTOM */
    @NotBlank(message = "提醒类型不能为空")
    @Size(max = 30, message = "提醒类型长度不能超过 30")
    @Pattern(regexp = "EXPIRY|PAYMENT|RENEWAL|REVIEW|CUSTOM",
            message = "提醒类型仅支持 EXPIRY/PAYMENT/RENEWAL/REVIEW/CUSTOM")
    private String reminderType;

    /** 提醒日期 */
    @NotNull(message = "提醒日期不能为空")
    private LocalDate reminderDate;

    /** 提醒时间 (可空) */
    private LocalTime reminderTime;

    /** 提醒标题 */
    @NotBlank(message = "提醒标题不能为空")
    @Size(max = 200, message = "提醒标题长度不能超过 200")
    private String title;

    /** 提醒消息 (可空) */
    @Size(max = 1000, message = "提醒消息长度不能超过 1000")
    private String message;

    /** 接收人逗号分隔 (可空) */
    @Size(max = 500, message = "接收人长度不能超过 500")
    private String recipients;

    /** 渠道逗号分隔: EMAIL / SMS / WECHAT / APP (可空) */
    @Size(max = 200, message = "渠道长度不能超过 200")
    private String channels;

    /** 状态: PENDING / SENT / FAILED / CANCELLED */
    @Pattern(regexp = "PENDING|SENT|FAILED|CANCELLED|",
            message = "状态仅支持 PENDING/SENT/FAILED/CANCELLED")
    private String status;

    /** 发送时间 (可空) */
    private LocalDateTime sentAt;

    /** 已发送次数 */
    @PositiveOrZero(message = "已发送次数不能为负数")
    private Integer sentCount;

    /** 失败次数 */
    @PositiveOrZero(message = "失败次数不能为负数")
    private Integer failedCount;

    /** 响应次数 */
    @PositiveOrZero(message = "响应次数不能为负数")
    private Integer responseCount;

    /** 是否重复提醒 */
    private Boolean isRecurring;

    /** JSON 重复配置 (可空) */
    private String recurringConfig;

    /** 需要操作: NOTIFY / RENEW / REVIEW / APPROVE / NONE */
    @Pattern(regexp = "NOTIFY|RENEW|REVIEW|APPROVE|NONE|",
            message = "需要操作仅支持 NOTIFY/RENEW/REVIEW/APPROVE/NONE")
    private String actionRequired;

    /** 操作 URL (可空) */
    @Size(max = 500, message = "操作 URL 长度不能超过 500")
    private String actionUrl;

    /** 是否已处理 */
    private Boolean actionTaken;

    /** 处理时间 (可空) */
    private LocalDateTime actionTakenAt;

    /** 处理人 (可空) */
    @Size(max = 100, message = "处理人长度不能超过 100")
    private String actionTakenBy;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
