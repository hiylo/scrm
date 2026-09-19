/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetUsageEntity.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 营销素材使用记录实体。
 * <p>
 * 记录素材在系统中的使用情况: {@code usageType} 区分使用类型
 * (VIEW/DOWNLOAD/USE/SHARE/FAVORITE/LIKE/EMBED/EXPORT), {@code usageModule} /
 * {@code usageEntity} / {@code usageScenario} 描述使用来源, {@code metadata} (TEXT)
 * 存储 JSON 附加数据, 用于使用统计、热门素材挖掘与使用趋势分析。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_asset_usage", schema = "scrm", indexes = {
        @Index(name = "idx_asset_usage_asset", columnList = "asset_id"),
        @Index(name = "idx_asset_usage_type", columnList = "usage_type"),
        @Index(name = "idx_asset_usage_module", columnList = "usage_module"),
        @Index(name = "idx_asset_usage_user", columnList = "user_id"),
        @Index(name = "idx_asset_usage_used_at", columnList = "used_at")
})
@Data
public class ScrmAssetUsageEntity {

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

    /** 素材 ID */
    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    /** 使用类型: VIEW/DOWNLOAD/USE/SHARE/FAVORITE/LIKE/EMBED/EXPORT */
    @Column(name = "usage_type", nullable = false, length = 20)
    private String usageType;

    /** 使用模块 (可空, 如 campaign / mass_send / quick_reply) */
    @Column(name = "usage_module", length = 100)
    private String usageModule;

    /** 使用实体 (可空, 如 campaign_id) */
    @Column(name = "usage_entity", length = 200)
    private String usageEntity;

    /** 使用实体名称 (可空) */
    @Column(name = "usage_entity_name", length = 200)
    private String usageEntityName;

    /** 使用场景 (可空) */
    @Column(name = "usage_scenario", length = 200)
    private String usageScenario;

    /** 用户 ID */
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    /** 用户名称 (可空) */
    @Column(name = "user_name", length = 100)
    private String userName;

    /** 用户角色 (可空) */
    @Column(name = "user_role", length = 50)
    private String userRole;

    /** 使用次数 (默认 1) */
    @Column(name = "usage_count")
    private Integer usageCount;

    /** JSON 附加数据 (TEXT, 可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /** 使用时间 */
    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;
}
