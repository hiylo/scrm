/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeFeedbackEntity.java
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
 * SCRM 知识反馈实体。
 * <p>
 * 记录用户对知识文章的反馈行为, {@link #feedbackType} 区分: HELPFUL / NOT_HELPFUL / LIKE /
 * DISLIKE / FAVORITE / SHARE / RATING / COMMENT / REPORT。{@link #commentType} 进一步细分
 * 评论类型 (COMMENT / QUESTION / SUGGESTION / ISSUE), {@link #parentCommentId} 支持评论回复。
 * {@link #status} 标识反馈处理状态 (ACTIVE / RESOLVED / DISMISSED / HIDDEN)。
 * {@link #metadata} 为 JSON 附加数据 (可空)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_knowledge_feedback", schema = "scrm", indexes = {
        @Index(name = "idx_knowledge_feedback_article", columnList = "article_id"),
        @Index(name = "idx_knowledge_feedback_type", columnList = "feedback_type"),
        @Index(name = "idx_knowledge_feedback_user", columnList = "user_id"),
        @Index(name = "idx_knowledge_feedback_status", columnList = "status")
})
@Data
public class ScrmKnowledgeFeedbackEntity {

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

    /** 文章 ID */
    @Column(name = "article_id", nullable = false)
    private Long articleId;

    /** 文章标题 (冗余, 可空) */
    @Column(name = "article_title", length = 500)
    private String articleTitle;

    /** 反馈类型: HELPFUL / NOT_HELPFUL / LIKE / DISLIKE / FAVORITE / SHARE / RATING / COMMENT / REPORT */
    @Column(name = "feedback_type", nullable = false, length = 20)
    private String feedbackType;

    /** 用户 ID */
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    /** 用户名称 (可空) */
    @Column(name = "user_name", length = 100)
    private String userName;

    /** 用户角色 (可空) */
    @Column(name = "user_role", length = 50)
    private String userRole;

    /** 评分 1-5 (可空, 仅 RATING 类型有效) */
    @Column(name = "rating")
    private Integer rating;

    /** 评论内容 (可空) */
    @Column(name = "comment", length = 2000)
    private String comment;

    /** 评论类型 (可空): COMMENT / QUESTION / SUGGESTION / ISSUE */
    @Column(name = "comment_type", length = 20)
    private String commentType;

    /** 父评论 ID (可空, 用于回复) */
    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    /** 是否内部备注 (默认 FALSE) */
    @Column(name = "is_internal", nullable = false)
    private Boolean isInternal;

    /** 举报原因 (可空, 仅 REPORT 类型有效) */
    @Column(name = "report_reason", length = 500)
    private String reportReason;

    /** 状态: ACTIVE / RESOLVED / DISMISSED / HIDDEN (默认 ACTIVE) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 解决人 (可空) */
    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    /** 解决时间 (可空) */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** 解决备注 (可空) */
    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    /** 赞同数 (默认 0) */
    @Column(name = "upvote_count")
    private Integer upvoteCount;

    /** JSON 附加数据 (可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
