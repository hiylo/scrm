/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBlacklistEntity.java
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
 * SCRM 黑名单风控实体。
 * <p>
 * 记录黑名单 / 灰名单 / 白名单 / 观察名单条目, 涵盖目标值 / 风险等级 / 风险评分 / 来源 /
 * 生效与到期 / 申诉 / 审核等信息。{@link #targetType} 与 {@link #targetValue} 联合定位
 * 一个风控目标, {@link #status} 标识当前条目状态 (ACTIVE / EXPIRED / REMOVED / APPEALED / RESTORED)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_blacklist", schema = "scrm", indexes = {
        @Index(name = "idx_blacklist_target", columnList = "target_type,target_value"),
        @Index(name = "idx_blacklist_list_type", columnList = "list_type"),
        @Index(name = "idx_blacklist_status", columnList = "status"),
        @Index(name = "idx_blacklist_risk_level", columnList = "risk_level"),
        @Index(name = "idx_blacklist_customer", columnList = "customer_id"),
        @Index(name = "idx_blacklist_expiry", columnList = "expiry_date")
})
@Data
public class ScrmBlacklistEntity {

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

    /** 名单类型: BLACKLIST / GRAYLIST / WHITELIST / WATCHLIST */
    @Column(name = "list_type", nullable = false, length = 20)
    private String listType;

    /** 目标类型: CUSTOMER / PHONE / EMAIL / IP / DEVICE / ID_CARD / BANK_CARD / ADDRESS / WECHAT_ID / COMPANY */
    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    /** 目标值 */
    @Column(name = "target_value", nullable = false, length = 500)
    private String targetValue;

    /** 目标名称 (可空) */
    @Column(name = "target_name", length = 200)
    private String targetName;

    /** 关联客户 ID (可空) */
    @Column(name = "customer_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 加入原因 */
    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    /** 风险等级: LOW / MEDIUM / HIGH / CRITICAL (默认 MEDIUM) */
    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    /** 风险评分 0-100 (默认 0) */
    @Column(name = "risk_score")
    private Double riskScore;

    /** 风险标签 (逗号分隔, 可空) */
    @Column(name = "risk_tags", length = 500)
    private String riskTags;

    /** 来源: MANUAL / AUTO / RULE / EXTERNAL / REPORT / SYSTEM */
    @Column(name = "source", nullable = false, length = 100)
    private String source;

    /** 来源详情 (可空) */
    @Column(name = "source_detail", length = 500)
    private String sourceDetail;

    /** 证据描述 (可空) */
    @Column(name = "evidence", length = 2000)
    private String evidence;

    /** 关联风险事件 ID (可空) */
    @Column(name = "related_event_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long relatedEventId;

    /** 生效日期 */
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /** 到期日期 (可空, null 表示永久) */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** 是否永久 (默认 FALSE) */
    @Column(name = "is_permanent", nullable = false)
    private Boolean isPermanent;

    /** 状态: ACTIVE / EXPIRED / REMOVED / APPEALED / RESTORED (默认 ACTIVE) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 添加人 */
    @Column(name = "added_by", nullable = false, length = 100)
    private String addedBy;

    /** 添加时间 */
    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    /** 审批人 (可空) */
    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    /** 审批时间 (可空) */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 移除人 (可空) */
    @Column(name = "removed_by", length = 100)
    private String removedBy;

    /** 移除时间 (可空) */
    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    /** 移除原因 (可空) */
    @Column(name = "remove_reason", length = 500)
    private String removeReason;

    /** 申诉状态 (可空): PENDING / UNDER_REVIEW / APPROVED / REJECTED */
    @Column(name = "appeal_status", length = 20)
    private String appealStatus;

    /** 申诉原因 (可空) */
    @Column(name = "appeal_reason", length = 1000)
    private String appealReason;

    /** 申诉时间 (可空) */
    @Column(name = "appealed_at")
    private LocalDateTime appealedAt;

    /** 申诉审核人 (可空) */
    @Column(name = "appeal_reviewed_by", length = 100)
    private String appealReviewedBy;

    /** 申诉审核时间 (可空) */
    @Column(name = "appeal_reviewed_at")
    private LocalDateTime appealReviewedAt;

    /** 申诉结果 (可空) */
    @Column(name = "appeal_result", length = 500)
    private String appealResult;

    /** 审核次数 (默认 0) */
    @Column(name = "review_count")
    private Integer reviewCount;

    /** 最近审核时间 (可空) */
    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    /** 下次复审日期 (可空) */
    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    /** 告警次数 (默认 0) */
    @Column(name = "alert_count")
    private Integer alertCount;

    /** 最近告警时间 (可空) */
    @Column(name = "last_alert_at")
    private LocalDateTime lastAlertAt;

    /** JSON 附加数据 (可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /** 备注 (可空) */
    @Column(name = "notes", length = 1000)
    private String notes;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
