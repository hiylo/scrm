/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContentChannelEntity.java
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
 * SCRM 内容分发渠道实体。
 * <p>
 * 描述内容在单一渠道 (WECHAT_OFFICIAL/WECHAT_MOMENTS/DOUYIN/KUAISHOU/XIAOHONGSHU/
 * BILIBILI/WEIBO/WEBSITE/EMAIL/SMS) 下的发布状态与互动指标。{@link #channelPostId} /
 * {@link #channelPostUrl} 记录渠道侧帖子标识, 各类 *Count 字段记录该渠道下的浏览/点赞/
 * 分享/评论/转化数据, 供效果对比使用。
 * </p>
 * <p>
 * 发布状态流转: PENDING (待发布) → PUBLISHING (发布中) → PUBLISHED (已发布) /
 * FAILED (发布失败) / REMOVED (已下架)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_content_channel", schema = "scrm", indexes = {
        @Index(name = "idx_content_channel_content", columnList = "content_id"),
        @Index(name = "idx_content_channel_channel", columnList = "channel"),
        @Index(name = "idx_content_channel_status", columnList = "status")
})
@Data
public class ScrmContentChannelEntity {

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

    /** 内容 ID */
    @Column(name = "content_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contentId;

    /* * 渠道: WECHAT_OFFICIAL / WECHAT_MOMENTS / DOUYIN / KUAISHOU / XIAOHONGSHU / BILIBILI / WEIBO / WEBSITE / EMAIL /
    /* SMS */
    @Column(name = "channel", nullable = false, length = 30)
    private String channel;

    /** 渠道帖子 ID (可空) */
    @Column(name = "channel_post_id", length = 200)
    private String channelPostId;

    /** 渠道帖子 URL (可空) */
    @Column(name = "channel_post_url", length = 500)
    private String channelPostUrl;

    /** 发布状态: PENDING / PUBLISHING / PUBLISHED / FAILED / REMOVED (默认 PENDING) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 发布时间 (可空) */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 浏览数 (默认 0) */
    @Column(name = "view_count")
    private Integer viewCount;

    /** 点赞数 (默认 0) */
    @Column(name = "like_count")
    private Integer likeCount;

    /** 分享数 (默认 0) */
    @Column(name = "share_count")
    private Integer shareCount;

    /** 评论数 (默认 0) */
    @Column(name = "comment_count")
    private Integer commentCount;

    /** 转化数 (默认 0) */
    @Column(name = "conversion_count")
    private Integer conversionCount;

    /** 错误信息 (可空) */
    @Column(name = "error_message", length = 500)
    private String errorMessage;
}
