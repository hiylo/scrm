/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyMatchDto.java
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

/**
 * SCRM 消息自动回复匹配 DTO。
 * <p>
 * 匹配引擎入参, 携带客户消息上下文 (客户 ID、消息内容、渠道、账号 ID),
 * 由 {@code ScrmAutoReplyService.matchReply} 加载启用规则按优先级评估,
 * 命中后返回回复内容并模拟发送, 同时记录回复日志。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAutoReplyMatchDto {

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 消息内容 */
    @NotBlank(message = "消息内容不能为空")
    private String message;

    /** 渠道: WECHAT/WORK_WECHAT/WEB/APP/SMS/EMAIL */
    @Size(max = 30, message = "渠道长度不能超过 30")
    private String channel;

    /** 账号 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 会话 ID（可空, 用于关联会话上下文） */
    @Size(max = 200, message = "会话 ID 长度不能超过 200")
    private String sessionId;

    /** 客户名称（可空, 用于模板变量替换） */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;
}
