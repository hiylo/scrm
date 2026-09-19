/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConversationMessageDto.java
 * Date : 2026/07/27 02:41:22
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
 * SCRM 会话消息 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmConversationMessageDto {

    /** 数据库主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 消息 ID（业务唯一，可空，后端自动生成 UUID） */
    @Size(max = 100, message = "消息 ID 长度不能超过 100")
    private String messageId;

    /** 会话 ID */
    @NotNull(message = "会话 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    /** 消息类型：TEXT / IMAGE / VOICE / VIDEO / FILE / LINK / SYSTEM（可空，默认 TEXT） */
    @Size(max = 20, message = "消息类型长度不能超过 20")
    @Pattern(regexp = "TEXT|IMAGE|VOICE|VIDEO|FILE|LINK|SYSTEM",
            message = "消息类型仅支持 TEXT/IMAGE/VOICE/VIDEO/FILE/LINK/SYSTEM")
    private String messageType;

    /** 消息方向：IN / OUT（可空，默认 OUT） */
    @Size(max = 10, message = "消息方向长度不能超过 10")
    @Pattern(regexp = "IN|OUT", message = "消息方向仅支持 IN/OUT")
    private String direction;

    /** 兼容字段：前端使用 contentType，映射到 messageType */
    private String contentType;

    /** 文本内容（媒体消息为空） */
    private String content;

    /** 媒体对象 key（对象存储 objectKey，文本消息为空） */
    @Size(max = 500, message = "媒体对象 key 长度不能超过 500")
    private String mediaObjectKey;

    /** 媒体大小（字节） */
    private Long mediaSize;

    /** 平台消息 ID */
    @Size(max = 200, message = "平台消息 ID 长度不能超过 200")
    private String platformMessageId;

    /** 发送时间（可空，默认当前时间） */
    private LocalDateTime sentAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
