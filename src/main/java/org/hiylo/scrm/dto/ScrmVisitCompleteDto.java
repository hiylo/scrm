/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVisitCompleteDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 客户回访完成 DTO。
 * <p>
 * 用于回访任务完成接口入参, 携带回访结果、满意度评分与客户反馈等字段。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmVisitCompleteDto {

    /** 任务 ID */
    @NotNull(message = "任务 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 回访结果: SUCCESS/PARTIAL/NO_ANSWER/REFUSED/RESCHEDULED/FAILED */
    @NotBlank(message = "回访结果不能为空")
    @Pattern(regexp = "SUCCESS|PARTIAL|NO_ANSWER|REFUSED|RESCHEDULED|FAILED",
            message = "回访结果仅支持 SUCCESS/PARTIAL/NO_ANSWER/REFUSED/RESCHEDULED/FAILED")
    private String visitOutcome;

    /** 满意度评分 1-5 (可空) */
    @Min(value = 1, message = "满意度评分不能小于 1")
    @Max(value = 5, message = "满意度评分不能大于 5")
    private Integer satisfactionScore;

    /** NPS 评分 0-10 (可空) */
    @Min(value = 0, message = "NPS 评分不能小于 0")
    @Max(value = 10, message = "NPS 评分不能大于 10")
    private Integer npsScore;

    /** 客户反馈 (可空) */
    @Size(max = 2000, message = "客户反馈长度不能超过 2000")
    private String feedback;

    /** 回访总结 (可空) */
    @Size(max = 1000, message = "回访总结长度不能超过 1000")
    private String summary;

    /** 后续行动项 (可空) */
    @Size(max = 1000, message = "后续行动项长度不能超过 1000")
    private String actionItems;

    /** 实际回访时长分钟 (可空) */
    @Min(value = 0, message = "实际时长不能小于 0")
    private Integer actualDurationMinutes;

    /** 是否发现商机 (可空, 默认 FALSE) */
    private Boolean opportunityFound;

    /** 商机描述 (可空) */
    @Size(max = 500, message = "商机描述长度不能超过 500")
    private String opportunityDescription;

    /** 是否发现问题 (可空, 默认 FALSE) */
    private Boolean issueFound;

    /** 问题描述 (可空) */
    @Size(max = 500, message = "问题描述长度不能超过 500")
    private String issueDescription;

    /** 问题是否已解决 (可空, 默认 FALSE) */
    private Boolean issueResolved;

    /** 是否需要跟进 (可空, 默认 FALSE) */
    private Boolean followUpRequired;
}
