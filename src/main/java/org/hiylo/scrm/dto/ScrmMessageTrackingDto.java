/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTrackingDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 消息跟踪 DTO。
 * <p>
 * {@code createTracking} 接口入参, 承载消息跟踪记录的核心字段。messageId / senderId /
 * recipientId / channel 为必填, 其余字段可选。sendStatus 缺省 PENDING, contentType 缺省
 * TEXT, recipientType 缺省 CUSTOMER, 由服务端在持久化前补齐默认值。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmMessageTrackingDto {

    /** 消息 ID (业务唯一) */
    @NotBlank(message = "消息 ID 不能为空")
    @Size(max = 200, message = "消息 ID 长度不能超过 200")
    private String messageId;

    /** 批次 ID (可空, 群发/批量发送时关联批次) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchId;

    /** 发送者 ID */
    @NotBlank(message = "发送者 ID 不能为空")
    @Size(max = 100, message = "发送者 ID 长度不能超过 100")
    private String senderId;

    /** 发送者名称 (可空) */
    @Size(max = 100, message = "发送者名称长度不能超过 100")
    private String senderName;

    /** 接收者 ID / 客户 ID */
    @NotBlank(message = "接收者 ID 不能为空")
    @Size(max = 200, message = "接收者 ID 长度不能超过 200")
    private String recipientId;

    /** 接收者名称 (可空) */
    @Size(max = 200, message = "接收者名称长度不能超过 200")
    private String recipientName;

    /** 接收者类型: CUSTOMER/GROUP/EXTERNAL (可空, 缺省 CUSTOMER) */
    @Size(max = 20, message = "接收者类型长度不能超过 20")
    private String recipientType;

    /** 渠道: WECHAT/WORK_WECHAT/SMS/EMAIL/APP_PUSH/WEB_SOCKET */
    @NotBlank(message = "渠道不能为空")
    @Size(max = 30, message = "渠道长度不能超过 30")
    private String channel;

    /** 消息内容摘要 (可空) */
    @Size(max = 2000, message = "消息内容摘要长度不能超过 2000")
    private String messageContent;

    /** 内容类型: TEXT/IMAGE/VIDEO/FILE/LINK/CARD/HTML (可空, 缺省 TEXT) */
    @Size(max = 20, message = "内容类型长度不能超过 20")
    private String contentType;

    /** 发送状态: PENDING/SENT/DELIVERED/FAILED/CANCELLED (可空, 缺省 PENDING) */
    @Size(max = 20, message = "发送状态长度不能超过 20")
    private String sendStatus;

    /** 发送时间 (可空) */
    private LocalDateTime sentAt;

    /** 客户端 IP (可空) */
    @Size(max = 100, message = "客户端 IP 长度不能超过 100")
    private String clientIp;

    /** 设备类型 (可空) */
    @Size(max = 30, message = "设备类型长度不能超过 30")
    private String deviceType;

    /** JSON 附加数据 (可空) */
    private String metadata;
}
