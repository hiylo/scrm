/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocInsightDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM VoC 洞察 DTO。
 * <p>
 * 用于洞察创建、更新、查询返回。创建时必填洞察标题与类型; 影响等级缺省 MEDIUM, 优先级缺省 MEDIUM,
 * 状态缺省 DRAFT, 计数与 estimated_impact 缺省 0。更新时字段非空才覆盖。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmVocInsightDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 洞察标题 */
    @NotBlank(message = "洞察标题不能为空")
    @Size(max = 500, message = "洞察标题长度不能超过 500")
    private String insightTitle;

    /** 洞察类型: TREND/PATTERN/ANOMALY/OPPORTUNITY/RISK/ROOT_CAUSE/BEST_PRACTICE/LESSON_LEARNED */
    @NotBlank(message = "洞察类型不能为空")
    @Size(max = 30, message = "洞察类型长度不能超过 30")
    private String insightType;

    /** 描述 (可空) */
    @Size(max = 2000, message = "描述长度不能超过 2000")
    private String description;

    /** 洞察摘要 (可空) */
    @Size(max = 2000, message = "洞察摘要长度不能超过 2000")
    private String summary;

    /** 来源主题 ID 列表 (可空, 逗号分隔) */
    @Size(max = 500, message = "来源主题 ID 列表长度不能超过 500")
    private String sourceTopicIds;

    /** 来源声音 ID 列表 (可空, 逗号分隔) */
    @Size(max = 500, message = "来源声音 ID 列表长度不能超过 500")
    private String sourceVoiceIds;

    /** 相关声音数 */
    private Integer relatedVoiceCount;

    /** 数据点 (可空, JSON 数组: [{label,value,trend}]) */
    private String dataPoints;

    /** 详细分析 (可空) */
    private String analysis;

    /** 影响等级: LOW/MEDIUM/HIGH/CRITICAL */
    @Size(max = 20, message = "影响等级长度不能超过 20")
    private String impactLevel;

    /** 影响领域 (可空, 逗号分隔) */
    @Size(max = 500, message = "影响领域长度不能超过 500")
    private String impactAreas;

    /** 受影响客群 (可空, 逗号分隔) */
    @Size(max = 500, message = "受影响客群长度不能超过 500")
    private String affectedSegments;

    /** 预估影响值 */
    private Double estimatedImpact;

    /** 建议 (可空) */
    @Size(max = 2000, message = "建议长度不能超过 2000")
    private String recommendations;

    /** 行动项 (可空, JSON 数组: [{action,owner,dueDate,priority}]) */
    @Size(max = 2000, message = "行动项长度不能超过 2000")
    private String actionItems;

    /** 优先级: LOW/MEDIUM/HIGH/URGENT */
    @Size(max = 20, message = "优先级长度不能超过 20")
    private String priority;

    /** 状态: DRAFT/REVIEW/PUBLISHED/ACTED_ON/ARCHIVED */
    @Size(max = 20, message = "状态长度不能超过 20")
    private String status;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 审核人 (可空) */
    @Size(max = 100, message = "审核人长度不能超过 100")
    private String reviewedBy;

    /** 审核时间 (可空) */
    private LocalDateTime reviewedAt;

    /** 发布时间 (可空) */
    private LocalDateTime publishedAt;

    /** 发布人 (可空) */
    @Size(max = 100, message = "发布人长度不能超过 100")
    private String publishedBy;

    /** 分享给 (可空, 用户 ID 逗号分隔) */
    @Size(max = 500, message = "分享给长度不能超过 500")
    private String sharedWith;

    /** 反馈数 */
    private Integer feedbackCount;

    /** 反馈平均评分 */
    private Double feedbackRating;

    /** 标签 (可空, 逗号分隔) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 分析周期 (可空, 如 2026-Q3) */
    @Size(max = 50, message = "分析周期长度不能超过 50")
    private String period;

    /** 分析日期 (可空) */
    private LocalDate analysisDate;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
