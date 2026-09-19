/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSalesSpeechEntity.java
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
 * SCRM 销售话术库实体。
 * <p>
 * 描述某场景下的一条话术条目: {@link #speechContent} (TEXT) 为话术正文,
 * {@link #speechType} 区分话术类型 (TEXT/SCRIPT/QA/GUIDE/TEMPLATE),
 * {@link #speechStyle} 描述话术风格 (FORMAL/FRIENDLY/PROFESSIONAL/CASUAL/PERSUASIVE/EMPATHETIC),
 * {@link #keywords} / {@link #variables} / {@link #tags} 用于匹配与渲染,
 * {@link #difficultyLevel} 标记难度, {@link #rating} / {@link #usageCount} /
 * {@link #successCount} / {@link #feedbackCount} / {@link #positiveFeedback} /
 * {@link #negativeFeedback} 为话术级使用与反馈统计, {@link #isRecommended} /
 * {@link #isVerified} 标记推荐与验证状态。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_sales_speech", schema = "scrm", indexes = {
        @Index(name = "idx_sales_speech_scenario", columnList = "scenario_id"),
        @Index(name = "idx_sales_speech_type", columnList = "speech_type"),
        @Index(name = "idx_sales_speech_style", columnList = "speech_style"),
        @Index(name = "idx_sales_speech_difficulty", columnList = "difficulty_level"),
        @Index(name = "idx_sales_speech_recommended", columnList = "is_recommended"),
        @Index(name = "idx_sales_speech_verified", columnList = "is_verified"),
        @Index(name = "idx_sales_speech_enabled", columnList = "enabled")
})
@Data
public class ScrmSalesSpeechEntity {

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

    /** 场景 ID */
    @Column(name = "scenario_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long scenarioId;

    /** 场景名称 (冗余, 便于列表展示, 可空) */
    @Column(name = "scenario_name", length = 200)
    private String scenarioName;

    /** 话术标题 */
    @Column(name = "speech_title", nullable = false, length = 200)
    private String speechTitle;

    /** 话术内容 */
    @Column(name = "speech_content", columnDefinition = "TEXT", nullable = false)
    private String speechContent;

    /** 话术类型: TEXT/SCRIPT/QA/GUIDE/TEMPLATE (默认 TEXT) */
    @Column(name = "speech_type", nullable = false, length = 30)
    private String speechType;

    /** 话术风格: FORMAL/FRIENDLY/PROFESSIONAL/CASUAL/PERSUASIVE/EMPATHETIC (可空) */
    @Column(name = "speech_style", length = 30)
    private String speechStyle;

    /** 目标受众 (可空) */
    @Column(name = "target_audience", length = 500)
    private String targetAudience;

    /** 适用产品 (逗号分隔, 可空) */
    @Column(name = "applicable_products", length = 500)
    private String applicableProducts;

    /** 适用场景 (逗号分隔, 可空) */
    @Column(name = "applicable_scenes", length = 500)
    private String applicableScenes;

    /** 关键词 (逗号分隔, 可空) */
    @Column(name = "keywords", length = 500)
    private String keywords;

    /** 可用变量 (逗号分隔, 如 {customerName},{productName},{price}, 可空) */
    @Column(name = "variables", length = 500)
    private String variables;

    /** 媒体附件 JSON (可空) */
    @Column(name = "media_attachments", length = 1000)
    private String mediaAttachments;

    /** 难度等级: BEGINNER/INTERMEDIATE/ADVANCED/EXPERT (默认 INTERMEDIATE) */
    @Column(name = "difficulty_level", nullable = false, length = 20)
    private String difficultyLevel;

    /** 预计时长 (秒, 默认 0) */
    @Column(name = "estimated_duration")
    private Integer estimatedDuration;

    /** 评分 (默认 0) */
    @Column(name = "rating")
    private Double rating;

    /** 使用次数 (默认 0) */
    @Column(name = "usage_count")
    private Integer usageCount;

    /** 成功次数 (默认 0) */
    @Column(name = "success_count")
    private Integer successCount;

    /** 反馈数 (默认 0) */
    @Column(name = "feedback_count")
    private Integer feedbackCount;

    /** 正面反馈数 (默认 0) */
    @Column(name = "positive_feedback")
    private Integer positiveFeedback;

    /** 负面反馈数 (默认 0) */
    @Column(name = "negative_feedback")
    private Integer negativeFeedback;

    /** 是否推荐 (默认 FALSE) */
    @Column(name = "is_recommended", nullable = false)
    private Boolean isRecommended;

    /** 是否验证 (默认 FALSE) */
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified;

    /** 业务版本号 (默认 1, 区别于乐观锁 version) */
    @Column(name = "version_no")
    private Integer versionNo;

    /** 作者 ID (可空) */
    @Column(name = "author_id", length = 100)
    private String authorId;

    /** 作者名称 (可空) */
    @Column(name = "author_name", length = 100)
    private String authorName;

    /** 标签 (逗号分隔, 可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
