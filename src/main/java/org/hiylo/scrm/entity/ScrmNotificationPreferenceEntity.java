/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationPreferenceEntity.java
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
 * SCRM 通知偏好实体。
 * <p>
 * 描述用户对指定渠道 (IN_APP/EMAIL/SMS/PUSH) 与分类 (SYSTEM/MARKETING/SERVICE/ALERT/
 * REMINDER/VERIFICATION) 通知的接收偏好: 是否启用, 免打扰时段 (quietHoursStart / quietHoursEnd,
 * HH:mm), 最低优先级阈值 (低于该优先级的通知不发送)。{@code ScrmNotificationCenterService.
 * checkPreference} 综合上述字段判定是否允许发送。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_notification_preference", schema = "scrm", indexes = {
        @Index(name = "idx_notification_preference_user", columnList = "user_id"),
        @Index(name = "idx_notification_preference_ucc", columnList = "user_id,channel,category")
})
@Data
public class ScrmNotificationPreferenceEntity {

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
     * 持久化前回调: 自动填充创建/更新时间与版本号初值, 同步刷新偏好更新时间
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (version == null) {
            version = 0L;
        }
    }

    /**
     * 更新前回调: 自动刷新更新时间与偏好更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        LocalDateTime now = LocalDateTime.now();
        updateTime = now;
        updatedAt = now;
    }

    // ==================== 业务字段 ====================

    /** 用户 ID */
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    /** 通知渠道: IN_APP/EMAIL/SMS/PUSH */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    /** 分类: SYSTEM/MARKETING/SERVICE/ALERT/REMINDER/VERIFICATION */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    /** 是否启用 (默认 true) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 免打扰开始时间 HH:mm (可空) */
    @Column(name = "quiet_hours_start", length = 10)
    private String quietHoursStart;

    /** 免打扰结束时间 HH:mm (可空) */
    @Column(name = "quiet_hours_end", length = 10)
    private String quietHoursEnd;

    /** 最低优先级阈值 (默认 0, 低于该优先级不发送) */
    @Column(name = "min_priority")
    private Integer minPriority;

    /** 偏好更新时间 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
