/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechRecommendationDto.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 销售话术推荐记录 DTO。
 * <p>
 * 对应 {@code ScrmSpeechRecommendationEntity} 的业务字段, 用于推荐记录的回写与查询。
 * recommendedSpeechIds / matchContext 由推荐引擎生成, feedback / outcome 由
 * 反馈接口回写。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmSpeechRecommendationDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 场景 ID */
    @NotNull(message = "场景 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long scenarioId;

    /** 场景名称 (可空) */
    @Size(max = 200, message = "场景名称长度不能超过 200")
    private String scenarioName;

    /** 推荐话术 ID 列表 (逗号分隔) */
    @NotBlank(message = "推荐话术 ID 列表不能为空")
    @Size(max = 1000, message = "推荐话术 ID 列表长度不能超过 1000")
    private String recommendedSpeechIds;

    /** 匹配上下文 JSON */
    @NotBlank(message = "匹配上下文不能为空")
    private String matchContext;

    /** 匹配分数 (查询返回) */
    private Double matchScore;

    /** 匹配原因 (逗号分隔, 可空) */
    @Size(max = 1000, message = "匹配原因长度不能超过 1000")
    private String matchReasons;

    /** 用户选择的话术 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long selectedSpeechId;

    /** 使用时间 (可空) */
    private LocalDateTime usedAt;

    /** 反馈: POSITIVE/NEGATIVE/NEUTRAL (可空) */
    @Pattern(regexp = "POSITIVE|NEGATIVE|NEUTRAL|",
            message = "反馈仅支持 POSITIVE/NEGATIVE/NEUTRAL")
    private String feedback;

    /** 反馈注释 (可空) */
    @Size(max = 500, message = "反馈注释长度不能超过 500")
    private String feedbackComment;

    /** 使用结果: SUCCESS/PARTIAL/FAILURE/NOT_USED (可空) */
    @Pattern(regexp = "SUCCESS|PARTIAL|FAILURE|NOT_USED|",
            message = "使用结果仅支持 SUCCESS/PARTIAL/FAILURE/NOT_USED")
    private String outcome;

    /** 推荐时间 (查询返回) */
    private LocalDateTime recommendedAt;

    /** 推荐人 ID (可空) */
    @Size(max = 100, message = "推荐人 ID 长度不能超过 100")
    private String recommendedBy;

    /** 推荐人名称 (可空) */
    @Size(max = 100, message = "推荐人名称长度不能超过 100")
    private String recommendedByName;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
