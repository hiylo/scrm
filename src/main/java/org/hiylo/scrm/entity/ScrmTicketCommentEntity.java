/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTicketCommentEntity.java
 * Date : 2026/07/29 21:19:51
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
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 工单评论实体。
 * <p>
 * 记录工单沟通轨迹: 客户留言 (CUSTOMER)、坐席回复 (AGENT)、内部备注 (INTERNAL) 与
 * 系统消息 (SYSTEM)。内部备注对客户不可见 (isInternal=true)。attachments 字段以 JSON
 * 数组形式存储附件列表。createdAt 为评论实际发生时间, createTime 为持久化时间。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_ticket_comment", schema = "scrm", indexes = {
        @Index(name = "idx_ticket_comment_ticket", columnList = "ticket_id"),
        @Index(name = "idx_ticket_comment_type", columnList = "comment_type"),
        @Index(name = "idx_ticket_comment_created", columnList = "created_at")
})
@Data
public class ScrmTicketCommentEntity {

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

    /** 关联工单 ID */
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** 评论类型: CUSTOMER / AGENT / INTERNAL / SYSTEM */
    @Column(name = "comment_type", nullable = false, length = 20)
    private String commentType;

    /** 作者 ID */
    @Column(name = "author_id", nullable = false, length = 100)
    private String authorId;

    /** 作者名称 (可空) */
    @Column(name = "author_name", length = 100)
    private String authorName;

    /** 评论内容 */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 附件列表 (可空, JSON 数组) */
    @Column(name = "attachments", length = 1000)
    private String attachments;

    /** 是否内部备注 */
    @Column(name = "is_internal", nullable = false)
    private Boolean isInternal;

    /** 评论发生时间 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
