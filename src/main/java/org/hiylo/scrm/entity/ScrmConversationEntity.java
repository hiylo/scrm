/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConversationEntity.java
 * Date : 2026/07/27 02:41:22
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
 * SCRM 会话实体。
 * <p>
 * 会话是账号与客户之间的对话载体，区分单聊与群聊，
 * 记录最后消息时间与摘要用于会话列表排序与预览。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_conversation", schema = "scrm", indexes = {
        @Index(name = "idx_conversation_account", columnList = "account_id"),
        @Index(name = "idx_conversation_customer", columnList = "customer_id"),
        @Index(name = "idx_conversation_platform_id", columnList = "platform_conversation_id"),
        @Index(name = "idx_conversation_last_msg", columnList = "last_message_at")
})
@Data
public class ScrmConversationEntity {

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
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
        if (unreadCount == null) {
            unreadCount = 0L;
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

    /** 平台类型 */
    @Column(name = "platform_type", nullable = false, length = 30)
    private String platformType;

    /** 账号 ID（引用 scrm_account.id） */
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** 客户 ID（引用 scrm_customer.id） */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** 会话类型：SINGLE / GROUP */
    @Column(name = "conversation_type", nullable = false, length = 20)
    private String conversationType;

    /** 平台会话 ID */
    @Column(name = "platform_conversation_id", length = 200)
    private String platformConversationId;

    /** 最后消息时间 */
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    /** 最后消息摘要 */
    @Column(name = "last_message_summary", length = 500)
    private String lastMessageSummary;

    /** 会话状态：ACTIVE / CLOSED / PENDING */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 未读消息数 */
    @Column(name = "unread_count", nullable = false)
    private Long unreadCount;
}
