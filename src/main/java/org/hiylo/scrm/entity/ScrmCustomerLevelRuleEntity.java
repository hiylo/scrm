/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLevelRuleEntity.java
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
 * SCRM 客户等级升降级规则实体。
 * <p>
 * 定义客户自动升降级规则, 由 {@code ScrmCustomerLevelService.evaluateRules} 在客户属性变更或
 * 定时批量评估时执行。规则按 {@link #ruleType} 分为 UPGRADE (升级) / DOWNGRADE (降级) 两类,
 * 命中后通过 {@link #actionType} (默认 SET_LEVEL) 将客户等级设为 {@link #targetLevelId}。
 * </p>
 * <p>
 * conditions 为 JSON 数组: {@code [{field, operator, value}]}, field 如 totalSpent (累计消费) /
 * orderCount (订单数) / registrationDays (注册天数) / lastInteractionDays (最近交互距今天数) /
 * lifecycle (生命周期); operator 如 eq/ne/gt/lt/between。
 * conditionType=ALL 表示全部条件满足, ANY 表示任一满足。
 * </p>
 * <p>
 * 评估顺序: UPGRADE 规则按 priority ASC 优先评估 (命中即升级, 不再继续降级评估),
 * 无升级命中时再评估 DOWNGRADE 规则。enabled=false 的规则跳过。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_level_rule", schema = "scrm", indexes = {
        @Index(name = "idx_customer_level_rule_target", columnList = "target_level_id"),
        @Index(name = "idx_customer_level_rule_type", columnList = "rule_type"),
        @Index(name = "idx_customer_level_rule_enabled", columnList = "enabled")
})
@Data
public class ScrmCustomerLevelRuleEntity {

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

    /** 目标等级 ID (规则命中后客户将设为该等级) */
    @Column(name = "target_level_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetLevelId;

    /** 规则类型: UPGRADE 升级 / DOWNGRADE 降级 */
    @Column(name = "rule_type", nullable = false, length = 30)
    private String ruleType;

    /** 条件类型: ALL 所有条件满足 / ANY 任一满足 (默认 ALL) */
    @Column(name = "condition_type", nullable = false, length = 20)
    private String conditionType;

    /** 条件 JSON 数组: [{field, operator, value}] */
    @Column(name = "conditions", nullable = false, columnDefinition = "TEXT")
    private String conditions;

    /** 动作类型: SET_LEVEL 设置等级 (默认, 当前仅支持 SET_LEVEL) */
    @Column(name = "action_type", nullable = false, length = 20)
    private String actionType;

    /** 优先级 (数字越小越优先, 默认 0; UPGRADE 评估按 priority ASC) */
    @Column(name = "priority")
    private Integer priority;

    /** 是否启用 (默认 true) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 匹配次数 (命中累计) */
    @Column(name = "match_count")
    private Integer matchCount;

    /** 最近匹配时间 */
    @Column(name = "last_match_at")
    private LocalDateTime lastMatchAt;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
