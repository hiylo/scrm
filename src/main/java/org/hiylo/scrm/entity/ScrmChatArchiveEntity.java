/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChatArchiveEntity.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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
 * SCRM 会话存档实体。
 * <p>
 * 记录全量消息归档, 用于合规审计、质量分析与风险标记。
 * 支持自动归档 (由归档规则触发) 与手动归档两种来源。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_chat_archive", schema = "scrm", indexes = {
        @Index(name = "idx_chat_archive_account", columnList = "account_id"),
        @Index(name = "idx_chat_archive_customer", columnList = "customer_id"),
        @Index(name = "idx_chat_archive_conversation", columnList = "conversation_id"),
        @Index(name = "idx_chat_archive_sent_at", columnList = "sent_at"),
        @Index(name = "idx_chat_archive_archived_at", columnList = "archived_at"),
        @Index(name = "idx_chat_archive_quality", columnList = "quality_flag"),
        @Index(name = "idx_chat_archive_sent", columnList = "sent_at")
})
@Data
public class ScrmChatArchiveEntity {

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

    /** 乐观锁版本号（并发更新保护，后写入者触发 OptimisticLockException） */
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

    /** 归属账号 ID（引用 scrm_account.id） */
    @Column(name = "account_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 客户 ID（引用 scrm_customer.id, 可空） */
    @Column(name = "customer_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 会话 ID（引用 scrm_conversation.id, 可空） */
    @Column(name = "conversation_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    /** 平台类型 */
    @Column(name = "platform_type", nullable = false, length = 30)
    private String platformType;

    /** 消息方向: INBOUND(接收) / OUTBOUND(发送) */
    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    /** 消息类型: TEXT / IMAGE / VIDEO / VOICE / FILE / LINK / SYSTEM */
    @Column(name = "message_type", nullable = false, length = 20)
    private String messageType;

    /** 消息内容 (文本内容或描述) */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** 原始消息内容 (完整 JSON / XML 报文, 用于溯源) */
    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    /** 媒体文件 URL */
    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    /** 消息发送时间 */
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    /** 归档时间 */
    @Column(name = "archived_at", nullable = false)
    private LocalDateTime archivedAt;

    /** 质量标记: NORMAL(正常) / SENSITIVE(敏感) / VIOLATION(违规) */
    @Column(name = "quality_flag", length = 20)
    private String qualityFlag;

    /** 风险等级: LOW / MEDIUM / HIGH */
    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    /** 归档来源: AUTO(自动) / MANUAL(手动) */
    @Column(name = "archive_source", nullable = false, length = 20)
    private String archiveSource;
}
