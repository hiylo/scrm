/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompetitorEntity.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 竞品信息实体。
 * <p>
 * 记录竞品企业基础信息与竞争态势: 公司规模 / 市场地位 / 市场份额 / 优势劣势 / 威胁等级 /
 * 融资情况 / 监测配置等。{@link #competitorCode} 为唯一编码, 用于业务侧引用。
 * 监测开关 {@link #monitoringEnabled} 与频率 {@link #monitoringFrequency} 控制定时抓取节奏。
 * </p>
 * <p>
 * 状态流转: ACTIVE (活跃) → INACTIVE (停用) → ARCHIVED (归档)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_competitor", schema = "scrm", indexes = {
        @Index(name = "idx_competitor_code", columnList = "competitor_code", unique = true),
        @Index(name = "idx_competitor_industry", columnList = "industry"),
        @Index(name = "idx_competitor_threat_level", columnList = "threat_level"),
        @Index(name = "idx_competitor_status", columnList = "status"),
        @Index(name = "idx_competitor_monitoring", columnList = "monitoring_enabled"),
        @Index(name = "idx_competitor_last_monitored", columnList = "last_monitored_at")
})
@Data
public class ScrmCompetitorEntity {

    // ==================== 公共字段 ====================

    /** 主键 ID（Snowflake 雪花算法生成） */
    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", strategy = "org.hiylo.scrm.config.SnowflakeIdGenerator")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 创建时间 */
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /** 乐观锁版本号（并发更新保护, 后写入者触发 OptimisticLockException） */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 持久化前回调: 自动填充创建/更新时间与版本号初值
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
    }

    /**
     * 更新前回调: 自动刷新更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // ==================== 业务字段 ====================

    /** 竞品名称 */
    @Column(name = "competitor_name", nullable = false, length = 200)
    private String competitorName;

    /** 竞品编码 (唯一) */
    @Column(name = "competitor_code", nullable = false, length = 50)
    private String competitorCode;

    /** 简称 (可空) */
    @Column(name = "short_name", length = 100)
    private String shortName;

    /** 描述 (可空) */
    @Column(name = "description", length = 1000)
    private String description;

    /** 官网 (可空) */
    @Column(name = "website", length = 500)
    private String website;

    /** Logo URL (可空) */
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /** 所属行业 (可空) */
    @Column(name = "industry", length = 100)
    private String industry;

    /** 成立年份 (可空) */
    @Column(name = "founded_year")
    private Integer foundedYear;

    /** 公司规模: STARTUP / SMALL / MEDIUM / LARGE / ENTERPRISE (可空) */
    @Column(name = "company_size", length = 50)
    private String companySize;

    /** 总部 (可空) */
    @Column(name = "headquarters", length = 200)
    private String headquarters;

    /** 市场地位: LEADER / CHALLENGER / FOLLOWER / NICHE / NEW_ENTRANT (可空) */
    @Column(name = "market_position", length = 50)
    private String marketPosition;

    /** 市场份额 (%) */
    @Column(name = "market_share", nullable = false)
    private Double marketShare;

    /** 优势 (可空) */
    @Column(name = "strengths", length = 1000)
    private String strengths;

    /** 劣势 (可空) */
    @Column(name = "weaknesses", length = 1000)
    private String weaknesses;

    /** 威胁等级: LOW / MEDIUM / HIGH / CRITICAL (默认 MEDIUM) */
    @Column(name = "threat_level", nullable = false, length = 20)
    private String threatLevel;

    /** 竞争产品 (逗号分隔, 可空) */
    @Column(name = "competitive_products", length = 500)
    private String competitiveProducts;

    /** 目标市场 (可空) */
    @Column(name = "target_market", length = 500)
    private String targetMarket;

    /** 定价策略 (可空) */
    @Column(name = "pricing_strategy", length = 200)
    private String pricingStrategy;

    /** 商业模式 (可空) */
    @Column(name = "business_model", length = 200)
    private String businessModel;

    /** 融资阶段: BOOTSTRAP / SEED / A / B / C / IPO (可空) */
    @Column(name = "funding_stage", length = 50)
    private String fundingStage;

    /** 融资总额 */
    @Column(name = "total_funding", nullable = false)
    private Double totalFunding;

    /** 关键人物 (可空) */
    @Column(name = "key_personnel", length = 500)
    private String keyPersonnel;

    /** 社交媒体 JSON: {wechat, weibo, douyin, website} (可空) */
    @Column(name = "social_media", length = 1000)
    private String socialMedia;

    /** 是否启用监测 (默认 TRUE) */
    @Column(name = "monitoring_enabled", nullable = false)
    private Boolean monitoringEnabled;

    /** 监测频率: REALTIME / DAILY / WEEKLY / MONTHLY (默认 DAILY) */
    @Column(name = "monitoring_frequency", nullable = false, length = 20)
    private String monitoringFrequency;

    /** 最近监测时间 (可空) */
    @Column(name = "last_monitored_at")
    private LocalDateTime lastMonitoredAt;

    /** 状态: ACTIVE / INACTIVE / ARCHIVED (默认 ACTIVE) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 标签 (可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 备注 (可空) */
    @Column(name = "notes", length = 1000)
    private String notes;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
