/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionRuleEntity.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 销售佣金规则实体。
 * <p>
 * 隶属于佣金方案, 定义具体规则类型 (FLAT_RATE/TIERED_RATE/BONUS/MULTIPLIER/DEDUCTION)、
 * 匹配条件 (JSON: [{field,operator,value}], 字段如 product_category/order_amount/team/customer_type)、
 * 佣金比例、固定佣金、阶梯配置 (JSON: [{minValue,maxValue,rate,bonusAmount}])、奖金、倍数、扣减、
 * 最低订单金额、每单最大佣金、优先级、启用状态, 并跟踪匹配次数与累计计算佣金。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_commission_rule", schema = "scrm", indexes = {
        @Index(name = "idx_commission_rule_plan", columnList = "plan_id"),
        @Index(name = "idx_commission_rule_type", columnList = "rule_type"),
        @Index(name = "idx_commission_rule_enabled", columnList = "enabled")
})
@Data
public class ScrmCommissionRuleEntity {

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
        if (commissionRate == null) {
            commissionRate = 0d;
        }
        if (commissionAmount == null) {
            commissionAmount = 0d;
        }
        if (bonusAmount == null) {
            bonusAmount = 0d;
        }
        if (multiplier == null) {
            multiplier = 1.0d;
        }
        if (deductionAmount == null) {
            deductionAmount = 0d;
        }
        if (minOrderAmount == null) {
            minOrderAmount = 0d;
        }
        if (maxCommissionPerOrder == null) {
            maxCommissionPerOrder = 0d;
        }
        if (priority == null) {
            priority = 0;
        }
        if (enabled == null) {
            enabled = true;
        }
        if (matchCount == null) {
            matchCount = 0;
        }
        if (totalCommissionCalculated == null) {
            totalCommissionCalculated = 0d;
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

    /** 所属方案 ID */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** 规则名称 */
    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    /** 规则类型: FLAT_RATE/TIERED_RATE/BONUS/MULTIPLIER/DEDUCTION */
    @Column(name = "rule_type", nullable = false, length = 30)
    private String ruleType;

    /** 匹配条件 JSON (可空): [{field,operator,value}], 字段如 product_category/order_amount/team/customer_type */
    @Column(name = "conditions", columnDefinition = "TEXT")
    private String conditions;

    /** 佣金比例 (0-1, FLAT_RATE/TIERED_RATE 使用) */
    @Column(name = "commission_rate")
    private Double commissionRate;

    /** 固定佣金金额 */
    @Column(name = "commission_amount")
    private Double commissionAmount;

    /** 阶梯配置 JSON (可空): [{minValue,maxValue,rate,bonusAmount}] */
    @Column(name = "tier_config", columnDefinition = "TEXT")
    private String tierConfig;

    /** 奖金金额 */
    @Column(name = "bonus_amount")
    private Double bonusAmount;

    /** 倍数 (默认 1.0) */
    @Column(name = "multiplier")
    private Double multiplier;

    /** 扣减金额 */
    @Column(name = "deduction_amount")
    private Double deductionAmount;

    /** 最低订单金额 (订单金额低于此值不计算佣金) */
    @Column(name = "min_order_amount")
    private Double minOrderAmount;

    /** 每单最大佣金 (0 表示无限) */
    @Column(name = "max_commission_per_order")
    private Double maxCommissionPerOrder;

    /** 优先级 (数值越大优先级越高) */
    @Column(name = "priority")
    private Integer priority;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 匹配次数 */
    @Column(name = "match_count")
    private Integer matchCount;

    /** 累计计算佣金 */
    @Column(name = "total_commission_calculated")
    private Double totalCommissionCalculated;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
