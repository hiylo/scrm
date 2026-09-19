/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmUserEntity.java
 * Date : 2026/09/17 00:00:00
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
 * SCRM 平台登录用户实体。
 * <p>
 * 自建 JWT 认证体系下的账号凭证记录: 用户名唯一, 口令存 BCrypt 密文,
 * 角色以逗号分隔字符串存储 (如 {@code ADMIN,OPERATOR}), 供登录签发令牌与权限校验使用。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_user", schema = "scrm", indexes = {
        @Index(name = "uk_user_username", columnList = "username", unique = true),
        @Index(name = "idx_user_status", columnList = "status")
})
@Data
public class ScrmUserEntity {

    // ==================== 公共字段 ====================

    /** 主键 ID (Snowflake 雪花算法生成, 不自增) */
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

    /** 乐观锁版本号 (并发更新保护, 后写入者触发 OptimisticLockException) */
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

    /** 登录用户名 (全局唯一, 索引约束) */
    @Column(name = "username", nullable = false, length = 64)
    private String username;

    /** 口令 BCrypt 密文 (不落库明文, 也不在任何日志中输出) */
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    /** 展示昵称 (可空, 为空时前端回退展示 username) */
    @Column(name = "display_name", length = 100)
    private String displayName;

    /** 邮箱 (可空, 用于找回密码与通知触达) */
    @Column(name = "email", length = 200)
    private String email;

    /** 角色列表 (逗号分隔, 如 {@code ADMIN,OPERATOR}) */
    @Column(name = "roles", nullable = false, length = 200)
    private String roles;

    /** 状态: 1=启用, 0=禁用 (禁用后不可登录) */
    @Column(name = "status", nullable = false)
    private Integer status;

    /** 最后登录时间 */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
