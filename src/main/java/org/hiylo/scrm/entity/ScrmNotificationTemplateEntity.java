/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationTemplateEntity.java
 * Date : 2026/07/27 02:41:22
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
 * SCRM 通知模板实体。
 * <p>
 * 描述一个通知模板的元信息: 模板编码 (唯一), 分类 (SYSTEM/MARKETING/SERVICE/ALERT/
 * REMINDER/VERIFICATION), 渠道 (IN_APP/EMAIL/SMS/PUSH/WEBHOOK), 标题与内容模板 (支持变量占位符
 * 如 {customerName} / {amount}), 可用变量列表, 发送者信息与短信签名。content 为 TEXT 字段,
 * 渲染时由 {@code ScrmNotificationCenterService.renderTemplate} 进行变量替换。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_notification_template", schema = "scrm", indexes = {
        @Index(name = "idx_notification_template_channel", columnList = "channel"),
        @Index(name = "idx_notification_template_category", columnList = "category"),
        @Index(name = "idx_notification_template_enabled", columnList = "enabled"),
        @Index(name = "idx_notification_template_code", columnList = "template_code", unique = true)
})
@Data
public class ScrmNotificationTemplateEntity {

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
    @Column(name = "template_name", nullable = false, length = 200)
    private String templateName;

    /** 模板编码 (唯一) */
    @Column(name = "template_code", nullable = false, length = 100)
    private String templateCode;

    /** 分类: SYSTEM/MARKETING/SERVICE/ALERT/REMINDER/VERIFICATION */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    /** 通知渠道: IN_APP/EMAIL/SMS/PUSH/WEBHOOK */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    /** 标题模板 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 内容模板 (支持变量: {customerName} / {amount}) */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 可用变量 (逗号分隔, 可空) */
    @Column(name = "variables", length = 500)
    private String variables;

    /** 发送者名称 (可空) */
    @Column(name = "sender_name", length = 100)
    private String senderName;

    /** 邮件发送者 (可空) */
    @Column(name = "sender_email", length = 200)
    private String senderEmail;

    /** 短信签名 (可空) */
    @Column(name = "sms_sign_name", length = 100)
    private String smsSignName;

    /** 是否 HTML 内容 (默认 false) */
    @Column(name = "is_html", nullable = false)
    private Boolean isHtml;

    /** 是否启用 (默认 true) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 使用次数 (默认 0) */
    @Column(name = "usage_count")
    private Integer usageCount;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
