/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerTimelineDto.java
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
 * SCRM 客户时间线事件 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCustomerTimelineDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 事件类型 */
    @NotBlank(message = "事件类型不能为空")
    @Size(max = 50, message = "事件类型长度不能超过 50")
    @Pattern(regexp = "CUSTOMER_CREATED|TAG_ADDED|TAG_REMOVED|LIFECYCLE_CHANGED|FOLLOW_UP_COMPLETED|"
            + "MESSAGE_SENT|MESSAGE_RECEIVED|OPPORTUNITY_CREATED|OPPORTUNITY_STAGE_CHANGED|"
            + "JOURNEY_ENROLLED|JOURNEY_STEP_COMPLETED|MASS_SEND_RECEIVED|CAMPAIGN_TRIGGERED|"
            + "NOTE_ADDED|FILE_SHARED",
            message = "事件类型仅支持 CUSTOMER_CREATED/TAG_ADDED/TAG_REMOVED/LIFECYCLE_CHANGED/"
                    + "FOLLOW_UP_COMPLETED/MESSAGE_SENT/MESSAGE_RECEIVED/OPPORTUNITY_CREATED/"
                    + "OPPORTUNITY_STAGE_CHANGED/JOURNEY_ENROLLED/JOURNEY_STEP_COMPLETED/"
                    + "MASS_SEND_RECEIVED/CAMPAIGN_TRIGGERED/NOTE_ADDED/FILE_SHARED")
    private String eventType;

    /** 事件标题 */
    @NotBlank(message = "事件标题不能为空")
    @Size(max = 200, message = "事件标题长度不能超过 200")
    private String eventTitle;

    /** 事件详情 (JSON 字符串, 可空) */
    private String eventDetail;

    /** 事件发生时间 */
    private LocalDateTime eventTime;

    /** 操作人 ID (可空) */
    @Size(max = 100, message = "操作人 ID 长度不能超过 100")
    private String operatorId;

    /** 操作人姓名 (可空) */
    @Size(max = 100, message = "操作人姓名长度不能超过 100")
    private String operatorName;

    /** 平台类型 (可空) */
    @Size(max = 30, message = "平台类型长度不能超过 30")
    private String platformType;

    /** 重要级别: HIGH / NORMAL / LOW (默认 NORMAL) */
    @Size(max = 10, message = "重要级别长度不能超过 10")
    @Pattern(regexp = "HIGH|NORMAL|LOW", message = "重要级别仅支持 HIGH/NORMAL/LOW")
    private String importance;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
