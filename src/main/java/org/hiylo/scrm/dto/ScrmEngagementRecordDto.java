/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementRecordDto.java
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
 * SCRM 互动行为事件记录 DTO。
 * <p>
 * {@code recordEvent} / {@code batchRecordEvents} 接口入参, 承载事件的核心字段与可选的
 * 上下文信息 (sessionId/pageUrl/userAgent 等)。eventTime 缺省时由服务端填充当前时间,
 * 支持补录历史事件。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmEngagementRecordDto {

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空, 用于冗余存储) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 行为类型: PAGE_VIEW/MESSAGE_SEND/MESSAGE_REPLY/CALL/EMAIL_OPEN/EMAIL_CLICK/LINK_CLICK/FORM_SUBMIT/PURCHASE/SHARE/FAVORITE/COMMENT/LOGIN/SEARCH/DOWNLOAD/APPOINTMENT */
    @NotBlank(message = "行为类型不能为空")
    @Size(max = 50, message = "行为类型长度不能超过 50")
    private String behaviorType;

    /** 发生渠道: WECHAT/WEB/APP/EMAIL/PHONE/STORE/OTHER (可空) */
    @Size(max = 30, message = "渠道长度不能超过 30")
    private String channel;

    /** 事件发生时间 (可空, 缺省由服务端填充当前时间, 支持补录) */
    private LocalDateTime eventTime;

    /** 会话 ID (可空) */
    @Size(max = 200, message = "会话 ID 长度不能超过 200")
    private String sessionId;

    /** 页面 URL (可空) */
    @Size(max = 500, message = "页面 URL 长度不能超过 500")
    private String pageUrl;

    /** 来源 (可空) */
    @Size(max = 500, message = "来源长度不能超过 500")
    private String referrer;

    /** User-Agent (可空) */
    @Size(max = 500, message = "User-Agent 长度不能超过 500")
    private String userAgent;

    /** 设备类型: MOBILE/PC/TABLET/OTHER (可空) */
    @Size(max = 30, message = "设备类型长度不能超过 30")
    private String deviceType;

    /** 地理位置 (可空) */
    @Size(max = 200, message = "地理位置长度不能超过 200")
    private String location;

    /** JSON 附加数据 (可空) */
    private String metadata;

    /** 客户端 IP (可空) */
    @Size(max = 100, message = "IP 长度不能超过 100")
    private String ip;
}
