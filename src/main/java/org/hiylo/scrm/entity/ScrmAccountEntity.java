/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAccountEntity.java
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
 * SCRM 平台账号实体。
 * <p>
 * 描述一个 SCRM 自动化运营账号（企微 / 抖音 / 快手 / 小红书 / B站 / 微信个人号），
 * 记录归属用户、设备与人设，以及登录态及最后登录时间。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_account", schema = "scrm", indexes = {
        @Index(name = "idx_account_platform_uid", columnList = "platform_type,platform_account_uid"),
        @Index(name = "idx_account_device_id", columnList = "device_id"),
        @Index(name = "idx_account_persona_id", columnList = "persona_id")
})
@Data
public class ScrmAccountEntity {

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

    /** 乐观锁版本号（并发更新保护，后写入者触发 OptimisticLockException） */
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

    /** 平台类型：WEWORK / DOUYIN / KUAISHOU / XIAOHONGSHU / BILIBILI / WECHAT_PERSONAL */
    @Column(name = "platform_type", nullable = false, length = 30)
    private String platformType;

    /** 归属用户 ID（关联 scrm_user.id，数据隔离按此过滤；可空表示历史/系统账号） */
    @Column(name = "owner_user_id")
    private Long ownerUserId;

    /** 平台内部账号唯一标识 */
    @Column(name = "platform_account_uid", nullable = false, length = 200)
    private String platformAccountUid;

    /** 账号名称 (如微信号 / 手机号 / 登录名, 区别于展示昵称) */
    @Column(name = "account_name", length = 200)
    private String accountName;

    /** 账号展示名称 */
    @Column(name = "display_name", length = 200)
    private String displayName;

    /** 账号头像 URL */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /** 关联设备 ID */
    @Column(name = "device_id", length = 100)
    private String deviceId;

    /** 关联人设 ID（可空） */
    @Column(name = "persona_id", length = 100)
    private String personaId;

    /** 登录态：LOGIN / LOGOUT / FROZEN / UNKNOWN */
    @Column(name = "login_state", nullable = false, length = 20)
    private String loginState;

    /** 最后登录时间 */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
