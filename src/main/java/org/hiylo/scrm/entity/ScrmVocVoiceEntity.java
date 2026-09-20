/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocVoiceEntity.java
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
 * SCRM 客户之声 (VoC) 声音实体。
 * <p>
 * 承载全渠道收集的客户声音核心数据: 声音编号、客户信息、来源渠道、声音类型、内容与原始内容、
 * 语言、情感与情感分、优先级、分类与子分类、标签、评分、关联产品/订单/服务、责任部门与处理人、
 * 状态与时间戳 (收集/分析/解决)、解决方案、解决后满意度、公开/验证标识、互动计数 (回复/点赞/查看/分享)、
 * 附件与元数据、关联声音。
 * 状态流转 NEW → ANALYZING → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED (可 ARCHIVED / IGNORED)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_voc_voice", schema = "scrm", indexes = {
        @Index(name = "idx_voc_voice_customer", columnList = "customer_id"),
        @Index(name = "idx_voc_voice_source", columnList = "source"),
        @Index(name = "idx_voc_voice_type", columnList = "voice_type"),
        @Index(name = "idx_voc_voice_sentiment", columnList = "sentiment"),
        @Index(name = "idx_voc_voice_status", columnList = "status"),
        @Index(name = "idx_voc_voice_priority", columnList = "priority"),
        @Index(name = "idx_voc_voice_category", columnList = "category"),
        @Index(name = "idx_voc_voice_collected", columnList = "collected_at"),
        @Index(name = "idx_voc_voice_assigned", columnList = "assigned_to")
})
@Data
public class ScrmVocVoiceEntity {

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

    /** 声音编号 (唯一, VOC + 年月日 + 4 位序号) */
    @Column(name = "voice_no", nullable = false, length = 100, unique = true)
    private String voiceNo;

    /** 客户 ID (可空, 匿名声音时为空) */
    @Column(name = "customer_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 客户联系方式 (可空) */
    @Column(name = "customer_contact", length = 200)
    private String customerContact;

/** 来源渠道: SURVEY/INTERVIEW/REVIEW/SOCIAL_MEDIA/CALL_CENTER/EMAIL/CHAT/APP_STORE/REFERRAL/COMPLAINT/SUGGESTION/OTHER
         * */
    @Column(name = "source", nullable = false, length = 30)
    private String source;

    /** 来源详情 (可空) */
    @Column(name = "source_detail", length = 200)
    private String sourceDetail;

    /** 来源链接 (可空) */
    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    /** 声音类型: COMPLAINT/COMPLIMENT/SUGGESTION/QUESTION/FEEDBACK/REVIEW/RATING/INQUIRY */
    @Column(name = "voice_type", nullable = false, length = 30)
    private String voiceType;

    /** 主题 (可空) */
    @Column(name = "title", length = 500)
    private String title;

    /** 声音内容 */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 原始内容 (可空, 未清洗/翻译前的原始文本) */
    @Column(name = "original_content", columnDefinition = "TEXT")
    private String originalContent;

    /** 语言 (默认 zh-CN) */
    @Column(name = "language", nullable = false, length = 20)
    private String language;

    /** 情感: POSITIVE/NEUTRAL/NEGATIVE/MIXED (默认 NEUTRAL) */
    @Column(name = "sentiment", nullable = false, length = 20)
    private String sentiment;

    /** 情感得分 (-1.0 ~ 1.0, 默认 0) */
    @Column(name = "sentiment_score", nullable = false)
    private Double sentimentScore;

    /** 优先级: LOW/MEDIUM/HIGH/URGENT (默认 MEDIUM) */
    @Column(name = "priority", nullable = false, length = 20)
    private String priority;

    /** 分类 (可空) */
    @Column(name = "category", length = 100)
    private String category;

    /** 子分类 (可空) */
    @Column(name = "sub_category", length = 100)
    private String subCategory;

    /** 标签 (可空, 逗号分隔) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 评分 (1-5, 可空) */
    @Column(name = "rating")
    private Integer rating;

    /** 关联产品 ID (可空) */
    @Column(name = "product_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long productId;

    /** 关联产品名称 (可空) */
    @Column(name = "product_name", length = 200)
    private String productName;

    /** 关联订单 ID (可空) */
    @Column(name = "order_id", length = 100)
    private String orderId;

    /** 关联服务 ID (可空) */
    @Column(name = "service_id", length = 100)
    private String serviceId;

    /** 责任部门 (可空) */
    @Column(name = "department", length = 200)
    private String department;

    /** 处理人 (可空) */
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    /** 分配时间 (可空) */
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /** 状态: NEW/ANALYZING/ASSIGNED/IN_PROGRESS/RESOLVED/CLOSED/ARCHIVED/IGNORED (默认 NEW) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 收集时间 */
    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    /** 收集人 (可空) */
    @Column(name = "collected_by", length = 100)
    private String collectedBy;

    /** 分析时间 (可空) */
    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    /** 解决时间 (可空) */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** 解决时长 (小时, 默认 0) */
    @Column(name = "resolution_time_hours", nullable = false)
    private Integer resolutionTimeHours;

    /** 解决方案 (可空) */
    @Column(name = "resolution", length = 2000)
    private String resolution;

    /** 解决后满意度 (1-5, 可空) */
    @Column(name = "customer_satisfaction")
    private Integer customerSatisfaction;

    /** 是否公开 (默认 FALSE) */
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic;

    /** 是否已验证 (默认 FALSE) */
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified;

    /** 验证人 (可空) */
    @Column(name = "verified_by", length = 100)
    private String verifiedBy;

    /** 回复数 (默认 0) */
    @Column(name = "response_count", nullable = false)
    private Integer responseCount;

    /** 点赞数 (默认 0) */
    @Column(name = "like_count", nullable = false)
    private Integer likeCount;

    /** 查看数 (默认 0) */
    @Column(name = "view_count", nullable = false)
    private Integer viewCount;

    /** 分享数 (默认 0) */
    @Column(name = "share_count", nullable = false)
    private Integer shareCount;

    /** 附件 (可空, JSON 数组) */
    @Column(name = "attachments", length = 1000)
    private String attachments;

    /** 附加数据 (可空, JSON) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /** 关联声音 ID 列表 (可空, 逗号分隔) */
    @Column(name = "related_voice_ids", length = 500)
    private String relatedVoiceIds;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
