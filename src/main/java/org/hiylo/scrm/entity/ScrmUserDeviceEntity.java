/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmUserDeviceEntity.java
 * Date : 2026/07/29 21:19:51
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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SCRM 用户设备实体。
 * <p>
 * 描述业务员 APP (Android / iOS) 注册的推送设备, 存储个推 (GeTui) client_id,
 * 供 {@code PushNotificationService} 在任务状态变更 / 风控告警 / 账号健康等场景
 * 向业务员手机端推送通知。一个用户可注册多个设备 (如手机 + 平板), 通过
 * (user_id, client_id) 唯一约束保证不重复注册。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_user_device", schema = "scrm", indexes = {
        @Index(name = "uk_user_client", columnList = "user_id,client_id", unique = true),
        @Index(name = "idx_user_id", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScrmUserDeviceEntity {

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

    /** 用户 ID, 关联 sys_user.user_id (由网关注入 X-User-Id) */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** 个推 client_id, 推送目标标识 */
    @Column(name = "client_id", nullable = false, length = 128)
    private String clientId;

    /** 设备唯一标识（可空） */
    @Column(name = "device_id", length = 128)
    private String deviceId;

    /** 平台: android / ios（默认 android） */
    @Column(name = "platform", nullable = false, length = 16)
    private String platform;

    /** 状态: ACTIVE / INACTIVE（默认 ACTIVE） */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 最后活跃时间 */
    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;
}
