/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIdentityMergeRuleEntity.java
 * Date : 2026/07/27 02:41:22
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
 * SCRM 客户身份合并规则实体。
 * <p>
 * 定义重复客户检测与自动合并的策略: 匹配字段 ({@link #matchFields}) 与阈值
 * ({@link #matchThreshold}) 用于精确匹配, 模糊匹配字段 ({@link #fuzzyMatchFields})
 * 与阈值 ({@link #fuzzyMatchThreshold}) 用于相似度匹配, {@link #fieldStrategy}
 * (JSON) 定义字段冲突时的合并策略 (KEEP_TARGET/KEEP_SOURCE/MERGE/CONCAT/MAX/MIN/LATEST)。
 * {@link #autoMerge} 控制是否自动执行合并, 否则仅创建待审核任务。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_identity_merge_rule", schema = "scrm", indexes = {
        @Index(name = "idx_identity_merge_rule_enabled", columnList = "enabled"),
        @Index(name = "idx_identity_merge_rule_priority", columnList = "priority"),
        @Index(name = "idx_identity_merge_rule_name", columnList = "rule_name")
})
@Data
public class ScrmIdentityMergeRuleEntity {

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

    /** 规则名称 */
    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 匹配字段 (逗号分隔): phone,email,name,wechat_unionid */
    @Column(name = "match_fields", nullable = false, length = 500)
    private String matchFields;

    /** 匹配阈值 (0~1) */
    @Column(name = "match_threshold", nullable = false)
    private Double matchThreshold;

    /** 模糊匹配字段 (可空): name,address */
    @Column(name = "fuzzy_match_fields", length = 500)
    private String fuzzyMatchFields;

    /** 模糊匹配阈值 (0~1) */
    @Column(name = "fuzzy_match_threshold")
    private Double fuzzyMatchThreshold;

    /** 自动合并 */
    @Column(name = "auto_merge", nullable = false)
    private Boolean autoMerge;

    /** JSON 字段策略 (可空): [{field,strategy:KEEP_TARGET/KEEP_SOURCE/MERGE/CONCAT/MAX/MIN/LATEST}] */
    @Column(name = "field_strategy", columnDefinition = "TEXT")
    private String fieldStrategy;

    /** 排除字段 (可空, 逗号分隔) */
    @Column(name = "exclude_fields", length = 500)
    private String excludeFields;

    /** 优先级 (越大越优先) */
    @Column(name = "priority")
    private Integer priority;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 匹配次数 */
    @Column(name = "match_count")
    private Integer matchCount;

    /** 合并次数 */
    @Column(name = "merge_count")
    private Integer mergeCount;

    /** 最后执行时间 (可空) */
    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
