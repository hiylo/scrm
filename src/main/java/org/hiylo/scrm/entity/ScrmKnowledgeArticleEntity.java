/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeArticleEntity.java
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

import java.time.LocalDateTime;

/**
 * SCRM 知识文章实体。
 * <p>
 * 知识库内容主体, {@link #content} 为 Markdown / HTML / PLAIN / JSON 文本, 由
 * {@link #contentType} 区分。{@link #articleType} 描述文章分类 (文章/FAQ/教程/指南/制度/
 * 产品文档/故障排查/最佳实践)。{@link #status} 流转: DRAFT → PENDING_REVIEW →
 * PUBLISHED → ARCHIVED (REJECTED 为审核驳回)。{@link #versionNumber} 为语义版本号,
 * 与 {@link #currentVersionId} 配合实现版本管理。
 * </p>
 * <p>
 * 互动指标: 浏览 / 独立浏览 / 点赞 / 踩 / 收藏 / 分享 / 评论 / 有用 / 无用 / 评分。
 * {@link #helpfulRate} / {@link #avgRating} 由 Service 在反馈变更时同步计算。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_knowledge_article", schema = "scrm", indexes = {
        @Index(name = "idx_knowledge_article_code", columnList = "article_code", unique = true),
        @Index(name = "idx_knowledge_article_category", columnList = "category_id"),
        @Index(name = "idx_knowledge_article_type", columnList = "article_type"),
        @Index(name = "idx_knowledge_article_status", columnList = "status"),
        @Index(name = "idx_knowledge_article_featured", columnList = "is_featured"),
        @Index(name = "idx_knowledge_article_pinned", columnList = "is_pinned")
})
@Data
public class ScrmKnowledgeArticleEntity {

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

    /** 文章标题 */
    @Column(name = "title", nullable = false, length = 500)
    private String title;

    /** 文章编码 (唯一) */
    @Column(name = "article_code", nullable = false, length = 50)
    private String articleCode;

    /** 分类 ID (可空) */
    @Column(name = "category_id")
    private Long categoryId;

    /** 分类名称 (冗余, 可空) */
    @Column(name = "category_name", length = 200)
    private String categoryName;

    /** 摘要 (可空) */
    @Column(name = "summary", length = 1000)
    private String summary;

    /** 文章内容 (Markdown / HTML / PLAIN / JSON) */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 内容类型: MARKDOWN / HTML / PLAIN / JSON (默认 MARKDOWN) */
    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    /** 文章类型: ARTICLE / FAQ / TUTORIAL / GUIDE / POLICY / PRODUCT_DOC / TROUBLESHOOTING / BEST_PRACTICE (默认 ARTICLE) */
    @Column(name = "article_type", nullable = false, length = 30)
    private String articleType;

    /** 标签 (可空, 逗号分隔) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 关键词 (可空, 逗号分隔) */
    @Column(name = "keywords", length = 500)
    private String keywords;

    /** 封面图 URL (可空) */
    @Column(name = "cover_image", length = 500)
    private String coverImage;

    /** 附件列表 (可空, JSON 字符串) */
    @Column(name = "attachments", length = 1000)
    private String attachments;

    /** 关联文章 ID (可空, 逗号分隔) */
    @Column(name = "related_articles", length = 500)
    private String relatedArticles;

    /** 关联产品 (可空, 逗号分隔) */
    @Column(name = "related_products", length = 500)
    private String relatedProducts;

    /** 适用场景 (可空, 逗号分隔) */
    @Column(name = "applicable_scenarios", length = 500)
    private String applicableScenarios;

    /** 难度: BEGINNER / INTERMEDIATE / ADVANCED / EXPERT (默认 BEGINNER) */
    @Column(name = "difficulty_level", nullable = false, length = 20)
    private String difficultyLevel;

    /** 预计阅读时长分钟 (默认 5) */
    @Column(name = "reading_time_minutes")
    private Integer readingTimeMinutes;

    /** 状态: DRAFT / PENDING_REVIEW / PUBLISHED / ARCHIVED / REJECTED (默认 DRAFT) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 文章版本号 (语义版本, 默认 1, 与乐观锁 version 区分) */
    @Column(name = "version_number")
    private Integer versionNumber;

    /** 当前激活版本 ID (可空, 指向当前生效的版本快照) */
    @Column(name = "current_version_id")
    private Long currentVersionId;

    /** 审核状态: PENDING / APPROVED / REJECTED (默认 PENDING) */
    @Column(name = "review_status", nullable = false, length = 20)
    private String reviewStatus;

    /** 审核人 (可空) */
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    /** 审核时间 (可空) */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 审核意见 (可空) */
    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    /** 发布时间 (可空) */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 最后修改时间 (可空) */
    @Column(name = "last_modified_at")
    private LocalDateTime lastModifiedAt;

    /** 作者 ID (可空) */
    @Column(name = "author_id", length = 100)
    private String authorId;

    /** 作者名称 (可空) */
    @Column(name = "author_name", length = 100)
    private String authorName;

    /** 浏览量 (默认 0) */
    @Column(name = "view_count")
    private Integer viewCount;

    /** 独立浏览量 (默认 0) */
    @Column(name = "unique_view_count")
    private Integer uniqueViewCount;

    /** 点赞数 (默认 0) */
    @Column(name = "like_count")
    private Integer likeCount;

    /** 踩数 (默认 0) */
    @Column(name = "dislike_count")
    private Integer dislikeCount;

    /** 收藏数 (默认 0) */
    @Column(name = "favorite_count")
    private Integer favoriteCount;

    /** 分享数 (默认 0) */
    @Column(name = "share_count")
    private Integer shareCount;

    /** 评论数 (默认 0) */
    @Column(name = "comment_count")
    private Integer commentCount;

    /** 有用数 (默认 0) */
    @Column(name = "helpful_count")
    private Integer helpfulCount;

    /** 无用数 (默认 0) */
    @Column(name = "not_helpful_count")
    private Integer notHelpfulCount;

    /** 有用率 (默认 0, helpfulCount / (helpfulCount + notHelpfulCount)) */
    @Column(name = "helpful_rate")
    private Double helpfulRate;

    /** 平均评分 (默认 0) */
    @Column(name = "avg_rating")
    private Double avgRating;

    /** 评分人数 (默认 0) */
    @Column(name = "rating_count")
    private Integer ratingCount;

    /** 是否精选 (默认 FALSE) */
    @Column(name = "is_featured", nullable = false)
    private Boolean isFeatured;

    /** 是否置顶 (默认 FALSE) */
    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned;

    /** 排序序号 (默认 0) */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
