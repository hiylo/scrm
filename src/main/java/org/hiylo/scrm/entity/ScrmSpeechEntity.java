/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechEntity.java
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
 * SCRM 话术条目实体。
 * <p>
 * 团队共享话术库条目, 支持 TEXT/IMAGE/VIDEO/FILE/LINK/MIXED 多种类型, 通过 {@code mediaUrls}
 * (JSON 数组) 携带媒体 URL。话术按 {@code sortOrder} 升序展示, {@code useCount} /
 * {@code likeCount} 用于使用统计, {@code status=DRAFT} 表示草稿不出现在选用列表中。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_speech", schema = "scrm", indexes = {
        @Index(name = "idx_speech_category", columnList = "category_id"),
        @Index(name = "idx_speech_platform", columnList = "platform_type"),
        @Index(name = "idx_speech_scenario", columnList = "scenario"),
        @Index(name = "idx_speech_status", columnList = "status")
})
@Data
public class ScrmSpeechEntity {

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

    /** 分类 ID（可空, 空表示未分类） */
    @Column(name = "category_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    /** 话术标题 */
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    /** 话术内容 */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 话术类型: TEXT / IMAGE / VIDEO / FILE / LINK / MIXED（默认 TEXT） */
    @Column(name = "speech_type", length = 20, nullable = false)
    private String speechType;

    /** 媒体 URL JSON 数组字符串, 如 ["url1", "url2"] */
    @Column(name = "media_urls", columnDefinition = "TEXT")
    private String mediaUrls;

    /** 适用平台类型（可空, 空表示全部, 如 wechat / douyin / xhs） */
    @Column(name = "platform_type", length = 30)
    private String platformType;

    /** 使用场景: GREETING / FOLLOW_UP / REJECTION / HOLIDAY / AFTER_SALE / ETC */
    @Column(name = "scenario", length = 50)
    private String scenario;

    /** 标签（逗号分隔） */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 排序值（数字越小越靠前, 默认 0） */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 使用次数 */
    @Column(name = "use_count")
    private Integer useCount;

    /** 点赞数 */
    @Column(name = "like_count")
    private Integer likeCount;

    /** 状态: ACTIVE / INACTIVE / DRAFT（默认 ACTIVE） */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    /** 创建人（可空） */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
