/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommunitySopEntity.java
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
 * SCRM 社群 SOP 实体。
 * <p>
 * 定义社群运营自动化动作: 触发类型 (定时/新人入群/退群/不活跃/关键词/手动) + 动作类型
 * (发消息/发欢迎语/发提醒/打标签/通知管理员)。triggerConfig 为 JSON 字符串, 含 time / days /
 * keywords / inactiveDays 等配置。communityId 为空表示对所有群生效。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_community_sop", schema = "scrm", indexes = {
        @Index(name = "idx_community_sop_community", columnList = "community_id"),
        @Index(name = "idx_community_sop_trigger", columnList = "trigger_type"),
        @Index(name = "idx_community_sop_enabled", columnList = "enabled")
})
@Data
public class ScrmCommunitySopEntity {

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

    /** SOP 名称 */
    @Column(name = "sop_name", nullable = false, length = 200)
    private String sopName;

    /** 关联社群 ID (可空, 空=所有群) */
    @Column(name = "community_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long communityId;

    /** 触发类型: TIME_BASED/MEMBER_JOIN/MEMBER_LEAVE/INACTIVE/KEYWORD/MANUAL */
    @Column(name = "trigger_type", nullable = false, length = 30)
    private String triggerType;

    /** 触发配置 (JSON: {time, days, keywords, inactiveDays}) */
    @Column(name = "trigger_config", columnDefinition = "TEXT")
    private String triggerConfig;

    /** 动作类型: SEND_MESSAGE/SEND_WELCOME/SEND_REMINDER/ADD_TAG/NOTIFY_MANAGER */
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    /** 动作内容 (消息模板) */
    @Column(name = "action_content", columnDefinition = "TEXT", nullable = false)
    private String actionContent;

    /** 延迟执行分钟 (默认 0) */
    @Column(name = "delay_minutes")
    private Integer delayMinutes;

    /** 是否启用 (默认 true) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 执行次数 (默认 0) */
    @Column(name = "execution_count")
    private Integer executionCount;

    /** 最后执行时间 (可空) */
    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
