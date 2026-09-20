/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignFunnelDto.java
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
 * SCRM 营销活动转化漏斗 DTO。
 * <p>
 * 对应 {@code ScrmCampaignFunnelEntity} 的业务字段, 创建/更新接口入参。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCampaignFunnelDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 分析 ID */
    @NotNull(message = "分析 ID 不能为空")
    private Long analysisId;

    /** 营销活动 ID (可空) */
    private Long campaignId;

    /** 漏斗名称 */
    @NotBlank(message = "漏斗名称不能为空")
    @Size(max = 200, message = "漏斗名称长度不能超过 200")
    private String funnelName;

    /** 漏斗类型: AWARENESS/REGISTRATION/PURCHASE/ENGAGEMENT/RETENTION/CUSTOM (默认 PURCHASE) */
    @Pattern(regexp = "AWARENESS|REGISTRATION|PURCHASE|ENGAGEMENT|RETENTION|CUSTOM",
            message = "漏斗类型仅支持 AWARENESS/REGISTRATION/PURCHASE/ENGAGEMENT/RETENTION/CUSTOM")
    private String funnelType;

    /** 阶段名称 */
    @NotBlank(message = "阶段名称不能为空")
    @Size(max = 200, message = "阶段名称长度不能超过 200")
    private String stageName;

    /** 阶段顺序 */
    @NotNull(message = "阶段顺序不能为空")
    private Integer stageOrder;

    /** 阶段类型: AWARENESS/INTEREST/CONSIDERATION/INTENT/PURCHASE/RETENTION/ADVOCACY */
    @NotBlank(message = "阶段类型不能为空")
    @Pattern(regexp = "AWARENESS|INTEREST|CONSIDERATION|INTENT|PURCHASE|RETENTION|ADVOCACY",
            message = "阶段类型仅支持 AWARENESS/INTEREST/CONSIDERATION/INTENT/PURCHASE/RETENTION/ADVOCACY")
    private String stageType;

    /** 进入数 */
    private Integer entryCount;

    /** 退出数 */
    private Integer exitCount;

    /** 转化数 */
    private Integer conversionCount;

    /** 流失数 */
    private Integer dropoffCount;

    /** 转化率 */
    private Double conversionRate;

    /** 流失率 */
    private Double dropoffRate;

    /** 平均耗时 */
    private Double avgTimeSpent;

    /** 收入 */
    private Double revenue;

    /** 成本 */
    private Double cost;

    /** 是否瓶颈阶段 */
    private Boolean isBottleneck;

    /** 优化建议 */
    @Size(max = 1000, message = "优化建议长度不能超过 1000")
    private String optimizationNotes;

    /** 元数据 JSON */
    private String metadata;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
