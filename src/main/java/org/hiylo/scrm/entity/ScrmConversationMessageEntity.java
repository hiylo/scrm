/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConversationMessageEntity.java
 * Date : 2026/07/27 02:41:22
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
 * SCRM 会话消息实体。
 * <p>
 * 记录会话内的每条消息（文本 / 图片 / 语音 / 视频 / 文件 / 链接 / 系统），
 * 文本消息存 {@link #content}，媒体消息存对象存储 key 到 {@link #mediaObjectKey}。
 * <p>
 * 对应数据库表 scrm_conversation_message 按 sent_at 月分区（PG declarative partitioning）。
 * 由于分区表唯一约束必须包含分区键 sent_at，message_id 的唯一性由应用层保证
 * （UUID / 雪花 ID），数据库侧仅建立普通索引以加速查询。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_conversation_message", schema = "scrm", indexes = {
        @Index(name = "idx_message_conversation", columnList = "conversation_id"),
        @Index(name = "idx_message_sent_at", columnList = "sent_at"),
        @Index(name = "idx_message_message_id", columnList = "message_id"),
        @Index(name = "idx_message_platform_message_id", columnList = "platform_message_id")
})
@Data
public class ScrmConversationMessageEntity {

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

    /** 消息 ID（业务唯一，由应用层保证全局唯一） */
    @Column(name = "message_id", nullable = false, length = 100)
    private String messageId;

    /** 会话 ID（引用 scrm_conversation.id） */
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /** 消息类型：TEXT / IMAGE / VOICE / VIDEO / FILE / LINK / SYSTEM */
    @Column(name = "message_type", nullable = false, length = 20)
    private String messageType;

    /** 消息方向：IN / OUT */
    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    /** 文本内容（媒体消息为空） */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** 媒体对象 key（对象存储 objectKey，文本消息为空） */
    @Column(name = "media_object_key", length = 500)
    private String mediaObjectKey;

    /** 媒体大小（字节） */
    @Column(name = "media_size")
    private Long mediaSize;

    /** 平台消息 ID */
    @Column(name = "platform_message_id", length = 200)
    private String platformMessageId;

    /** 发送时间（分区键） */
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
