/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCampaignDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 营销活动 DTO。
 * <p>
 * 对应 {@code ScrmMarketingCampaignEntity} 的业务字段, 创建/更新接口入参。
 * channels 以逗号分隔存储多渠道枚举; metricsJson 仅在查询时返回汇总指标。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmMarketingCampaignDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 活动名称 */
    @NotBlank(message = "活动名称不能为空")
    @Size(max = 200, message = "活动名称长度不能超过 200")
    private String campaignName;

    /** 活动类型: PROMOTION / NEW_PRODUCT / SEASONAL / RETENTION / ACQUISITION / BRAND_AWARENESS / FLASH_SALE */
    @NotBlank(message = "活动类型不能为空")
    @Pattern(regexp = "PROMOTION|NEW_PRODUCT|SEASONAL|RETENTION|ACQUISITION|BRAND_AWARENESS|FLASH_SALE",
            message = "活动类型仅支持 PROMOTION/NEW_PRODUCT/SEASONAL/RETENTION/ACQUISITION/BRAND_AWARENESS/FLASH_SALE")
    private String campaignType;

    /** 活动描述 (可空) */
    @Size(max = 1000, message = "活动描述长度不能超过 1000")
    private String description;

    /** 活动目标 (可空) */
    @Size(max = 500, message = "活动目标长度不能超过 500")
    private String objective;

    /** 目标客群条件 JSON (可空) */
    @Size(max = 500, message = "目标客群条件长度不能超过 500")
    private String targetSegment;

    /** 渠道 (逗号分隔: WECHAT/WORK_WECHAT/SMS/EMAIL/DOUYIN/KUAISHOU/XIAOHONGSHU/BILIBILI) */
    @NotBlank(message = "渠道不能为空")
    @Size(max = 500, message = "渠道长度不能超过 500")
    private String channels;

    /** 开始日期 */
    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    /** 结束日期 */
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    /** 预算 (默认 0) */
    private Double budget;

    /** 实际花费 (查询返回) */
    private Double actualCost;

    /** 状态: DRAFT / SCHEDULED / RUNNING / PAUSED / COMPLETED / CANCELLED (查询返回) */
    @Pattern(regexp = "DRAFT|SCHEDULED|RUNNING|PAUSED|COMPLETED|CANCELLED",
            message = "状态仅支持 DRAFT/SCHEDULED/RUNNING/PAUSED/COMPLETED/CANCELLED")
    private String status;

    /** 负责人 ID (可空) */
    @Size(max = 100, message = "负责人 ID 长度不能超过 100")
    private String managerId;

    /** 负责人名称 (可空) */
    @Size(max = 100, message = "负责人名称长度不能超过 100")
    private String managerName;

    /** 优先级 (默认 0) */
    private Integer priority;

    /** 标签 (逗号分隔, 可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 活动指标 JSON (查询返回) */
    private String metricsJson;

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
