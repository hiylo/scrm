/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFollowUpTemplateEntity.java
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
 * SCRM 跟进模板实体。
 * <p>
 * 按场景 (NEW_CUSTOMER/DORMANT_REACTIVATE/AFTER_SALE/BIRTHDAY/MEMBERSHIP_RENEWAL)
 * 预置标题/内容/优先级/提醒分钟数。应用模板 (applyTemplate) 即基于模板生成一条跟进任务。
 * useCount 跟踪模板累计应用次数, 用于热度统计。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_follow_up_template", schema = "scrm", indexes = {
        @Index(name = "idx_follow_up_template_task_type", columnList = "task_type"),
        @Index(name = "idx_follow_up_template_scenario", columnList = "scenario"),
        @Index(name = "idx_follow_up_template_enabled", columnList = "enabled")
})
@Data
public class ScrmFollowUpTemplateEntity {

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

    /** 跟进类型: CALL/MESSAGE/VISIT/EMAIL/MEETING/OTHER */
    @Column(name = "task_type", nullable = false, length = 30)
    private String taskType;

    /** 标题模板 */
    @Column(name = "title_template", nullable = false, length = 200)
    private String titleTemplate;

    /** 内容模板 */
    @Column(name = "content_template", columnDefinition = "TEXT")
    private String contentTemplate;

    /** 默认优先级: HIGH/MEDIUM/LOW */
    @Column(name = "default_priority", length = 10)
    private String defaultPriority;

    /** 默认提醒分钟数 */
    @Column(name = "default_reminder_minutes")
    private Integer defaultReminderMinutes;

    /** 平台类型（可空） */
    @Column(name = "platform_type", length = 30)
    private String platformType;

    /** 场景: NEW_CUSTOMER/DORMANT_REACTIVATE/AFTER_SALE/BIRTHDAY/MEMBERSHIP_RENEWAL */
    @Column(name = "scenario", length = 50)
    private String scenario;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 累计应用次数 */
    @Column(name = "use_count")
    private Integer useCount;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
