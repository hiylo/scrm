/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompetitorDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 竞品信息 DTO。
 * <p>
 * 用于竞品增删改查接口入参与返回。threatLevel / monitoringFrequency / status 缺省时由 Service 填默认值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCompetitorDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 竞品名称 */
    @NotBlank(message = "竞品名称不能为空")
    @Size(max = 200, message = "竞品名称长度不能超过 200")
    private String competitorName;

    /** 竞品编码 (唯一) */
    @NotBlank(message = "竞品编码不能为空")
    @Size(max = 50, message = "竞品编码长度不能超过 50")
    private String competitorCode;

    /** 简称 (可空) */
    @Size(max = 100, message = "简称长度不能超过 100")
    private String shortName;

    /** 描述 (可空) */
    @Size(max = 1000, message = "描述长度不能超过 1000")
    private String description;

    /** 官网 (可空) */
    @Size(max = 500, message = "官网长度不能超过 500")
    private String website;

    /** Logo URL (可空) */
    @Size(max = 500, message = "Logo URL 长度不能超过 500")
    private String logoUrl;

    /** 所属行业 (可空) */
    @Size(max = 100, message = "行业长度不能超过 100")
    private String industry;

    /** 成立年份 (可空) */
    private Integer foundedYear;

    /** 公司规模: STARTUP / SMALL / MEDIUM / LARGE / ENTERPRISE (可空) */
    @Pattern(regexp = "STARTUP|SMALL|MEDIUM|LARGE|ENTERPRISE",
            message = "公司规模仅支持 STARTUP/SMALL/MEDIUM/LARGE/ENTERPRISE")
    private String companySize;

    /** 总部 (可空) */
    @Size(max = 200, message = "总部长度不能超过 200")
    private String headquarters;

    /** 市场地位: LEADER / CHALLENGER / FOLLOWER / NICHE / NEW_ENTRANT (可空) */
    @Pattern(regexp = "LEADER|CHALLENGER|FOLLOWER|NICHE|NEW_ENTRANT",
            message = "市场地位仅支持 LEADER/CHALLENGER/FOLLOWER/NICHE/NEW_ENTRANT")
    private String marketPosition;

    /** 市场份额 (%) */
    private Double marketShare;

    /** 优势 (可空) */
    @Size(max = 1000, message = "优势长度不能超过 1000")
    private String strengths;

    /** 劣势 (可空) */
    @Size(max = 1000, message = "劣势长度不能超过 1000")
    private String weaknesses;

    /** 威胁等级: LOW / MEDIUM / HIGH / CRITICAL */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL",
            message = "威胁等级仅支持 LOW/MEDIUM/HIGH/CRITICAL")
    private String threatLevel;

    /** 竞争产品 (逗号分隔, 可空) */
    @Size(max = 500, message = "竞争产品长度不能超过 500")
    private String competitiveProducts;

    /** 目标市场 (可空) */
    @Size(max = 500, message = "目标市场长度不能超过 500")
    private String targetMarket;

    /** 定价策略 (可空) */
    @Size(max = 200, message = "定价策略长度不能超过 200")
    private String pricingStrategy;

    /** 商业模式 (可空) */
    @Size(max = 200, message = "商业模式长度不能超过 200")
    private String businessModel;

    /** 融资阶段: BOOTSTRAP / SEED / A / B / C / IPO (可空) */
    @Pattern(regexp = "BOOTSTRAP|SEED|A|B|C|IPO",
            message = "融资阶段仅支持 BOOTSTRAP/SEED/A/B/C/IPO")
    private String fundingStage;

    /** 融资总额 */
    private Double totalFunding;

    /** 关键人物 (可空) */
    @Size(max = 500, message = "关键人物长度不能超过 500")
    private String keyPersonnel;

    /** 社交媒体 JSON: {wechat, weibo, douyin, website} (可空) */
    @Size(max = 1000, message = "社交媒体长度不能超过 1000")
    private String socialMedia;

    /** 是否启用监测 (默认 TRUE) */
    private Boolean monitoringEnabled;

    /** 监测频率: REALTIME / DAILY / WEEKLY / MONTHLY */
    @Pattern(regexp = "REALTIME|DAILY|WEEKLY|MONTHLY",
            message = "监测频率仅支持 REALTIME/DAILY/WEEKLY/MONTHLY")
    private String monitoringFrequency;

    /** 最近监测时间 (可空) */
    private LocalDateTime lastMonitoredAt;

    /** 状态: ACTIVE / INACTIVE / ARCHIVED */
    @Pattern(regexp = "ACTIVE|INACTIVE|ARCHIVED",
            message = "状态仅支持 ACTIVE/INACTIVE/ARCHIVED")
    private String status;

    /** 标签 (可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 备注 (可空) */
    @Size(max = 1000, message = "备注长度不能超过 1000")
    private String notes;

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
