/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPersonaEntity.java
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
 * SCRM 人设业务字段实体。
 * <p>
 * 人设 ID 与 scrm-server 执行侧共享，业务侧维护人设的展示信息、
 * 话术风格标签、话术模板、自定义标签等。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_persona", schema = "scrm", indexes = {
        @Index(name = "idx_persona_persona_id", columnList = "persona_id", unique = true),
        @Index(name = "idx_persona_account_id", columnList = "account_id")
})
@Data
public class ScrmPersonaEntity {

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

    /** 人设 ID（与 scrm-server 执行侧共享，业务唯一） */
    @Column(name = "persona_id", nullable = false, unique = true, length = 100)
    private String personaId;

    /** 归属账号 ID（引用 scrm_account.id） */
    @Column(name = "account_id")
    private Long accountId;

    /** 昵称 */
    @Column(name = "nickname", length = 200)
    private String nickname;

    /** 头像 URL */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /** 性别：MALE / FEMALE / UNKNOWN */
    @Column(name = "gender", length = 20)
    private String gender;

    /** 年龄段：如 18-24 / 25-30 等 */
    @Column(name = "age_range", length = 30)
    private String ageRange;

    /** 地区 */
    @Column(name = "region", length = 100)
    private String region;

    /** 个性签名 */
    @Column(name = "signature", length = 500)
    private String signature;

    /** 话术风格标签（JSON 数组） */
    @Column(name = "style_tags", columnDefinition = "TEXT")
    private String styleTags;

    /** 话术模板 ID（JSON 数组） */
    @Column(name = "script_template_ids", columnDefinition = "TEXT")
    private String scriptTemplateIds;

    /** 自定义标签（JSON 数组） */
    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;
}
