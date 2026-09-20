/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSurveySubmitDto.java
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
 * SCRM 调查回复提交 DTO。
 * <p>
 * 客户凭邀请码提交调查回答的入参。invitationCode 必填, 用于定位邀请记录与问卷;
 * responses 为 JSON 字符串描述回答列表 (结构: {@code [{questionId,answer,value}]});
 * customerId 用于校验邀请归属 (可空, 缺省取邀请记录的 customerId)。
 * </p>
 * <p>
 * 服务端根据回答自动计算 NPS / CSAT / CES 分数与情感倾向, 写入回复记录并更新问卷统计与
 * NPS 基准。clientIp / userAgent / durationSeconds 由调用方填充用于审计与耗时分析 (可空)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmSurveySubmitDto {

    /** 邀请码 (必填) */
    @NotBlank(message = "邀请码不能为空")
    @Size(max = 100, message = "邀请码长度不能超过 100")
    private String invitationCode;

    /** JSON 回答: [{questionId,answer,value}] */
    @NotBlank(message = "回答列表不能为空")
    private String responses;

    /** 客户 ID (可空, 缺省取邀请记录的 customerId) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 文字反馈 (可空) */
    private String feedbackText;

    /** 完成耗时秒 (可空) */
    private Integer durationSeconds;

    /** 客户端 IP (可空) */
    @Size(max = 100, message = "客户端 IP 长度不能超过 100")
    private String clientIp;

    /** User-Agent (可空) */
    @Size(max = 500, message = "User-Agent 长度不能超过 500")
    private String userAgent;
}
