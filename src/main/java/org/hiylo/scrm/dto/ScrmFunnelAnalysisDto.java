/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFunnelAnalysisDto.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 转化漏斗分析 DTO。
 * <p>
 * 对应 {@code ScrmFunnelAnalysisEntity} 的业务字段, 创建/更新接口入参。
 * stages 为 JSON 文本: {@code [{name,order,event,description}]}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmFunnelAnalysisDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 漏斗名称 */
    @NotBlank(message = "漏斗名称不能为空")
    @Size(max = 200, message = "漏斗名称长度不能超过 200")
    private String funnelName;

    /** 漏斗编码 (唯一) */
    @NotBlank(message = "漏斗编码不能为空")
    @Size(max = 50, message = "漏斗编码长度不能超过 50")
    private String funnelCode;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 关联营销活动 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long campaignId;

    /** 关联营销活动名称 (可空) */
    @Size(max = 200, message = "营销活动名称长度不能超过 200")
    private String campaignName;

    /** 漏斗类型 */
    @NotBlank(message = "漏斗类型不能为空")
    @Pattern(regexp = "MARKETING|SALES|ONBOARDING|PURCHASE|REGISTRATION|ACTIVATION|RETENTION|CUSTOM",
            message = "漏斗类型仅支持 MARKETING/SALES/ONBOARDING/PURCHASE/REGISTRATION/ACTIVATION/RETENTION/CUSTOM")
    private String funnelType;

    /** JSON 阶段: [{name,order,event,description}] */
    @NotBlank(message = "阶段数据不能为空")
    private String stages;

    /** 阶段数 (查询返回) */
    private Integer stageCount;

    /** 总进入数 (查询返回) */
    private Integer totalEntrants;

    /** 总完成数 (查询返回) */
    private Integer totalCompleted;

    /** 整体转化率% (查询返回) */
    private Double overallConversionRate;

    /** 整体流失率% (查询返回) */
    private Double overallDropOffRate;

    /** 平均完成时间 (小时, 查询返回) */
    private Double avgTimeToComplete;

    /** JSON 阶段指标 (查询返回) */
    private String stageMetrics;

    /** JSON 瓶颈分析 (查询返回) */
    private String bottlenecks;

    /** 最佳表现阶段 (查询返回) */
    private String bestPerformingStage;

    /** 最差表现阶段 (查询返回) */
    private String worstPerformingStage;

    /** 最大流失阶段 (查询返回) */
    private String maxDropOffStage;

    /** JSON 分群分析 (可空) */
    private String segmentAnalysis;

    /** JSON 设备分析 (可空) */
    private String deviceAnalysis;

    /** JSON 渠道分析 (可空) */
    private String channelAnalysis;

    /** 分析时间范围: 7D/30D/90D/180D/365D/ALL (默认 30D) */
    @Pattern(regexp = "7D|30D|90D|180D|365D|ALL",
            message = "分析时间范围仅支持 7D/30D/90D/180D/365D/ALL")
    private String timeRange;

    /** 分析开始日期 */
    @NotNull(message = "分析开始日期不能为空")
    private LocalDate startDate;

    /** 分析结束日期 */
    @NotNull(message = "分析结束日期不能为空")
    private LocalDate endDate;

    /** 最近计算时间 (查询返回) */
    private LocalDateTime lastCalculatedAt;

    /** 计算状态: PENDING/CALCULATING/COMPLETED/FAILED */
    @Pattern(regexp = "PENDING|CALCULATING|COMPLETED|FAILED",
            message = "计算状态仅支持 PENDING/CALCULATING/COMPLETED/FAILED")
    private String calculationStatus;

    /** 洞察 (可空) */
    @Size(max = 2000, message = "洞察长度不能超过 2000")
    private String insights;

    /** 建议 (可空) */
    @Size(max = 2000, message = "建议长度不能超过 2000")
    private String recommendations;

    /** 标签 (可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
