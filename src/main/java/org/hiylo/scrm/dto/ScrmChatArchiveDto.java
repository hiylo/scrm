/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChatArchiveDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 会话存档 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmChatArchiveDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 归属账号 ID */
    @NotNull(message = "归属账号 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 客户 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 会话 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    /** 平台类型 */
    @NotBlank(message = "平台类型不能为空")
    @Size(max = 30, message = "平台类型长度不能超过 30")
    private String platformType;

    /** 消息方向: INBOUND / OUTBOUND */
    @NotBlank(message = "消息方向不能为空")
    @Size(max = 10, message = "消息方向长度不能超过 10")
    private String direction;

    /** 消息类型: TEXT / IMAGE / VIDEO / VOICE / FILE / LINK / SYSTEM */
    @NotBlank(message = "消息类型不能为空")
    @Size(max = 20, message = "消息类型长度不能超过 20")
    private String messageType;

    /** 消息内容 */
    private String content;

    /** 原始消息内容 */
    private String rawContent;

    /** 媒体文件 URL */
    @Size(max = 500, message = "媒体 URL 长度不能超过 500")
    private String mediaUrl;

    /** 消息发送时间 */
    @NotNull(message = "消息发送时间不能为空")
    private LocalDateTime sentAt;

    /** 归档时间 */
    private LocalDateTime archivedAt;

    /** 质量标记: NORMAL / SENSITIVE / VIOLATION */
    @Size(max = 20, message = "质量标记长度不能超过 20")
    private String qualityFlag;

    /** 风险等级: LOW / MEDIUM / HIGH */
    @Size(max = 20, message = "风险等级长度不能超过 20")
    private String riskLevel;

    /** 归档来源: AUTO / MANUAL */
    @Size(max = 20, message = "归档来源长度不能超过 20")
    private String archiveSource;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
