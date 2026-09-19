/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExternalContactSyncConfigEntity.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 外部联系人同步配置实体。
 * <p>
 * 描述企业微信 / 抖音 / 快手 / 小红书等平台外部联系人同步的配置项, 包括企业凭证、
 * 同步模式 (增量 / 全量)、同步方向、同步频率、字段映射等。{@link #secret} 为加密存储的应用密钥,
 * {@link #fieldMapping} (JSON) 描述外部字段到 SCRM 字段的映射规则。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_external_contact_sync_config", schema = "scrm", indexes = {
        @Index(name = "idx_ecs_config_platform", columnList = "platform"),
        @Index(name = "idx_ecs_config_enabled", columnList = "enabled"),
        @Index(name = "idx_ecs_config_corp", columnList = "corp_id")
})
@Data
public class ScrmExternalContactSyncConfigEntity {

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

    /** 配置名称 */
    @Column(name = "config_name", nullable = false, length = 200)
    private String configName;

    /** 平台: WORK_WECHAT / DOUYIN / KUAISHOU / XIAOHONGSHU / OTHER */
    @Column(name = "platform", nullable = false, length = 30)
    private String platform;

    /** 企业 ID (可空) */
    @Column(name = "corp_id", length = 200)
    private String corpId;

    /** 应用 ID (可空) */
    @Column(name = "agent_id", length = 100)
    private String agentId;

    /** 应用密钥 (加密存储, 可空) */
    @Column(name = "secret", length = 500)
    private String secret;

    /** 同步模式: INCREMENTAL / FULL */
    @Column(name = "sync_mode", nullable = false, length = 20)
    private String syncMode;

    /** 同步方向: ONE_WAY_IN / ONE_WAY_OUT / BIDIRECTIONAL */
    @Column(name = "sync_direction", nullable = false, length = 20)
    private String syncDirection;

    /** 同步频率: REALTIME / HOURLY / DAILY / WEEKLY / MANUAL */
    @Column(name = "sync_frequency", nullable = false, length = 20)
    private String syncFrequency;

    /** 最后同步时间 (可空) */
    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    /** 最后同步状态 (可空): SUCCESS / FAILED / PARTIAL */
    @Column(name = "last_sync_status", length = 20)
    private String lastSyncStatus;

    /** 最后同步数量 */
    @Column(name = "last_sync_count")
    private Integer lastSyncCount;

    /** 是否自动创建客户 (默认 true) */
    @Column(name = "auto_create_customer", nullable = false)
    private Boolean autoCreateCustomer;

    /** 是否自动合并重复联系人 (默认 false) */
    @Column(name = "auto_merge_duplicate", nullable = false)
    private Boolean autoMergeDuplicate;

    /** JSON 字段映射配置 (可空) */
    @Column(name = "field_mapping", columnDefinition = "TEXT")
    private String fieldMapping;

    /** 是否启用 (默认 true) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
