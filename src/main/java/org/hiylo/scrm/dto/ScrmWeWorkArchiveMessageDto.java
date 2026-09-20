/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWeWorkArchiveMessageDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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
 * 企微会话存档消息 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmWeWorkArchiveMessageDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 存档配置 ID */
    @NotNull(message = "存档配置 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long configId;

    /** 企微消息 seq */
    @NotNull(message = "消息 seq 不能为空")
    private Long seq;

    /** 消息 ID */
    @NotBlank(message = "消息 ID 不能为空")
    @Size(max = 200, message = "消息 ID 长度不能超过 200")
    private String msgId;

    /** 动作: send / recall */
    @NotBlank(message = "动作不能为空")
    @Size(max = 20, message = "动作长度不能超过 20")
    private String action;

    /** 发送者 */
    @NotBlank(message = "发送者不能为空")
    @Size(max = 200, message = "发送者长度不能超过 200")
    private String fromUser;

    /** 接收者列表（逗号分隔） */
    @NotBlank(message = "接收者列表不能为空")
    @Size(max = 1000, message = "接收者列表长度不能超过 1000")
    private String toList;

    /** 群 ID（可空） */
    @Size(max = 200, message = "群 ID 长度不能超过 200")
    private String roomId;

    /** 消息类型 */
    @NotBlank(message = "消息类型不能为空")
    @Size(max = 30, message = "消息类型长度不能超过 30")
    private String msgType;

    /** 解密后的消息内容 JSON */
    private String content;

    /** 原始加密内容 */
    private String rawContent;

    /** 媒体文件 URL */
    @Size(max = 500, message = "媒体 URL 长度不能超过 500")
    private String mediaUrl;

    /** 文件名 */
    @Size(max = 200, message = "文件名长度不能超过 200")
    private String fileName;

    /** 文件大小 */
    private Long fileSize;

    /** 消息发送时间 */
    @NotNull(message = "消息发送时间不能为空")
    private LocalDateTime sentAt;

    /** 入库时间 */
    private LocalDateTime archivedAt;

    /** 是否已处理 */
    private Boolean processed;

    /** 创建时间 */
    private LocalDateTime createTime;
}
