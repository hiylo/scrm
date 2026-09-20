/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerHealthScoreDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户健康度评分 DTO。
 * <p>
 * 对应 {@code ScrmCustomerHealthScoreEntity} 的业务字段, 创建/更新接口入参与查询返回。
 * metricScores 为 JSON 数组字符串: {@code [{metricCode, metricName, score, maxScore,
 * details, status}]}。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCustomerHealthScoreDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    private String customerName;

    /** 健康度模型 ID */
    @NotNull(message = "模型 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    /** 总分 */
    private Double totalScore;

    /** 满分 */
    private Double maxScore;

    /** 健康等级: CRITICAL / AT_RISK / NEUTRAL / HEALTHY / EXCELLENT */
    private String healthLevel;

    /** 健康标签 */
    private String healthLabel;

    /** 得分百分比 */
    private Double scorePercent;

    /** 各指标得分 JSON */
    private String metricScores;

    /** 互动分 */
    private Double engagementScore;

    /** 使用分 */
    private Double usageScore;

    /** 满意度分 */
    private Double satisfactionScore;

    /** 支付分 */
    private Double paymentScore;

    /** 增长分 */
    private Double growthScore;

    /** 支持分 */
    private Double supportScore;

    /** 评分趋势: IMPROVING / STABLE / DECLINING / RAPID_DECLINE */
    private String scoreTrend;

    /** 趋势变化 */
    private Double trendChange;

    /** 上次得分 */
    private Double previousScore;

    /** 风险等级: NONE / LOW / MEDIUM / HIGH / CRITICAL */
    private String riskLevel;

    /** 风险因素 (逗号分隔) */
    private String riskFactors;

    /** 建议动作 (逗号分隔) */
    private String recommendedActions;

    /** 是否风险客户 */
    private Boolean isAtRisk;

    /** 是否流失风险 */
    private Boolean isChurnRisk;

    /** 距上次互动天数 */
    private Integer lastInteractionDays;

    /** 距上次订单天数 */
    private Integer daysSinceLastOrder;

    /** 待处理工单数 */
    private Integer openTickets;

    /** NPS 评分 (可空) */
    private Integer npsScore;

    /** 计算时间 */
    private LocalDateTime calculatedAt;

    /** 下次计算时间 */
    private LocalDateTime nextCalculationAt;

    /** 备注 */
    private String notes;

    /** 负责人 */
    private String assignedTo;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
