/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyLogEntity.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.hibernate.annotations.GenericGenerator;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 消息自动回复日志实体。
 * <p>
 * 记录自动回复规则每次命中的执行轨迹, 包括命中的规则 ID、客户 ID、渠道、收到的消息、
 * 匹配关键词、匹配分数、回复内容、发送状态 (SENT/FAILED/SKIPPED/QUEUED) 与响应耗时,
 * 用于回复效果追踪、失败排查与统计聚合。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_auto_reply_log", schema = "scrm", indexes = {
        @Index(name = "idx_auto_reply_log_rule", columnList = "rule_id"),
        @Index(name = "idx_auto_reply_log_customer", columnList = "customer_id"),
        @Index(name = "idx_auto_reply_log_sent", columnList = "sent_at"),
        @Index(name = "idx_auto_reply_log_status", columnList = "status")
})
@Data
public class ScrmAutoReplyLogEntity {

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

    /** 规则 ID (引用 scrm_auto_reply_rule.id, 兜底回复可为空) */
    @Column(name = "rule_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;

    /** 规则名称 (执行时快照) */
    @Column(name = "rule_name", length = 200)
    private String ruleName;

    /** 规则类型: KEYWORD/WELCOME/TIMEOUT/OFFLINE/FORM/EVENT/CONDITIONAL */
    @Column(name = "rule_type", length = 30, nullable = false)
    private String ruleType;

    /** 客户 ID (引用 scrm_customer.id) */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (执行时快照) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 账号 ID (可空) */
    @Column(name = "account_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 渠道: WECHAT/WORK_WECHAT/WEB/APP/SMS/EMAIL (可空) */
    @Column(name = "channel", length = 30)
    private String channel;

    /** 收到的消息 (可空) */
    @Column(name = "incoming_message", columnDefinition = "TEXT")
    private String incomingMessage;

    /** 匹配的关键词 (可空) */
    @Column(name = "matched_keyword", length = 200)
    private String matchedKeyword;

    /** 匹配分数 (0-1, FUZZY 匹配时为相似度) */
    @Column(name = "match_score")
    private Double matchScore;

    /** 回复类型: TEXT/IMAGE/LINK/FILE/TEMPLATE/HTML/RICH_TEXT */
    @Column(name = "reply_type", length = 20, nullable = false)
    private String replyType;

    /** 回复内容 (可空, 失败时可能为空) */
    @Column(name = "reply_content", columnDefinition = "TEXT")
    private String replyContent;

    /** 发送时间 */
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    /** 响应耗时 (毫秒, 可空) */
    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    /** 发送状态: SENT/FAILED/SKIPPED/QUEUED（默认 SENT） */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    /** 错误信息 (失败时记录, 可空) */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** 是否兜底回复（默认 FALSE） */
    @Column(name = "is_fallback", nullable = false)
    private Boolean isFallback;

    /** 会话 ID (可空, 关联会话上下文) */
    @Column(name = "session_id", length = 200)
    private String sessionId;

    /** JSON 附加数据 (可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
