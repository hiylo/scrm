/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateVersionEntity.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 消息模板版本实体。
 * <p>
 * 模板内容的版本快照, 每次 {@code createVersion} 时将当前模板内容固化为一条版本记录,
 * 支持版本激活 (activateVersion)、回滚 (rollbackToVersion) 与版本对比 (compareVersions)。
 * {@code versionNumber} 为模板内容的业务版本号 (1, 2, 3...), 与 @Version 乐观锁字段语义不同。
 * 同一模板下同一 {@code versionNumber} 唯一。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_message_template_version", schema = "scrm", indexes = {
        @Index(name = "idx_message_template_version_template", columnList = "template_id"),
        @Index(name = "idx_message_template_version_tpl_ver", columnList = "template_id,version_number"),
        @Index(name = "idx_message_template_version_status", columnList = "status")
})
@Data
public class ScrmMessageTemplateVersionEntity {

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
     * 持久化前回调: 自动填充创建/更新时间、版本号初值与版本创建时间
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (createdAt == null) {
            createdAt = now;
        }
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

    /** 所属模板 ID */
    @Column(name = "template_id", nullable = false)
    private Long templateId;

    /** 版本号（同一模板下递增, 1, 2, 3...） */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /** 邮件主题/推送标题（可空） */
    @Column(name = "subject", length = 500)
    private String subject;

    /** 版本内容, 支持变量占位符 {{variable}} */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 纯文本内容（可空） */
    @Column(name = "plain_content", length = 2000)
    private String plainContent;

    /** HTML 内容（可空） */
    @Column(name = "html_content", columnDefinition = "TEXT")
    private String htmlContent;

    /** 变量定义 JSON（可空） */
    @Column(name = "variables", columnDefinition = "TEXT")
    private String variables;

    /** 变更说明（可空） */
    @Column(name = "change_log", length = 500)
    private String changeLog;

    /** 状态: ACTIVE/ARCHIVED（默认 ARCHIVED, activateVersion 时置为 ACTIVE） */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    /** 从哪个版本创建（可空） */
    @Column(name = "created_from_version")
    private Integer createdFromVersion;

    /** 审批人（可空） */
    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    /** 审批时间（可空） */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 创建人（可空） */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /** 版本创建时间（业务字段, 记录该版本快照生成时刻） */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
