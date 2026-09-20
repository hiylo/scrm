/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateCenterEntity.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 消息模板中心模板定义实体。
 * <p>
 * 统一消息模板的核心定义, 支持多渠道适配 (微信/企微/短信/邮件/APP推送/WebSocket/抖音/快手)、
 * 变量占位符 {@code {{variable}}}、模板分组归属、多版本管理、审批流程 (草稿→待审核→已发布)、
 * 使用统计 (使用次数/成功率/回复率) 与标准模板标记。{@code templateCode} 全局唯一。
 * </p>
 * <p>
 * 注意: {@code version} 为 @Version 乐观锁字段 (公共字段), {@code versionNumber} 为模板内容
 * 业务版本号 (每次 createVersion 自增), 二者语义不同。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_message_template_center", schema = "scrm", indexes = {
        @Index(name = "idx_message_template_center_code", columnList = "template_code"),
        @Index(name = "idx_message_template_center_group", columnList = "group_id"),
        @Index(name = "idx_message_template_center_status", columnList = "status"),
        @Index(name = "idx_message_template_center_type", columnList = "template_type"),
        @Index(name = "idx_message_template_center_review", columnList = "review_status"),
        @Index(name = "idx_message_template_center_standard", columnList = "is_standard"),
        @Index(name = "idx_message_template_center_usage", columnList = "usage_count")
})
@Data
public class ScrmMessageTemplateCenterEntity {

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

    /** 模板编码（全局唯一, 业务侧稳定引用） */
    @Column(name = "template_code", length = 50, nullable = false)
    private String templateCode;

    /** 所属分组 ID（可空） */
    @Column(name = "group_id")
    private Long groupId;

    /** 所属分组名称（冗余字段, 便于列表展示） */
    @Column(name = "group_name", length = 200)
    private String groupName;

    /** 模板描述（可空） */
    @Column(name = "description", length = 500)
    private String description;

    /** 模板类型: TEXT/RICH_TEXT/HTML/CARD/IMAGE_TEXT/VIDEO_TEXT/PROGRAM/JSON */
    @Column(name = "template_type", length = 30, nullable = false)
    private String templateType;

    /** 适用渠道（逗号分隔: WECHAT/WORK_WECHAT/SMS/EMAIL/APP_PUSH/WEB_SOCKET/DOUYIN/KUAISHOU） */
    @Column(name = "channels", length = 500, nullable = false)
    private String channels;

    /** 邮件主题/推送标题（可空） */
    @Column(name = "subject", length = 500)
    private String subject;

    /** 模板内容, 支持变量占位符 {{variable}} */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 纯文本内容（可空, 用于短信等纯文本渠道） */
    @Column(name = "plain_content", length = 2000)
    private String plainContent;

    /** HTML 内容（可空, 用于邮件等富文本渠道） */
    @Column(name = "html_content", columnDefinition = "TEXT")
    private String htmlContent;

    /** 企微链接（可空） */
    @Column(name = "wechat_link", length = 500)
    private String wechatLink;

    /** 小程序路径（可空） */
    @Column(name = "mini_program_path", length = 500)
    private String miniProgramPath;

    /** 变量定义 JSON: [{name,type,defaultValue,description,required}]（可空） */
    @Column(name = "variables", columnDefinition = "TEXT")
    private String variables;

    /** 附件列表 JSON（可空） */
    @Column(name = "attachments", length = 1000)
    private String attachments;

    /** 模板分类（可空） */
    @Column(name = "category", length = 100)
    private String category;

    /** 标签（逗号分隔, 可空） */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 适用场景（逗号分隔, 可空） */
    @Column(name = "applicable_scenarios", length = 500)
    private String applicableScenarios;

    /** 语言（默认 zh_CN） */
    @Column(name = "language", length = 20, nullable = false)
    private String language;

    /** 状态: DRAFT/PENDING_REVIEW/APPROVED/REJECTED/PUBLISHED/ARCHIVED（默认 DRAFT） */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    /** 模板内容业务版本号（默认 1, 每次 createVersion 自增） */
    @Column(name = "version_number")
    private Integer versionNumber;

    /** 当前版本 ID（指向 scrm_message_template_version, 可空） */
    @Column(name = "current_version_id")
    private Long currentVersionId;

    /** 审核状态: PENDING/APPROVED/REJECTED（默认 PENDING） */
    @Column(name = "review_status", length = 20, nullable = false)
    private String reviewStatus;

    /** 审核人（可空） */
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    /** 审核时间（可空） */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 审核意见（可空） */
    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    /** 使用次数（默认 0） */
    @Column(name = "usage_count")
    private Integer usageCount;

    /** 最后使用时间（可空） */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** 发送成功率（默认 0） */
    @Column(name = "success_rate")
    private Double successRate;

    /** 平均回复率（默认 0） */
    @Column(name = "avg_response_rate")
    private Double avgResponseRate;

    /** 是否标准模板（默认 false） */
    @Column(name = "is_standard", nullable = false)
    private Boolean isStandard;

    /** 创建人（可空） */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
