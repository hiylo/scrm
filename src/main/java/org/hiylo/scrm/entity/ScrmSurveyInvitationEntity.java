/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSurveyInvitationEntity.java
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 调查邀请实体。
 * <p>
 * 记录向客户 ({@link #customerId}) 发送调查问卷 ({@link #surveyId}) 的邀请。{@link #invitationCode}
 * 为唯一邀请码, 客户凭此提交回答; {@link #channel} 标注分发渠道 (IN_APP/SMS/EMAIL/WECHAT);
 * {@link #status} 跟踪邀请生命周期 (PENDING/SENT/OPENED/IN_PROGRESS/COMPLETED/EXPIRED/BOUNCED)。
 * </p>
 * <p>
 * {@link #sentAt} / {@link #openedAt} / {@link #completedAt} / {@link #expiredAt} 记录关键时间点;
 * {@link #reminderCount} / {@link #lastReminderAt} 跟踪提醒次数; {@link #sourceEvent} / {@link #sourceId}
 * 用于追溯触发来源事件 (如订单 / 工单)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_survey_invitation", schema = "scrm",
        uniqueConstraints = @UniqueConstraint(name = "uk_survey_invitation_code", columnNames = "invitation_code"),
        indexes = {
                @Index(name = "idx_survey_invitation_survey", columnList = "survey_id"),
                @Index(name = "idx_survey_invitation_customer", columnList = "customer_id"),
                @Index(name = "idx_survey_invitation_status", columnList = "status"),
                @Index(name = "idx_survey_invitation_channel", columnList = "channel"),
                @Index(name = "idx_survey_invitation_sent", columnList = "sent_at"),
                @Index(name = "idx_survey_invitation_expired", columnList = "expired_at")
        })
@Data
public class ScrmSurveyInvitationEntity {

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

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (冗余, 便于列表展示, 可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 分发渠道: IN_APP / SMS / EMAIL / WECHAT */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    /** 联系方式 (可空, 手机号 / 邮箱 / openid 等) */
    @Column(name = "contact_info", length = 200)
    private String contactInfo;

    /** 邀请码 (唯一, 客户凭此提交回答) */
    @Column(name = "invitation_code", nullable = false, length = 100)
    private String invitationCode;

    /** 状态: PENDING / SENT / OPENED / IN_PROGRESS / COMPLETED / EXPIRED / BOUNCED (默认 PENDING) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 发送时间 (可空) */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /** 打开时间 (可空) */
    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    /** 完成时间 (可空) */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 过期时间 (可空) */
    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    /** 提醒次数 (默认 0) */
    @Column(name = "reminder_count")
    private Integer reminderCount;

    /** 最近提醒时间 (可空) */
    @Column(name = "last_reminder_at")
    private LocalDateTime lastReminderAt;

    /** 触发来源事件 (可空, 如 PURCHASE / SERVICE_TICKET) */
    @Column(name = "source_event", length = 100)
    private String sourceEvent;

    /** 触发来源 ID (可空, 如订单号 / 工单号) */
    @Column(name = "source_id", length = 100)
    private String sourceId;
}
