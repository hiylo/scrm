/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocInsightEntity.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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
 * SCRM 客户之声 (VoC) 洞察实体。
 * <p>
 * 承载从主题与声音分析中提炼出的洞察: 洞察标题与类型、描述与摘要、来源主题与声音、相关声音数、
 * 数据点与详细分析 (TEXT)、影响等级与影响领域/受影响客群、预估影响值、建议与行动项 (JSON)、
 * 优先级与状态、审核与发布信息、分享与反馈统计、标签、分析周期与日期。
 * 状态流转 DRAFT → REVIEW → PUBLISHED → ACTED_ON (可 ARCHIVED)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_voc_insight", schema = "scrm", indexes = {
        @Index(name = "idx_voc_insight_type", columnList = "insight_type"),
        @Index(name = "idx_voc_insight_status", columnList = "status"),
        @Index(name = "idx_voc_insight_impact", columnList = "impact_level"),
        @Index(name = "idx_voc_insight_period", columnList = "period"),
        @Index(name = "idx_voc_insight_published_at", columnList = "published_at")
})
@Data
public class ScrmVocInsightEntity {

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

    /** 洞察标题 */
    @Column(name = "insight_title", nullable = false, length = 500)
    private String insightTitle;

    /** 洞察类型: TREND/PATTERN/ANOMALY/OPPORTUNITY/RISK/ROOT_CAUSE/BEST_PRACTICE/LESSON_LEARNED */
    @Column(name = "insight_type", nullable = false, length = 30)
    private String insightType;

    /** 描述 (可空) */
    @Column(name = "description", length = 2000)
    private String description;

    /** 洞察摘要 (可空) */
    @Column(name = "summary", length = 2000)
    private String summary;

    /** 来源主题 ID 列表 (可空, 逗号分隔) */
    @Column(name = "source_topic_ids", length = 500)
    private String sourceTopicIds;

    /** 来源声音 ID 列表 (可空, 逗号分隔) */
    @Column(name = "source_voice_ids", length = 500)
    private String sourceVoiceIds;

    /** 相关声音数 (默认 0) */
    @Column(name = "related_voice_count", nullable = false)
    private Integer relatedVoiceCount;

    /** 数据点 (可空, JSON 数组: [{label,value,trend}]) */
    @Column(name = "data_points", columnDefinition = "TEXT")
    private String dataPoints;

    /** 详细分析 (可空) */
    @Column(name = "analysis", columnDefinition = "TEXT")
    private String analysis;

    /** 影响等级: LOW/MEDIUM/HIGH/CRITICAL (默认 MEDIUM) */
    @Column(name = "impact_level", nullable = false, length = 20)
    private String impactLevel;

    /** 影响领域 (可空, 逗号分隔) */
    @Column(name = "impact_areas", length = 500)
    private String impactAreas;

    /** 受影响客群 (可空, 逗号分隔) */
    @Column(name = "affected_segments", length = 500)
    private String affectedSegments;

    /** 预估影响值 (默认 0) */
    @Column(name = "estimated_impact", nullable = false)
    private Double estimatedImpact;

    /** 建议 (可空) */
    @Column(name = "recommendations", length = 2000)
    private String recommendations;

    /** 行动项 (可空, JSON 数组: [{action,owner,dueDate,priority}]) */
    @Column(name = "action_items", length = 2000)
    private String actionItems;

    /** 优先级: LOW/MEDIUM/HIGH/URGENT (默认 MEDIUM) */
    @Column(name = "priority", nullable = false, length = 20)
    private String priority;

    /** 状态: DRAFT/REVIEW/PUBLISHED/ACTED_ON/ARCHIVED (默认 DRAFT) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /** 审核人 (可空) */
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    /** 审核时间 (可空) */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 发布时间 (可空) */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 发布人 (可空) */
    @Column(name = "published_by", length = 100)
    private String publishedBy;

    /** 分享给 (可空, 用户 ID 逗号分隔) */
    @Column(name = "shared_with", length = 500)
    private String sharedWith;

    /** 反馈数 (默认 0) */
    @Column(name = "feedback_count", nullable = false)
    private Integer feedbackCount;

    /** 反馈平均评分 (默认 0) */
    @Column(name = "feedback_rating", nullable = false)
    private Double feedbackRating;

    /** 标签 (可空, 逗号分隔) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 分析周期 (可空, 如 2026-Q3) */
    @Column(name = "period", length = 50)
    private String period;

    /** 分析日期 (可空) */
    @Column(name = "analysis_date")
    private LocalDate analysisDate;
}
