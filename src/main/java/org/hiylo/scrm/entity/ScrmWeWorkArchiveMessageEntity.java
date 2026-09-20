/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWeWorkArchiveMessageEntity.java
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
 * 企微会话存档消息实体。
 * <p>
 * 记录从企业微信会话存档 API 拉取并解密后的会话消息, 支持合规审计、
 * 会话回溯与内容检索。原始加密内容可保留用于溯源。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_wework_archive_message", schema = "scrm", indexes = {
        @Index(name = "idx_wework_archive_msg_config", columnList = "config_id"),
        @Index(name = "idx_wework_archive_msg_seq", columnList = "seq"),
        @Index(name = "idx_wework_archive_msg_msgid", columnList = "msg_id"),
        @Index(name = "idx_wework_archive_msg_from", columnList = "from_user"),
        @Index(name = "idx_wework_archive_msg_room", columnList = "room_id"),
        @Index(name = "idx_wework_archive_msg_type", columnList = "msg_type"),
        @Index(name = "idx_wework_archive_msg_sent_at", columnList = "sent_at"),
        @Index(name = "idx_wework_archive_msg_sent", columnList = "sent_at")
})
@Data
public class ScrmWeWorkArchiveMessageEntity {

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

    /** 存档配置 ID（引用 scrm_wework_archive_config.id） */
    @Column(name = "config_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long configId;

    /** 企微消息 seq */
    @Column(name = "seq", nullable = false)
    private Long seq;

    /** 消息 ID */
    @Column(name = "msg_id", nullable = false, length = 200)
    private String msgId;

    /** 动作: send(发送) / recall(撤回) */
    @Column(name = "action", nullable = false, length = 20)
    private String action;

    /** 发送者 */
    @Column(name = "from_user", nullable = false, length = 200)
    private String fromUser;

    /** 接收者列表（逗号分隔） */
    @Column(name = "to_list", nullable = false, length = 1000)
    private String toList;

    /** 群 ID（可空） */
    @Column(name = "room_id", length = 200)
    private String roomId;

    /*
     * 消息类型:
     * text/image/voice/video/file/emoji/revoke/agree/disagree/card/location/link/weapp/chatrecord/
     * todo/vote/collect/redpacket/meeting_voice_call/voip_doc_share/external_redpacket
     */
    @Column(name = "msg_type", nullable = false, length = 30)
    private String msgType;

    /** 解密后的消息内容 JSON */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** 原始加密内容（可空） */
    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    /** 媒体文件 URL（可空） */
    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    /** 文件名（可空） */
    @Column(name = "file_name", length = 200)
    private String fileName;

    /** 文件大小（可空） */
    @Column(name = "file_size")
    private Long fileSize;

    /** 消息发送时间 */
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    /** 入库时间 */
    @Column(name = "archived_at", nullable = false)
    private LocalDateTime archivedAt;

    /** 是否已处理 */
    @Column(name = "processed", nullable = false)
    private Boolean processed = Boolean.FALSE;
}
