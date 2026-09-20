/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContentEntity.java
 * Date : 2026/08/04 08:40:58
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

import java.time.LocalDateTime;

/**
 * SCRM 内容营销实体。
 * <p>
 * 承载内容创作主体: 标题 / 摘要 / 富文本正文 / 媒体资源 / SEO 信息 / 互动计数。
 * 状态流转: DRAFT (草稿) → PENDING_REVIEW (待审核) → APPROVED (已通过) /
 * REJECTED (已驳回) → SCHEDULED (已排期) → PUBLISHED (已发布) / ARCHIVED (已归档)。
 * {@link #reviewStatus} 记录最近一次审核结果, {@link #tags} / {@link #targetAudience}
 * 以逗号分隔存储多值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_content", schema = "scrm", indexes = {
        @Index(name = "idx_content_type", columnList = "content_type"),
        @Index(name = "idx_content_status", columnList = "status"),
        @Index(name = "idx_content_author", columnList = "author_id"),
        @Index(name = "idx_content_category", columnList = "category")
})
@Data
public class ScrmContentEntity {

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

    /** 内容标题 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 内容类型: ARTICLE / VIDEO / IMAGE / POSTER / LIVE_SHORT / INFOGRAPHIC / PDF */
    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    /** 内容分类 (可空) */
    @Column(name = "category", length = 100)
    private String category;

    /** 摘要 (可空) */
    @Column(name = "summary", length = 500)
    private String summary;

    /** 正文内容 / 富文本 (可空) */
    @Column(name = "body_content", columnDefinition = "TEXT")
    private String bodyContent;

    /** 封面图 URL (可空) */
    @Column(name = "cover_image", length = 500)
    private String coverImage;

    /** 媒体文件 URL (可空) */
    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    /** 媒体时长秒 (可空) */
    @Column(name = "media_duration")
    private Integer mediaDuration;

    /** 标签 (逗号分隔, 可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 目标受众 (逗号分隔, 可空) */
    @Column(name = "target_audience", length = 500)
    private String targetAudience;

    /** 作者 ID (可空) */
    @Column(name = "author_id", length = 100)
    private String authorId;

    /** 作者名称 (可空) */
    @Column(name = "author_name", length = 100)
    private String authorName;

    /** 状态: DRAFT / PENDING_REVIEW / APPROVED / SCHEDULED / PUBLISHED / ARCHIVED / REJECTED (默认 DRAFT) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 审核状态: PENDING / APPROVED / REJECTED (可空) */
    @Column(name = "review_status", length = 20)
    private String reviewStatus;

    /** 审核人 ID (可空) */
    @Column(name = "reviewer_id", length = 100)
    private String reviewerId;

    /** 审核人名称 (可空) */
    @Column(name = "reviewer_name", length = 100)
    private String reviewerName;

    /** 审核时间 (可空) */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 审核意见 (可空) */
    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    /** 发布时间 (可空) */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 计划发布时间 (可空) */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    /** 浏览数 (默认 0) */
    @Column(name = "view_count")
    private Integer viewCount;

    /** 点赞数 (默认 0) */
    @Column(name = "like_count")
    private Integer likeCount;

    /** 分享数 (默认 0) */
    @Column(name = "share_count")
    private Integer shareCount;

    /** 评论数 (默认 0) */
    @Column(name = "comment_count")
    private Integer commentCount;

    /** 收藏数 (默认 0) */
    @Column(name = "collect_count")
    private Integer collectCount;

    /** 转化数 (默认 0) */
    @Column(name = "conversion_count")
    private Integer conversionCount;

    /** SEO 标题 (可空) */
    @Column(name = "seo_title", length = 200)
    private String seoTitle;

    /** SEO 描述 (可空) */
    @Column(name = "seo_description", length = 500)
    private String seoDescription;

    /** SEO 关键字 (逗号分隔, 可空) */
    @Column(name = "seo_keywords", length = 500)
    private String seoKeywords;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
