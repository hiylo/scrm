/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompetitorActivityDto.java
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
 * SCRM 竞品动态 DTO。
 * <p>
 * 用于竞品动态增删改查接口入参与返回。impactLevel / responseStatus / importanceScore / isVerified
 * 缺省时由 Service 填默认值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCompetitorActivityDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 竞品 ID */
    @NotNull(message = "竞品 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long competitorId;

    /** 竞品名称 (可空, 缺省时由 Service 从竞品实体填充) */
    @Size(max = 200, message = "竞品名称长度不能超过 200")
    private String competitorName;

    /** 动态类型 */
    @NotBlank(message = "动态类型不能为空")
    @Pattern(regexp = "PRODUCT_LAUNCH|PRICE_CHANGE|MARKETING_CAMPAIGN|FUNDING|PARTNERSHIP|HIRING|EXPANSION|"
            + "CONTENT|PR_EVENT|ACQUISITION|PROMOTION|FEATURE_UPDATE|CRISIS|OTHER",
            message = "动态类型非法")
    private String activityType;

    /** 动态标题 */
    @NotBlank(message = "动态标题不能为空")
    @Size(max = 500, message = "动态标题长度不能超过 500")
    private String title;

    /** 动态摘要 (可空) */
    @Size(max = 2000, message = "动态摘要长度不能超过 2000")
    private String summary;

    /** 详细描述 (可空) */
    private String description;

    /** 动态日期 */
    @NotNull(message = "动态日期不能为空")
    private LocalDate activityDate;

    /** 来源 (可空) */
    @Size(max = 200, message = "来源长度不能超过 200")
    private String source;

    /** 来源链接 (可空) */
    @Size(max = 500, message = "来源链接长度不能超过 500")
    private String sourceUrl;

    /** 影响等级: LOW / MEDIUM / HIGH / CRITICAL */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL",
            message = "影响等级仅支持 LOW/MEDIUM/HIGH/CRITICAL")
    private String impactLevel;

    /** 影响分析 (可空) */
    @Size(max = 1000, message = "影响分析长度不能超过 1000")
    private String impactAnalysis;

    /** 受影响产品 (可空) */
    @Size(max = 500, message = "受影响产品长度不能超过 500")
    private String affectedProducts;

    /** 受影响客群 (可空) */
    @Size(max = 500, message = "受影响客群长度不能超过 500")
    private String affectedSegments;

    /** 我方应对策略 (可空) */
    @Size(max = 1000, message = "我方应对策略长度不能超过 1000")
    private String ourResponse;

    /** 应对状态: PENDING / PLANNING / EXECUTING / COMPLETED / NO_ACTION */
    @Pattern(regexp = "PENDING|PLANNING|EXECUTING|COMPLETED|NO_ACTION",
            message = "应对状态仅支持 PENDING/PLANNING/EXECUTING/COMPLETED/NO_ACTION")
    private String responseStatus;

    /** 应对负责人 (可空) */
    @Size(max = 100, message = "应对负责人长度不能超过 100")
    private String responseOwner;

    /** 应对截止日期 (可空) */
    private LocalDate responseDueDate;

    /** 发现者 (可空) */
    @Size(max = 100, message = "发现者长度不能超过 100")
    private String detectedBy;

    /** 发现方式: MANUAL / AUTOMATED / ALERT / INTEL (可空) */
    @Pattern(regexp = "MANUAL|AUTOMATED|ALERT|INTEL",
            message = "发现方式仅支持 MANUAL/AUTOMATED/ALERT/INTEL")
    private String detectionMethod;

    /** 重要性评分 (0-100) */
    private Integer importanceScore;

    /** 是否已验证 */
    private Boolean isVerified;

    /** 验证人 (可空) */
    @Size(max = 100, message = "验证人长度不能超过 100")
    private String verifiedBy;

    /** 验证时间 (可空) */
    private LocalDateTime verifiedAt;

    /** 标签 (可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 附件 JSON (可空) */
    @Size(max = 1000, message = "附件长度不能超过 1000")
    private String attachments;

    /** 相关联动态 ID (可空, 逗号分隔) */
    @Size(max = 500, message = "相关联动态长度不能超过 500")
    private String relatedActivityIds;

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
