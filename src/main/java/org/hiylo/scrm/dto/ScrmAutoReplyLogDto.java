/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyLogDto.java
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
 * SCRM 消息自动回复日志 DTO。
 * <p>
 * 对应 {@code ScrmAutoReplyLogEntity} 的业务字段, 用于记录回复命中的执行轨迹。
 * Service 内部 recordLog 方法使用, 不直接暴露为创建接口 (日志由匹配引擎自动写入)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAutoReplyLogDto {

    /** 规则 ID (兜底回复可为空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;

    /** 规则名称 */
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 规则类型 */
    @NotBlank(message = "规则类型不能为空")
    @Size(max = 30, message = "规则类型长度不能超过 30")
    private String ruleType;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 账号 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 渠道 */
    @Size(max = 30, message = "渠道长度不能超过 30")
    private String channel;

    /** 收到的消息 */
    private String incomingMessage;

    /** 匹配的关键词 */
    @Size(max = 200, message = "匹配关键词长度不能超过 200")
    private String matchedKeyword;

    /** 匹配分数 */
    private Double matchScore;

    /** 回复类型 */
    @NotBlank(message = "回复类型不能为空")
    @Size(max = 20, message = "回复类型长度不能超过 20")
    private String replyType;

    /** 回复内容 */
    private String replyContent;

    /** 发送时间 */
    @NotNull(message = "发送时间不能为空")
    private LocalDateTime sentAt;

    /** 响应耗时 (毫秒) */
    private Integer responseTimeMs;

    /** 发送状态: SENT/FAILED/SKIPPED/QUEUED */
    @Size(max = 20, message = "发送状态长度不能超过 20")
    private String status;

    /** 错误信息 */
    @Size(max = 500, message = "错误信息长度不能超过 500")
    private String errorMessage;

    /** 是否兜底回复 */
    private Boolean isFallback;

    /** 会话 ID */
    @Size(max = 200, message = "会话 ID 长度不能超过 200")
    private String sessionId;

    /** JSON 附加数据 */
    private String metadata;
}
