/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChurnRuleEntity.java
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
 * SCRM 客户流失预警规则实体。
 * <p>
 * 定义客户流失风险识别规则, 由 {@code ScrmChurnWarningService.scanCustomer} 在客户扫描时
 * 评估。规则按 {@link #riskLevel} (HIGH/MEDIUM/LOW) 标注风险等级, 通过 {@link #conditions}
 * (JSON 数组: [{field, operator, value}]) 描述触发条件, {@link #conditionType} (ALL/ANY)
 * 控制条件间逻辑关系。命中后通过 {@link #actionType} 触发预警动作。
 * </p>
 * <p>
 * conditions 中 field 支持: lastInteractionDays (最近互动距今天数) /
 * noInteractionDays (无互动天数) / totalInteractions (累计互动次数) /
 * lifecycle (生命周期) / customerDays (入客天数) / orderFrequency (消费频率);
 * operator 支持: gt / lt / eq / between。
 * </p>
 * <p>
 * {@link #cooldownDays} 控制同一客户两次预警的最小间隔天数, 避免重复打扰;
 * {@link #priority} 越小越优先; {@link #matchCount} / {@link #lastMatchAt} 记录命中统计。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_churn_rule", schema = "scrm", indexes = {
        @Index(name = "idx_churn_rule_risk_level", columnList = "risk_level"),
        @Index(name = "idx_churn_rule_enabled", columnList = "enabled"),
        @Index(name = "idx_churn_rule_priority", columnList = "priority")
})
@Data
public class ScrmChurnRuleEntity {

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

    /** 规则描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 风险等级: HIGH / MEDIUM / LOW */
    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    /** 条件类型: ALL 所有条件满足 / ANY 任一满足 (默认 ALL) */
    @Column(name = "condition_type", nullable = false, length = 20)
    private String conditionType;

    /** 条件 JSON 数组: [{field, operator, value}] */
    @Column(name = "conditions", nullable = false, columnDefinition = "TEXT")
    private String conditions;

    /** 预警动作: NOTIFY_ASSIGNEE / CREATE_FOLLOW_UP / TRIGGER_MASS_SEND / ADD_TAG / CHANGE_LIFECYCLE / WEBHOOK */
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    /** 动作参数 (JSON 字符串) */
    @Column(name = "action_params", nullable = false, columnDefinition = "TEXT")
    private String actionParams;

    /** 同一客户冷却天数 (默认 7) */
    @Column(name = "cooldown_days")
    private Integer cooldownDays;

    /** 优先级 (数字越小越优先, 默认 0) */
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
