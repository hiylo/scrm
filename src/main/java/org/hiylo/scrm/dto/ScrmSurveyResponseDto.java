/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSurveyResponseDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 调查回复 DTO。
 * <p>
 * 对应 {@code ScrmSurveyResponseEntity} 的业务字段, 查询 / 跟进接口入参与返回。responses 为 JSON
 * 字符串描述回答列表 (结构: {@code [{questionId,answer,value}]}); npsScore / csatScore / cesScore
 * 由系统根据回答自动计算; sentiment 为情感倾向 (POSITIVE/NEUTRAL/NEGATIVE)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmSurveyResponseDto {

    /** 主键 ID (查询返回) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 调查问卷 ID (查询返回) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long surveyId;

    /** 邀请 ID (查询返回, 可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long invitationId;

    /** 客户 ID (查询返回) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (查询返回, 可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** JSON 回答: [{questionId,answer,value}] */
    private String responses;

    /** NPS 得分 0-10 (查询返回, 可空) */
    private Integer npsScore;

    /** CSAT 得分 1-5 (查询返回, 可空) */
    private Integer csatScore;

    /** CES 得分 1-7 (查询返回, 可空) */
    private Integer cesScore;

    /** 综合得分 (查询返回, 可空) */
    private Double overallScore;

    /** 情感: POSITIVE / NEUTRAL / NEGATIVE (查询返回, 可空) */
    @Pattern(regexp = "POSITIVE|NEUTRAL|NEGATIVE",
            message = "情感仅支持 POSITIVE/NEUTRAL/NEGATIVE")
    private String sentiment;

    /** 文字反馈 (可空) */
    private String feedbackText;

    /** 标签 (可空, 逗号分隔) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 是否需要跟进 (查询返回) */
    private Boolean followUpRequired;

    /** 跟进状态: PENDING / IN_PROGRESS / COMPLETED (可空) */
    @Pattern(regexp = "PENDING|IN_PROGRESS|COMPLETED",
            message = "跟进状态仅支持 PENDING/IN_PROGRESS/COMPLETED")
    private String followUpStatus;

    /** 跟进人 ID (可空) */
    @Size(max = 100, message = "跟进人 ID 长度不能超过 100")
    private String assigneeId;

    /** 提交时间 (查询返回) */
    private LocalDateTime submittedAt;

    /** 完成耗时秒 (查询返回, 可空) */
    private Integer durationSeconds;

    /** 客户端 IP (查询返回, 可空) */
    @Size(max = 100, message = "客户端 IP 长度不能超过 100")
    private String clientIp;

    /** User-Agent (查询返回, 可空) */
    @Size(max = 500, message = "User-Agent 长度不能超过 500")
    private String userAgent;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
