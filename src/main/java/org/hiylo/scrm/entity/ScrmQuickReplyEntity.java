/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQuickReplyEntity.java
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
 * SCRM 快捷回复条目实体。
 * <p>
 * 客服快捷回复条目, 支持 TEXT/IMAGE/LINK/MIXED 多种类型, 通过 {@code shortcut} (如 "/你好")
 * 实现侧边栏快速插入。{@code isPersonal=TRUE} 时按 {@code ownerUserId} + 账号 ID 隔离为
 * 个人专属回复; 为 FALSE 时为团队共享。{@code useCount} 用于使用频次统计, {@code status=INACTIVE}
 * 的回复不出现在选用列表中。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_quick_reply", schema = "scrm", indexes = {
        @Index(name = "idx_quick_reply_category", columnList = "category_id"),
        @Index(name = "idx_quick_reply_platform", columnList = "platform_type"),
        @Index(name = "idx_quick_reply_shortcut", columnList = "shortcut"),
        @Index(name = "idx_quick_reply_personal", columnList = "is_personal"),
        @Index(name = "idx_quick_reply_owner", columnList = "owner_user_id"),
        @Index(name = "idx_quick_reply_status", columnList = "status")
})
@Data
public class ScrmQuickReplyEntity {

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

    /** 快捷回复标题/摘要 */
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    /** 回复内容 */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 回复类型: TEXT / IMAGE / LINK / MIXED（默认 TEXT） */
    @Column(name = "reply_type", length = 20, nullable = false)
    private String replyType;

    /** 媒体 URL JSON 数组字符串, 如 ["url1", "url2"] */
    @Column(name = "media_urls", columnDefinition = "TEXT")
    private String mediaUrls;

    /** 快捷键, 如 "/你好"（可空） */
    @Column(name = "shortcut", length = 50)
    private String shortcut;

    /** 适用平台类型（可空, 空表示全部） */
    @Column(name = "platform_type", length = 30)
    private String platformType;

    /** 使用场景 */
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

    /** 是否个人专属（TRUE 时按 ownerUserId + 账号 ID 隔离） */
    @Column(name = "is_personal", nullable = false)
    private Boolean isPersonal;

    /** 个人专属时归属人用户 ID（可空） */
    @Column(name = "owner_user_id", length = 100)
    private String ownerUserId;

    /** 状态: ACTIVE / INACTIVE（默认 ACTIVE） */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    /** 创建人（可空） */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
