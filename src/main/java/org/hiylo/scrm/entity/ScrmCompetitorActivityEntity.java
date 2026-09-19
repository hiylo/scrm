/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompetitorActivityEntity.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 竞品动态实体。
 * <p>
 * 记录竞品单条市场动态: 新品发布 / 价格变动 / 营销活动 / 融资 / 并购 / 公关事件等。
 * 每条动态携带影响等级 ({@link #impactLevel}) 与影响分析, 关联受影响产品与客群,
 * 并跟踪我方应对流程: PENDING → PLANNING → EXECUTING → COMPLETED / NO_ACTION。
 * {@link #importanceScore} (0-100) 为综合重要性评分, {@link #isVerified} 标记是否已人工核实。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_competitor_activity", schema = "scrm", indexes = {
        @Index(name = "idx_competitor_activity_competitor", columnList = "competitor_id"),
        @Index(name = "idx_competitor_activity_type", columnList = "activity_type"),
        @Index(name = "idx_competitor_activity_impact", columnList = "impact_level"),
        @Index(name = "idx_competitor_activity_response", columnList = "response_status"),
        @Index(name = "idx_competitor_activity_date", columnList = "activity_date"),
        @Index(name = "idx_competitor_activity_importance", columnList = "importance_score")
})
@Data
public class ScrmCompetitorActivityEntity {

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

    /** 竞品 ID */
    @Column(name = "competitor_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long competitorId;

    /** 竞品名称 (冗余, 便于列表展示) */
    @Column(name = "competitor_name", length = 200)
    private String competitorName;

    /** 动态类型: PRODUCT_LAUNCH / PRICE_CHANGE / MARKETING_CAMPAIGN / FUNDING / PARTNERSHIP /
     *  HIRING / EXPANSION / CONTENT / PR_EVENT / ACQUISITION / PROMOTION / FEATURE_UPDATE /
     *  CRISIS / OTHER */
    @Column(name = "activity_type", nullable = false, length = 30)
    private String activityType;

    /** 动态标题 */
    @Column(name = "title", nullable = false, length = 500)
    private String title;

    /** 动态摘要 (可空) */
    @Column(name = "summary", length = 2000)
    private String summary;

    /** 详细描述 (可空) */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 动态日期 */
    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    /** 来源 (可空) */
    @Column(name = "source", length = 200)
    private String source;

    /** 来源链接 (可空) */
    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    /** 影响等级: LOW / MEDIUM / HIGH / CRITICAL (默认 MEDIUM) */
    @Column(name = "impact_level", nullable = false, length = 20)
    private String impactLevel;

    /** 影响分析 (可空) */
    @Column(name = "impact_analysis", length = 1000)
    private String impactAnalysis;

    /** 受影响产品 (可空) */
    @Column(name = "affected_products", length = 500)
    private String affectedProducts;

    /** 受影响客群 (可空) */
    @Column(name = "affected_segments", length = 500)
    private String affectedSegments;

    /** 我方应对策略 (可空) */
    @Column(name = "our_response", length = 1000)
    private String ourResponse;

    /** 应对状态: PENDING / PLANNING / EXECUTING / COMPLETED / NO_ACTION (默认 PENDING) */
    @Column(name = "response_status", nullable = false, length = 20)
    private String responseStatus;

    /** 应对负责人 (可空) */
    @Column(name = "response_owner", length = 100)
    private String responseOwner;

    /** 应对截止日期 (可空) */
    @Column(name = "response_due_date")
    private LocalDate responseDueDate;

    /** 发现者 (可空) */
    @Column(name = "detected_by", length = 100)
    private String detectedBy;

    /** 发现方式: MANUAL / AUTOMATED / ALERT / INTEL (可空) */
    @Column(name = "detection_method", length = 50)
    private String detectionMethod;

    /** 重要性评分 (0-100, 默认 50) */
    @Column(name = "importance_score", nullable = false)
    private Integer importanceScore;

    /** 是否已验证 (默认 FALSE) */
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified;

    /** 验证人 (可空) */
    @Column(name = "verified_by", length = 100)
    private String verifiedBy;

    /** 验证时间 (可空) */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /** 标签 (可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 附件 JSON (可空) */
    @Column(name = "attachments", length = 1000)
    private String attachments;

    /** 相关联动态 ID (可空, 逗号分隔) */
    @Column(name = "related_activity_ids", length = 500)
    private String relatedActivityIds;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
