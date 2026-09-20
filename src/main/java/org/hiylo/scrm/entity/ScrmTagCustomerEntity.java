/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagCustomerEntity.java
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
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 客户标签关联实体 (客户-标签映射)。
 * <p>
 * 记录客户 ({@link ScrmCustomerEntity}) 与标签 ({@link ScrmTagEntity}) 的关联关系,
 * 支持标签值 (tag_value, 用于有值标签)、标签来源 (tag_source: MANUAL 手动 / AUTO 自动 /
 * IMPORT 导入 / COMPUTED 计算)、打标人 (assigned_by / assignedByName)、过期时间
 * (expires_at, 到期自动失效)、置信度 (confidence, 0-1, 自动打标可能携带) 与备注 (note)。
 * </p>
 * <p>
 * 同一客户同一标签默认仅保留一条记录, 重复打标按 tag_value 覆盖更新。is_auto=true 表示
 * 由规则引擎自动打标, 不允许手动去标 (需先禁用关联规则)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_tag_customer", schema = "scrm", indexes = {
        @Index(name = "idx_tag_customer_customer", columnList = "customer_id"),
        @Index(name = "idx_tag_customer_tag", columnList = "tag_id"),
        @Index(name = "idx_tag_customer_unique", columnList = "customer_id,tag_id", unique = true),
        @Index(name = "idx_tag_customer_source", columnList = "tag_source"),
        @Index(name = "idx_tag_customer_expires", columnList = "expires_at")
})
@Data
public class ScrmTagCustomerEntity {

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

    /** 客户 ID (引用 scrm_customer.id) */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** 标签 ID (引用 scrm_tag.id) */
    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    /** 标签值 (用于有值标签, 可空) */
    @Column(name = "tag_value", length = 500)
    private String tagValue;

    /** 标签来源: MANUAL 手动 / AUTO 自动 / IMPORT 导入 / COMPUTED 计算 */
    @Column(name = "tag_source", nullable = false, length = 20)
    private String tagSource;

    /** 打标人 ID (可空) */
    @Column(name = "assigned_by", length = 100)
    private String assignedBy;

    /** 打标人名称 (可空, 冗余字段便于展示) */
    @Column(name = "assigned_by_name", length = 100)
    private String assignedByName;

    /** 打标时间 */
    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    /** 过期时间 (可空, 到期后视为失效) */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** 置信度 (0-1, 自动打标可能携带, 默认 1.0) */
    @Column(name = "confidence")
    private Double confidence;

    /** 备注 (可空) */
    @Column(name = "note", length = 500)
    private String note;

    /** 是否自动打标 (true 表示由规则引擎打标, 不允许手动去标) */
    @Column(name = "is_auto", nullable = false)
    private Boolean isAuto;
}
