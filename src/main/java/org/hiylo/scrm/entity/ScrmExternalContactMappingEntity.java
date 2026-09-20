/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExternalContactMappingEntity.java
 * Date : 2026/08/04 08:40:58
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 外部联系人映射实体。
 * <p>
 * 维护平台外部联系人与 SCRM 客户的双向映射关系, 支持企业微信 / 抖音 / 快手 / 小红书等多平台。
 * 由 {@code ScrmExternalContactSyncService.executeSync} 在同步过程中创建 / 更新。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_external_contact_mapping", schema = "scrm",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ecs_mapping_platform_ext",
                        columnNames = {"platform", "external_contact_id"})
        },
        indexes = {
                @Index(name = "idx_ecs_mapping_platform", columnList = "platform"),
                @Index(name = "idx_ecs_mapping_customer", columnList = "customer_id"),
                @Index(name = "idx_ecs_mapping_sync_status", columnList = "sync_status"),
                @Index(name = "idx_ecs_mapping_follow_user", columnList = "follow_user_id")
        })
/**
 * SCRM 外部联系人映射实体。
 * <p>记录各平台外部联系人与内部客户档案的关联关系: 平台标识、外部联系人 ID 与
 * 外部用户 ID、对应客户 (customerId/customerName)、外部姓名/头像/企业 ID/unionId、
 * 同步状态与同步时间、跟进人 (followUserId)、备注。平台与外部联系人 ID 组合唯一。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmExternalContactMappingEntity {

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

    /** 平台: WORK_WECHAT / DOUYIN / KUAISHOU / XIAOHONGSHU / OTHER */
    @Column(name = "platform", nullable = false, length = 30)
    private String platform;

    /** 平台外部联系人 ID */
    @Column(name = "external_contact_id", nullable = false, length = 200)
    private String externalContactId;

    /** 外部用户 ID (可空) */
    @Column(name = "external_user_id", length = 200)
    private String externalUserId;

    /** SCRM 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空, 冗余便于列表展示) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 外部联系人名称 (可空) */
    @Column(name = "external_name", length = 200)
    private String externalName;

    /** 外部联系人头像 URL (可空) */
    @Column(name = "external_avatar", length = 500)
    private String externalAvatar;

    /** 外部企业 ID (可空) */
    @Column(name = "external_corp_id", length = 200)
    private String externalCorpId;

    /** 联合 ID (可空, 跨应用识别) */
    @Column(name = "union_id", length = 200)
    private String unionId;

    /** 开放平台 ID (可空) */
    @Column(name = "open_id", length = 200)
    private String openId;

    /** 跟进人 ID (可空) */
    @Column(name = "follow_user_id", length = 100)
    private String followUserId;

    /** 跟进状态 (可空): NORMAL / TRANSFERRED / LOST */
    @Column(name = "follow_status", length = 20)
    private String followStatus;

    /** 最后同步时间 */
    @Column(name = "last_sync_at", nullable = false)
    private LocalDateTime lastSyncAt;

    /** 同步状态: ACTIVE / DELETED / MERGED */
    @Column(name = "sync_status", nullable = false, length = 20)
    private String syncStatus;
}
