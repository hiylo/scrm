/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyTemplateEntity.java
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
 * SCRM 消息自动回复模板实体。
 * <p>
 * 存储可复用的回复模板, 支持变量替换: {customerName}、{nickname}、{time}。
 * 模板由 {@code ScrmAutoReplyRuleEntity.replyTemplateId} 引用, 在规则命中后由
 * {@code ScrmAutoReplyService.renderTemplate} 渲染为最终回复内容。
 * </p>
 * <p>
 * templateType 区分 TEXT/RICH_TEXT/HTML 三种模板类型, category 用于模板分类管理,
 * usageCount 累计模板被引用次数, enabled=FALSE 的模板不出现在选用列表中。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_auto_reply_template", schema = "scrm", indexes = {
        @Index(name = "idx_auto_reply_template_type", columnList = "template_type"),
        @Index(name = "idx_auto_reply_template_category", columnList = "category"),
        @Index(name = "idx_auto_reply_template_enabled", columnList = "enabled")
})
@Data
public class ScrmAutoReplyTemplateEntity {

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

    /** 模板名称 */
    @Column(name = "template_name", length = 200, nullable = false)
    private String templateName;

    /** 模板类型: TEXT/RICH_TEXT/HTML（默认 TEXT） */
    @Column(name = "template_type", length = 20, nullable = false)
    private String templateType;

    /** 模板分类（可空） */
    @Column(name = "category", length = 100)
    private String category;

    /** 模板内容 (支持变量: {customerName}/{nickname}/{time}) */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 可用变量 (逗号分隔, 可空) */
    @Column(name = "variables", length = 500)
    private String variables;

    /** 适用场景 (逗号分隔, 可空) */
    @Column(name = "applicable_scenes", length = 500)
    private String applicableScenes;

    /** 缩略图 URL（可空） */
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    /** 使用次数（默认 0） */
    @Column(name = "usage_count")
    private Integer usageCount;

    /** 是否启用（默认 TRUE） */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
