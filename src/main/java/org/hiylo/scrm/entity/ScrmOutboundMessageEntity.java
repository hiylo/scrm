/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOutboundMessageEntity.java
 * Date : 2026/10/07 18:00:00
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
 * SCRM 出站消息队列实体。
 * <p>
 * 当 scrm 决策回复客户（自动回复 / 人工坐席下发 / 营销触达）且目标账号平台为
 * 无官方 API 的社交平台（如个人微信 wechat_personal）时，回复内容会写入本表
 * 形成待发送队列。执行侧（wx-console / wechat-agent 等）轮询拉取本队列、调
 * 用本地 UIA / 协议库发送，发送结果通过 {@code /scrm/callback/outbound/ack}
 * 回写状态。
 * </p>
 * <p>
 * 状态机：PENDING（待发送）→ IN_PROGRESS（执行侧取走，发送中）→ SENT（成功）/
 * FAILED（失败，errorMessage 写原因）。IN_PROGRESS 超过
 * {@code scrm.outbound.in-progress-timeout-seconds}（默认 120s）后由扫描任务
 * 退回 PENDING 重新入队。SENT / FAILED 为终态，不再被拉取。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_outbound_message", schema = "scrm", indexes = {
        @Index(name = "idx_outbound_account", columnList = "account_id"),
        @Index(name = "idx_outbound_status", columnList = "status"),
        @Index(name = "idx_outbound_conversation", columnList = "conversation_id"),
        @Index(name = "idx_outbound_message", columnList = "message_id")
})
@Data
public class ScrmOutboundMessageEntity {

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

    /** 乐观锁版本号（并发更新保护，执行侧 ack 时用 version 乐观锁防覆盖） */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 持久化前回调: 自动填充创建/更新时间与版本号初值。
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
     * 更新前回调: 自动刷新更新时间。
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // ==================== 业务字段 ====================

    /** 关联的会话消息 ID（scrm_conversation_message.id），用于回查消息正文与发送时间 */
    @Column(name = "message_id")
    private Long messageId;

    /** 业务消息 ID（scrm_conversation_message.message_id，UUID/雪花字符串），冗余存以便日志与排查 */
    @Column(name = "business_message_id", length = 100)
    private String businessMessageId;

    /** 会话 ID（scrm_conversation.id） */
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /** 账号 ID（scrm_account.id，即个人微信账号在 scrm 的雪花 ID） */
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** 平台类型（wework / wechat_personal 等，用于执行侧过滤自己关心的平台） */
    @Column(name = "platform_type", nullable = false, length = 30)
    private String platformType;

    /**
     * 目标平台会话标识（个人微信场景为 wxid 或 xxx@chatroom）。
     * 执行侧据此调用 UIA 打开会话发送。
     */
    @Column(name = "target_platform_id", nullable = false, length = 200)
    private String targetPlatformId;

    /** 消息类型（TEXT / IMAGE / LINK 等，目前仅 TEXT 走队列下发） */
    @Column(name = "message_type", nullable = false, length = 20)
    private String messageType;

    /** 发送内容（文本正文；媒体消息为空） */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** 媒体对象存储 key（IMAGE / FILE 消息，对象已通过上传接口存入对象存储） */
    @Column(name = "media_object_key", length = 500)
    private String mediaObjectKey;

    /** 媒体原始文件名（下发指令时供执行侧落盘命名，IMAGE / FILE 消息） */
    @Column(name = "media_file_name", length = 255)
    private String mediaFileName;

    /** 来源（AUTO_REPLY=自动回复触发，MANUAL=人工坐席下发，CAMPAIGN=营销触达） */
    @Column(name = "source", nullable = false, length = 20)
    private String source;

    /**
     * 处理状态：PENDING（待发送）/ IN_PROGRESS（执行侧取走，发送中）/
     * SENT（成功）/ FAILED（失败，errorMessage 写原因）。
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 重试次数（执行侧 ack FAILED 时累加，超过 max_retries 进入 FAILED 终态） */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    /** 最大重试次数（默认 3，由配置或创建时设定） */
    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    /** 进入 IN_PROGRESS 的时间（用于超时回退 PENDING） */
    @Column(name = "in_progress_at")
    private LocalDateTime inProgressAt;

    /** 发送成功/失败回执时间 */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 失败原因（ack FAILED 时由执行侧回填） */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** 排序号（取走时按 createdAt 升序，确保先入先发） */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;
}
