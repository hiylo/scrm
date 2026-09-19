/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCampaignChannelEntity.java
 * Date : 2026/08/04 08:40:58
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

import java.time.LocalDateTime;

/**
 * SCRM 营销活动渠道实体。
 * <p>
 * 描述活动在单一渠道 (WECHAT/WORK_WECHAT/SMS/EMAIL/DOUYIN/KUAISHOU/XIAOHONGSHU/BILIBILI) 下的
 * 内容配置与发送指标。{@link #channelConfig} (JSON) 承载渠道差异化配置, {@link #contentBody} /
 * {@link #contentImage} / {@link #linkUrl} 为发送内容, 各类 *Count 字段记录发送漏斗:
 * targetCount → sentCount → deliveredCount → readCount → clickCount → convertCount。
 * </p>
 * <p>
 * 状态流转: PENDING (待发送) → SENDING (发送中) → SENT (已发送) / FAILED (发送失败)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_marketing_campaign_channel", schema = "scrm", indexes = {
        @Index(name = "idx_mkt_channel_campaign", columnList = "campaign_id"),
        @Index(name = "idx_mkt_channel_channel", columnList = "channel"),
        @Index(name = "idx_mkt_channel_status", columnList = "status")
})
@Data
public class ScrmMarketingCampaignChannelEntity {

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

    /** 活动 ID */
    @Column(name = "campaign_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long campaignId;

    /** 渠道: WECHAT / WORK_WECHAT / SMS / EMAIL / DOUYIN / KUAISHOU / XIAOHONGSHU / BILIBILI */
    @Column(name = "channel", nullable = false, length = 30)
    private String channel;

    /** 渠道配置 JSON (可空) */
    @Column(name = "channel_config", columnDefinition = "TEXT")
    private String channelConfig;

    /** 内容标题 (可空) */
    @Column(name = "content_title", length = 200)
    private String contentTitle;

    /** 内容正文 (可空) */
    @Column(name = "content_body", columnDefinition = "TEXT")
    private String contentBody;

    /** 内容图片 URL (可空) */
    @Column(name = "content_image", length = 500)
    private String contentImage;

    /** 跳转链接 (可空) */
    @Column(name = "link_url", length = 500)
    private String linkUrl;

    /** 计划发送时间 (可空) */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    /** 实际发送时间 (可空) */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /** 目标人数 (默认 0) */
    @Column(name = "target_count")
    private Integer targetCount;

    /** 已发送数 (默认 0) */
    @Column(name = "sent_count")
    private Integer sentCount;

    /** 已送达数 (默认 0) */
    @Column(name = "delivered_count")
    private Integer deliveredCount;

    /** 已读数 (默认 0) */
    @Column(name = "read_count")
    private Integer readCount;

    /** 点击数 (默认 0) */
    @Column(name = "click_count")
    private Integer clickCount;

    /** 转化数 (默认 0) */
    @Column(name = "convert_count")
    private Integer convertCount;

    /** 渠道花费 (默认 0) */
    @Column(name = "cost")
    private Double cost;

    /** 状态: PENDING / SENDING / SENT / FAILED (默认 PENDING) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;
}
