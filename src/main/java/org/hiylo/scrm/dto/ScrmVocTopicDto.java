/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocTopicDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM VoC 主题 DTO。
 * <p>
 * 用于主题创建、更新、查询返回。创建时必填主题名称与编码; 层级缺省 1, 计数缺省 0,
 * 趋势缺省 STABLE, 评分缺省 0, isHotTopic/isEmerging/actionTaken 缺省 FALSE, enabled 缺省 TRUE。
 * 更新时字段非空才覆盖。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmVocTopicDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 主题名称 */
    @NotBlank(message = "主题名称不能为空")
    @Size(max = 200, message = "主题名称长度不能超过 200")
    private String topicName;

    /** 主题编码 (唯一) */
    @NotBlank(message = "主题编码不能为空")
    @Size(max = 50, message = "主题编码长度不能超过 50")
    private String topicCode;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 父主题 ID (可空, 顶层主题为空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentTopicId;

    /** 主题层级 (默认 1, 顶层为 1) */
    private Integer topicLevel;

    /** 主题分类: PRODUCT/SERVICE/PRICE/QUALITY/EXPERIENCE/DELIVERY/SUPPORT/OTHER (可空) */
    @Size(max = 100, message = "主题分类长度不能超过 100")
    private String category;

    /** 关键词 (可空, 逗号分隔) */
    @Size(max = 500, message = "关键词长度不能超过 500")
    private String keywords;

    /** 声音数 */
    private Integer voiceCount;

    /** 正面数 */
    private Integer positiveCount;

    /** 负面数 */
    private Integer negativeCount;

    /** 中性数 */
    private Integer neutralCount;

    /** 正面率 (0-100) */
    private Double positiveRate;

    /** 负面率 (0-100) */
    private Double negativeRate;

    /** 平均情感分 (-1.0 ~ 1.0) */
    private Double avgSentimentScore;

    /** 平均评分 (0-5) */
    private Double avgRating;

    /** 趋势方向: RISING/STABLE/FALLING */
    @Size(max = 20, message = "趋势方向长度不能超过 20")
    private String trendDirection;

    /** 趋势变化 (%) */
    private Double trendPercent;

    /** 最新声音时间 (可空) */
    private LocalDateTime lastVoiceAt;

    /** 最早声音时间 (可空) */
    private LocalDateTime firstVoiceAt;

    /** 紧急度评分 (0-100) */
    private Double urgencyScore;

    /** 影响度评分 (0-100) */
    private Double impactScore;

    /** 综合优先级评分 (0-100) */
    private Double priorityScore;

    /** 是否热点主题 */
    private Boolean isHotTopic;

    /** 是否新兴主题 */
    private Boolean isEmerging;

    /** 责任部门 (可空) */
    @Size(max = 200, message = "责任部门长度不能超过 200")
    private String assignedDepartment;

    /** 是否已采取行动 */
    private Boolean actionTaken;

    /** 最近分析时间 (可空) */
    private LocalDateTime lastAnalysisAt;

    /** 是否启用 */
    private Boolean enabled;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
