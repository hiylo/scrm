/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataDictionaryUsageEntity.java
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
 * SCRM 数据字典使用记录实体。
 * <p>
 * 记录字典/字典项在系统中的引用情况, 包括使用模块 / 实体 / 字段 / 场景 / 累计次数 /
 * 首次与最近使用时间, 用于使用统计、热门字典项挖掘与未使用字典项清理。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_data_dictionary_usage", schema = "scrm", indexes = {
        @Index(name = "idx_data_dictionary_usage_dict", columnList = "dict_id"),
        @Index(name = "idx_data_dictionary_usage_code", columnList = "dict_code"),
        @Index(name = "idx_data_dictionary_usage_item", columnList = "item_id"),
        @Index(name = "idx_data_dictionary_usage_module", columnList = "usage_module"),
        @Index(name = "idx_data_dictionary_usage_last", columnList = "last_used_at")
})
@Data
public class ScrmDataDictionaryUsageEntity {

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

    /** 字典 ID */
    @Column(name = "dict_id", nullable = false)
    private Long dictId;

    /** 字典编码 */
    @Column(name = "dict_code", nullable = false, length = 50)
    private String dictCode;

    /** 字典项 ID (可空, 字典级使用统计时为空) */
    @Column(name = "item_id")
    private Long itemId;

    /** 字典项值 (可空) */
    @Column(name = "item_value", length = 500)
    private String itemValue;

    /** 使用模块 (如 customer / order / ticket) */
    @Column(name = "usage_module", nullable = false, length = 100)
    private String usageModule;

    /** 使用实体 (可空, 如 ScrmCustomerEntity) */
    @Column(name = "usage_entity", length = 200)
    private String usageEntity;

    /** 使用字段 (可空, 如 customer_level) */
    @Column(name = "usage_field", length = 200)
    private String usageField;

    /** 使用场景 (可空, 如 FORM / REPORT / FILTER) */
    @Column(name = "usage_scenario", length = 200)
    private String usageScenario;

    /** 使用次数 (默认 0) */
    @Column(name = "usage_count")
    private Integer usageCount;

    /** 最近使用时间 (可空) */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** 首次使用时间 */
    @Column(name = "first_used_at", nullable = false)
    private LocalDateTime firstUsedAt;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;
}
