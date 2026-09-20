/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommunityMessageEntity.java
 * Date : 2026/07/27 02:41:22
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
 * SCRM 社群消息实体。
 * <p>
 * 归档社群内每条消息: 发送者 (成员/管理员/群主/机器人/系统), 消息类型 (文本/图片/文件/链接/
 * 视频/语音), 内容与媒体 URL, 是否回复及回复目标, 情感分析结果, 是否归档。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_community_message", schema = "scrm", indexes = {
        @Index(name = "idx_community_msg_community", columnList = "community_id"),
        @Index(name = "idx_community_msg_sender_type", columnList = "sender_type"),
        @Index(name = "idx_community_msg_type", columnList = "message_type"),
        @Index(name = "idx_community_msg_sent_at", columnList = "community_id,sent_at")
})
@Data
public class ScrmCommunityMessageEntity {

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

    /** 社群 ID (引用 scrm_community.id) */
    @Column(name = "community_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long communityId;

    /** 发送者 ID (可空) */
    @Column(name = "sender_id", length = 200)
    private String senderId;

    /** 发送者名称 (可空) */
    @Column(name = "sender_name", length = 200)
    private String senderName;

    /** 发送者类型: MEMBER/ADMIN/OWNER/BOT/SYSTEM (默认 MEMBER) */
    @Column(name = "sender_type", nullable = false, length = 20)
    private String senderType;

    /** 消息类型: TEXT/IMAGE/FILE/LINK/VIDEO/VOICE (默认 TEXT) */
    @Column(name = "message_type", nullable = false, length = 20)
    private String messageType;

    /** 消息内容 (可空) */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** 媒体 URL (可空) */
    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    /** 发送时间 */
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    /** 是否回复 (默认 false) */
    @Column(name = "is_reply", nullable = false)
    private Boolean isReply;

    /** 回复目标消息 ID (可空) */
    @Column(name = "reply_to_message_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long replyToMessageId;

    /** 情感 (可空: POSITIVE/NEUTRAL/NEGATIVE) */
    @Column(name = "sentiment", length = 20)
    private String sentiment;

    /** 是否归档 (默认 false) */
    @Column(name = "archived", nullable = false)
    private Boolean archived;
}
