/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommunityBroadcastDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * SCRM 社群群广播 DTO。
 * <p>
 * 用于 {@code ScrmCommunityService.broadcast} 接口入参, 向多个社群批量发送同一条消息。
 * communityIds 指定目标群集合, messageType 标识消息类型, content 为消息内容。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCommunityBroadcastDto {

    /** 目标社群 ID 列表 */
    @NotEmpty(message = "社群 ID 列表不能为空")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> communityIds;

    /** 消息类型: TEXT/IMAGE/FILE/LINK/VIDEO/VOICE (默认 TEXT) */
    @NotNull(message = "消息类型不能为空")
    @Pattern(regexp = "TEXT|IMAGE|FILE|LINK|VIDEO|VOICE",
            message = "消息类型仅支持 TEXT/IMAGE/FILE/LINK/VIDEO/VOICE")
    private String messageType;

    /** 消息内容 */
    @NotBlank(message = "消息内容不能为空")
    private String content;

    /** 媒体 URL (可空) */
    @Size(max = 500, message = "媒体 URL 长度不能超过 500")
    private String mediaUrl;

    /** 发送者名称 (可空, 默认 SYSTEM) */
    @Size(max = 200, message = "发送者名称长度不能超过 200")
    private String senderName;
}
