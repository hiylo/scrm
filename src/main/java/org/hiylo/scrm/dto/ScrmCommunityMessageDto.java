/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommunityMessageDto.java
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
 * SCRM 社群消息 DTO。
 * <p>
 * 对应 {@code ScrmCommunityMessageEntity} 的业务字段, 用于消息记录与查询接口入参与返回。
 * senderType 标识发送者类型, messageType 标识消息类型, sentAt 标识发送时间。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCommunityMessageDto {

    /** 主键 ID (查询返回) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 社群 ID */
    @NotNull(message = "社群 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long communityId;

    /** 发送者 ID (可空) */
    @Size(max = 200, message = "发送者 ID 长度不能超过 200")
    private String senderId;

    /** 发送者名称 (可空) */
    @Size(max = 200, message = "发送者名称长度不能超过 200")
    private String senderName;

    /** 发送者类型: MEMBER/ADMIN/OWNER/BOT/SYSTEM (默认 MEMBER) */
    @Pattern(regexp = "MEMBER|ADMIN|OWNER|BOT|SYSTEM",
            message = "发送者类型仅支持 MEMBER/ADMIN/OWNER/BOT/SYSTEM")
    private String senderType;

    /** 消息类型: TEXT/IMAGE/FILE/LINK/VIDEO/VOICE (默认 TEXT) */
    @NotBlank(message = "消息类型不能为空")
    @Pattern(regexp = "TEXT|IMAGE|FILE|LINK|VIDEO|VOICE",
            message = "消息类型仅支持 TEXT/IMAGE/FILE/LINK/VIDEO/VOICE")
    private String messageType;

    /** 消息内容 (可空) */
    private String content;

    /** 媒体 URL (可空) */
    @Size(max = 500, message = "媒体 URL 长度不能超过 500")
    private String mediaUrl;

    /** 发送时间 */
    @NotNull(message = "发送时间不能为空")
    private LocalDateTime sentAt;

    /** 是否回复 (默认 false) */
    private Boolean isReply;

    /** 回复目标消息 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long replyToMessageId;

    /** 情感 (可空: POSITIVE/NEUTRAL/NEGATIVE) */
    @Pattern(regexp = "POSITIVE|NEUTRAL|NEGATIVE|",
            message = "情感仅支持 POSITIVE/NEUTRAL/NEGATIVE")
    private String sentiment;

    /** 是否归档 (查询返回, 默认 false) */
    private Boolean archived;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
