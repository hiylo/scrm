/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWeWorkArchiveConfigEntity.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import org.hiylo.scrm.config.EncryptedStringConverter;

import java.time.LocalDateTime;

/**
 * 企微会话存档配置实体。
 * <p>
 * 存储企业微信会话存档所需的连接信息, 包括企业 corpid、会话存档 secret 与 RSA 私钥。
 * secret 与 privateKey 通过 {@link EncryptedStringConverter} 加密落库, 避免明文泄露。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_wework_archive_config", schema = "scrm", indexes = {
        @Index(name = "idx_wework_archive_config_corp", columnList = "corp_id"),
        @Index(name = "idx_wework_archive_config_status", columnList = "status")
})
@Data
public class ScrmWeWorkArchiveConfigEntity {

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

    /** 配置名称 */
    @Column(name = "config_name", nullable = false, length = 200)
    private String configName;

    /** 企业 corpid */
    @Column(name = "corp_id", nullable = false, length = 100)
    private String corpId;

    /** 应用 agentid（可空） */
    @Column(name = "agent_id", length = 100)
    private String agentId;

    /** 会话存档 secret（加密存储） */
    @Column(name = "secret", nullable = false, length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String secret;

    /** RSA 私钥 PEM（加密存储） */
    @Column(name = "private_key", nullable = false, length = 2000)
    @Convert(converter = EncryptedStringConverter.class)
    private String privateKey;

    /** 企微会话存档 SDK 路径（可空） */
    @Column(name = "sdk_lib_path", length = 500)
    private String sdkLibPath;

    /** 最后拉取的 seq（可空） */
    @Column(name = "last_seq")
    private Long lastSeq;

    /** 最后拉取时间（可空） */
    @Column(name = "last_fetch_at")
    private LocalDateTime lastFetchAt;

    /** 状态: ACTIVE(启用) / INACTIVE(停用) / ERROR(异常) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 错误信息（可空） */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
