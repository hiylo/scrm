/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateEntity.java
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
 * SCRM 消息模板实体（快捷回复）。
 * <p>
 * 运营人员维护可复用的消息模板, 发送消息时按分类与平台类型选用, 并通过
 * {@code ScrmMessageTemplateService.renderTemplate} 完成变量插值 (如
 * {@code {{nickname}}} / {@code {{platformType}}} / {@code {{customerName}}} /
 * {@code {{ownerName}}})。模板按 sortOrder 升序展示 (数字越小越靠前), enabled=false
 * 的模板不出现在选用列表中。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_message_template", schema = "scrm", indexes = {
        @Index(name = "idx_message_template_category", columnList = "category"),
        @Index(name = "idx_message_template_platform", columnList = "platform_type"),
        @Index(name = "idx_message_template_enabled", columnList = "enabled")
})
@Data
public class ScrmMessageTemplateEntity {

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

    /** 分类: greeting（问候）/ promotion（促销）/ service（服务）/ follow_up（跟进）/ apology（致歉） */
    @Column(name = "category", length = 50, nullable = false)
    private String category;

    /** 模板内容, 支持变量插值 {{nickname}} / {{platformType}} / {{customerName}} / {{ownerName}} */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 适用平台类型（可空, 空表示通用, 如 wechat / douyin / xhs） */
    @Column(name = "platform_type", length = 30)
    private String platformType;

    /** 变量列表 JSON 数组字符串, 如 ["nickname", "platformType"], 由 content 自动提取 */
    @Column(name = "variables", columnDefinition = "TEXT")
    private String variables;

    /** 是否启用（默认 true） */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 排序值（默认 0, 数字越小越靠前） */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 创建人（可空） */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
