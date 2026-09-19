/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmArchiveRuleEntity.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 归档规则实体。
 * <p>
 * 定义自动归档的触发条件与过滤规则, 支持按平台类型、账号、消息方向、
 * 消息类型、关键词与风险等级进行过滤。规则按 priority 降序匹配,
 * 命中任一规则即执行归档。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_archive_rule", schema = "scrm", indexes = {
        @Index(name = "idx_archive_rule_enabled", columnList = "enabled")
})
@Data
public class ScrmArchiveRuleEntity {

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

    /** 规则名称 */
    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    /** 平台类型过滤 (null 表示全部) */
    @Column(name = "platform_type", length = 30)
    private String platformType;

    /** 归属账号 ID 过滤 (null 表示全部) */
    @Column(name = "account_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 消息方向过滤: INBOUND / OUTBOUND (null 表示全部) */
    @Column(name = "direction", length = 10)
    private String direction;

    /** 消息类型过滤 (逗号分隔): TEXT,IMAGE,VIDEO */
    @Column(name = "message_types", length = 200)
    private String messageTypes;

    /** 关键词过滤 (逗号分隔), 命中任一即归档 */
    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywords;

    /** 风险等级过滤: LOW / MEDIUM / HIGH (null 表示全部) */
    @Column(name = "risk_level_filter", length = 20)
    private String riskLevelFilter;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 优先级 (数字越大越先匹配) */
    @Column(name = "priority", nullable = false)
    private Integer priority;
}
