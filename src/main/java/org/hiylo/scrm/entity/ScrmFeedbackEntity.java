/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFeedbackEntity.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 客户反馈实体。
 * <p>
 * 承载客户反馈的核心数据: 反馈编号、标题、内容、类型、分类、优先级、状态、来源、客户信息、
 * 关联订单与产品、关联工单、情感分析结果、处理人与处理团队、SLA 时间戳 (分配/首次响应/解决/关闭)、
 * 响应与解决时长、解决方案、处理满意度、公开/匿名标识、浏览/赞同/评论计数。
 * 反馈状态流转 NEW → IN_REVIEW → IN_PROGRESS → RESOLVED → CLOSED (可 REJECTED / DUPLICATE)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_feedback", schema = "scrm", indexes = {
        @Index(name = "idx_feedback_customer", columnList = "customer_id"),
        @Index(name = "idx_feedback_assignee", columnList = "assignee_id"),
        @Index(name = "idx_feedback_status", columnList = "status"),
        @Index(name = "idx_feedback_priority", columnList = "priority"),
        @Index(name = "idx_feedback_type", columnList = "feedback_type"),
        @Index(name = "idx_feedback_category", columnList = "category"),
        @Index(name = "idx_feedback_sentiment", columnList = "sentiment"),
        @Index(name = "idx_feedback_created", columnList = "create_time")
})
@Data
public class ScrmFeedbackEntity {

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

    /** 反馈编号 (唯一, FB + 年月日 + 序号) */
    @Column(name = "feedback_no", nullable = false, length = 50, unique = true)
    private String feedbackNo;

    /** 反馈标题 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 反馈内容 */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

/** 反馈类型: SUGGESTION / COMPLAINT / COMPLIMENT / BUG_REPORT / FEATURE_REQUEST / SERVICE_ISSUE / PRODUCT_ISSUE / OTHER
         * */
    @Column(name = "feedback_type", nullable = false, length = 30)
    private String feedbackType;

    /** 反馈分类 (可空) */
    @Column(name = "category", length = 100)
    private String category;

    /** 优先级: URGENT / HIGH / MEDIUM / LOW */
    @Column(name = "priority", nullable = false, length = 10)
    private String priority;

    /** 状态: NEW / IN_REVIEW / IN_PROGRESS / RESOLVED / CLOSED / REJECTED / DUPLICATE */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 来源: CUSTOMER / AGENT / SYSTEM / SURVEY / SOCIAL / EMAIL / PHONE / CHAT */
    @Column(name = "source", nullable = false, length = 30)
    private String source;

    /** 客户 ID (可空, 匿名反馈时为空) */
    @Column(name = "customer_id")
    private Long customerId;

    /** 客户名称 (可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 客户电话 (可空) */
    @Column(name = "customer_phone", length = 50)
    private String customerPhone;

    /** 客户邮箱 (可空) */
    @Column(name = "customer_email", length = 200)
    private String customerEmail;

    /** 客户等级 (可空) */
    @Column(name = "customer_level", length = 50)
    private String customerLevel;

    /** 关联订单 ID (可空) */
    @Column(name = "order_id", length = 100)
    private String orderId;

    /** 关联产品 ID (可空) */
    @Column(name = "product_id", length = 100)
    private String productId;

    /** 关联工单 ID (可空) */
    @Column(name = "ticket_id")
    private Long ticketId;

    /** 情感: POSITIVE / NEUTRAL / NEGATIVE (可空) */
    @Column(name = "sentiment", length = 20)
    private String sentiment;

    /** 情感得分 (-1.0 ~ 1.0, 默认 0) */
    @Column(name = "sentiment_score")
    private Double sentimentScore;

    /** 评分 (1-5, 可空) */
    @Column(name = "rating")
    private Integer rating;

    /** 标签 (可空, 逗号分隔) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 附件列表 (可空, JSON 数组) */
    @Column(name = "attachments", length = 1000)
    private String attachments;

    /** 处理人 ID (可空) */
    @Column(name = "assignee_id", length = 100)
    private String assigneeId;

    /** 处理人名称 (可空) */
    @Column(name = "assignee_name", length = 100)
    private String assigneeName;

    /** 处理团队 ID (可空) */
    @Column(name = "team_id", length = 100)
    private String teamId;

    /** 分配时间 (可空) */
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /** 首次响应时间 (可空) */
    @Column(name = "first_response_at")
    private LocalDateTime firstResponseAt;

    /** 解决时间 (可空) */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** 关闭时间 (可空) */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** 响应时长 (小时, 可空) */
    @Column(name = "response_time_hours")
    private Integer responseTimeHours;

    /** 解决时长 (小时, 可空) */
    @Column(name = "resolution_time_hours")
    private Integer resolutionTimeHours;

    /** 解决方案 (可空) */
    @Column(name = "resolution", length = 1000)
    private String resolution;

    /** 处理满意度评分 (1-5, 可空) */
    @Column(name = "satisfaction_score")
    private Integer satisfactionScore;

    /** 是否公开 (默认 FALSE) */
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic;

    /** 是否匿名 (默认 FALSE) */
    @Column(name = "is_anonymous", nullable = false)
    private Boolean isAnonymous;

    /** 浏览数 (默认 0) */
    @Column(name = "view_count")
    private Integer viewCount;

    /** 赞同数 (默认 0) */
    @Column(name = "upvote_count")
    private Integer upvoteCount;

    /** 评论数 (默认 0) */
    @Column(name = "comment_count")
    private Integer commentCount;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
