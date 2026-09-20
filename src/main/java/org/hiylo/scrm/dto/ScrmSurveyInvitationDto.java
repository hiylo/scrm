/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSurveyInvitationDto.java
 * Date : 2026/08/04 08:40:58
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

import java.time.LocalDateTime;

/**
 * SCRM 调查邀请 DTO。
 * <p>
 * 对应 {@code ScrmSurveyEntity} 邀请记录的业务字段, 创建/查询接口入参。surveyId / customerId / channel
 * 必填; invitationCode 由系统生成 (创建时为空); status 标注邀请生命周期
 * (PENDING/SENT/OPENED/IN_PROGRESS/COMPLETED/EXPIRED/BOUNCED)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmSurveyInvitationDto {

    /** 主键 ID (查询返回) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 调查问卷 ID */
    @NotNull(message = "调查问卷 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long surveyId;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 分发渠道: IN_APP / SMS / EMAIL / WECHAT */
    @NotBlank(message = "分发渠道不能为空")
    @Pattern(regexp = "IN_APP|SMS|EMAIL|WECHAT",
            message = "分发渠道仅支持 IN_APP/SMS/EMAIL/WECHAT")
    private String channel;

    /** 联系方式 (可空, 手机号 / 邮箱 / openid 等) */
    @Size(max = 200, message = "联系方式长度不能超过 200")
    private String contactInfo;

    /** 邀请码 (系统生成, 查询返回) */
    @Size(max = 100, message = "邀请码长度不能超过 100")
    private String invitationCode;

    /** 状态: PENDING / SENT / OPENED / IN_PROGRESS / COMPLETED / EXPIRED / BOUNCED (查询返回) */
    @Pattern(regexp = "PENDING|SENT|OPENED|IN_PROGRESS|COMPLETED|EXPIRED|BOUNCED",
            message = "状态仅支持 PENDING/SENT/OPENED/IN_PROGRESS/COMPLETED/EXPIRED/BOUNCED")
    private String status;

    /** 发送时间 (查询返回) */
    private LocalDateTime sentAt;

    /** 打开时间 (查询返回) */
    private LocalDateTime openedAt;

    /** 完成时间 (查询返回) */
    private LocalDateTime completedAt;

    /** 过期时间 (可空) */
    private LocalDateTime expiredAt;

    /** 提醒次数 (查询返回) */
    private Integer reminderCount;

    /** 最近提醒时间 (查询返回) */
    private LocalDateTime lastReminderAt;

    /** 触发来源事件 (可空, 如 PURCHASE / SERVICE_TICKET) */
    @Size(max = 100, message = "触发来源事件长度不能超过 100")
    private String sourceEvent;

    /** 触发来源 ID (可空, 如订单号 / 工单号) */
    @Size(max = 100, message = "触发来源 ID 长度不能超过 100")
    private String sourceId;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
