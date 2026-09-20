/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationDto.java
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
 * SCRM 通知记录 DTO。
 * <p>
 * 对应 {@code ScrmNotificationEntity} 的业务字段, 用于通知查询返回与直接创建场景入参。
 * 渲染后的标题与内容由 {@code ScrmNotificationCenterService.sendNotification} 在发送时填充。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmNotificationDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 来源模板 ID (可空, 直接发送时为空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    /** 来源模板编码 (可空) */
    @Size(max = 100, message = "模板编码长度不能超过 100")
    private String templateCode;

    /** 通知渠道: IN_APP/EMAIL/SMS/PUSH/WEBHOOK */
    @NotBlank(message = "通知渠道不能为空")
    @Pattern(regexp = "IN_APP|EMAIL|SMS|PUSH|WEBHOOK",
            message = "通知渠道仅支持 IN_APP/EMAIL/SMS/PUSH/WEBHOOK")
    private String channel;

    /** 分类: SYSTEM/MARKETING/SERVICE/ALERT/REMINDER/VERIFICATION */
    @NotBlank(message = "分类不能为空")
    @Pattern(regexp = "SYSTEM|MARKETING|SERVICE|ALERT|REMINDER|VERIFICATION",
            message = "分类仅支持 SYSTEM/MARKETING/SERVICE/ALERT/REMINDER/VERIFICATION")
    private String category;

    /** 标题 (渲染后) */
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过 200")
    private String title;

    /** 内容 (渲染后) */
    @NotBlank(message = "内容不能为空")
    private String content;

    /** 接收者类型: USER/CUSTOMER/EXTERNAL/ROLE/DEPARTMENT (默认 USER) */
    @Pattern(regexp = "USER|CUSTOMER|EXTERNAL|ROLE|DEPARTMENT",
            message = "接收者类型仅支持 USER/CUSTOMER/EXTERNAL/ROLE/DEPARTMENT")
    private String recipientType;

    /** 接收者 ID */
    @NotBlank(message = "接收者 ID 不能为空")
    @Size(max = 200, message = "接收者 ID 长度不能超过 200")
    private String recipientId;

    /** 接收者名称 (可空) */
    @Size(max = 200, message = "接收者名称长度不能超过 200")
    private String recipientName;

    /** 接收者联系方式: 手机/邮箱 (可空) */
    @Size(max = 200, message = "接收者联系方式长度不能超过 200")
    private String recipientContact;

    /** 发送者 ID (可空) */
    @Size(max = 100, message = "发送者 ID 长度不能超过 100")
    private String senderId;

    /** 发送者名称 (可空) */
    @Size(max = 100, message = "发送者名称长度不能超过 100")
    private String senderName;

    /** 状态: PENDING/SENDING/SENT/DELIVERED/READ/FAILED/CANCELLED (查询返回) */
    private String status;

    /** 优先级 (可空, 默认 0) */
    private Integer priority;

    /** 计划发送时间 (可空) */
    private LocalDateTime scheduledAt;

    /** 实际发送时间 (查询返回) */
    private LocalDateTime sentAt;

    /** 送达时间 (查询返回) */
    private LocalDateTime deliveredAt;

    /** 已读时间 (查询返回) */
    private LocalDateTime readAt;

    /** 错误信息 (查询返回) */
    private String errorMessage;

    /** 重试次数 (查询返回) */
    private Integer retryCount;

    /** 最大重试次数 (可空, 默认 3) */
    private Integer maxRetries;

    /** JSON 附加数据 (可空) */
    private String metadata;

    /** 关联类型 (可空) */
    @Size(max = 50, message = "关联类型长度不能超过 50")
    private String relatedType;

    /** 关联 ID (可空) */
    @Size(max = 100, message = "关联 ID 长度不能超过 100")
    private String relatedId;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
