/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSurveyResponseEntity.java
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
 * SCRM 调查回复实体。
 * <p>
 * 记录客户 ({@link #customerId}) 对调查问卷 ({@link #surveyId}) 的一次回答。{@link #invitationId}
 * 关联触发本次回答的邀请 (可空, 表示匿名 / 主动填写); {@link #responses} 为 JSON 字符串描述回答列表
 * (结构: {@code [{questionId,answer,value}]})。
 * </p>
 * <p>
 * 系统根据回答自动计算 NPS / CSAT / CES 分数与综合得分 ({@link #npsScore} / {@link #csatScore} /
 * {@link #cesScore} / {@link #overallScore}), {@link #sentiment} 为情感倾向 (POSITIVE/NEUTRAL/NEGATIVE)。
 * {@link #followUpRequired} / {@link #followUpStatus} / {@link #assigneeId} 支持负面反馈跟进闭环。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_survey_response", schema = "scrm", indexes = {
        @Index(name = "idx_survey_response_survey", columnList = "survey_id"),
        @Index(name = "idx_survey_response_invitation", columnList = "invitation_id"),
        @Index(name = "idx_survey_response_customer", columnList = "customer_id"),
        @Index(name = "idx_survey_response_nps", columnList = "nps_score"),
        @Index(name = "idx_survey_response_sentiment", columnList = "sentiment"),
        @Index(name = "idx_survey_response_follow_up", columnList = "follow_up_required"),
        @Index(name = "idx_survey_response_submitted", columnList = "submitted_at")
})
@Data
public class ScrmSurveyResponseEntity {

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

    /** 调查问卷 ID */
    @Column(name = "survey_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long surveyId;

    /** 邀请 ID (可空, 为空表示匿名 / 主动填写) */
    @Column(name = "invitation_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long invitationId;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (冗余, 便于列表展示, 可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** JSON 回答: [{questionId,answer,value}] */
    @Column(name = "responses", nullable = false, columnDefinition = "TEXT")
    private String responses;

    /** NPS 得分 0-10 (可空) */
    @Column(name = "nps_score")
    private Integer npsScore;

    /** CSAT 得分 1-5 (可空) */
    @Column(name = "csat_score")
    private Integer csatScore;

    /** CES 得分 1-7 (可空) */
    @Column(name = "ces_score")
    private Integer cesScore;

    /** 综合得分 (可空, 各量表归一化加权) */
    @Column(name = "overall_score")
    private Double overallScore;

    /** 情感: POSITIVE / NEUTRAL / NEGATIVE (可空) */
    @Column(name = "sentiment", length = 20)
    private String sentiment;

    /** 文字反馈 (可空) */
    @Column(name = "feedback_text", columnDefinition = "TEXT")
    private String feedbackText;

    /** 标签 (可空, 逗号分隔) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 是否需要跟进 (默认 FALSE) */
    @Column(name = "follow_up_required", nullable = false)
    private Boolean followUpRequired;

    /** 跟进状态: PENDING / IN_PROGRESS / COMPLETED (可空) */
    @Column(name = "follow_up_status", length = 20)
    private String followUpStatus;

    /** 跟进人 ID (可空) */
    @Column(name = "assignee_id", length = 100)
    private String assigneeId;

    /** 提交时间 */
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    /** 完成耗时秒 (可空) */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** 客户端 IP (可空) */
    @Column(name = "client_ip", length = 100)
    private String clientIp;

    /** User-Agent (可空) */
    @Column(name = "user_agent", length = 500)
    private String userAgent;
}
