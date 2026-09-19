/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoTagRuleLogEntity.java
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
 * SCRM 客户自动标签规则执行日志实体。
 * <p>
 * 记录自动标签规则每次评估命中的执行轨迹, 包括命中的规则 ID、客户 ID、触发事件、
 * 匹配的条件详情、动作类型、执行结果 (SUCCESS/FAILED/SKIPPED) 与执行详情,
 * 用于规则效果追踪、失败排查与统计聚合。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_auto_tag_rule_log", schema = "scrm", indexes = {
        @Index(name = "idx_auto_tag_rule_log_rule", columnList = "rule_id"),
        @Index(name = "idx_auto_tag_rule_log_customer", columnList = "customer_id"),
        @Index(name = "idx_auto_tag_rule_log_executed", columnList = "executed_at")
})
@Data
public class ScrmAutoTagRuleLogEntity {

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

    /** 规则 ID (引用 scrm_auto_tag_rule.id) */
    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    /** 客户 ID (引用 scrm_customer.id) */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** 客户昵称 (执行时快照, 避免后续客户改名导致日志失真) */
    @Column(name = "customer_nickname", length = 200)
    private String customerNickname;

/** 触发事件: CUSTOMER_CREATED / CUSTOMER_UPDATED / MESSAGE_RECEIVED / LIFECYCLE_CHANGED / TAG_ADDED /
         * INTERACTION_TIMEOUT */
    @Column(name = "trigger_event", length = 50, nullable = false)
    private String triggerEvent;

    /** 匹配的条件详情 JSON (可空, 命中后写入) */
    @Column(name = "matched_conditions", columnDefinition = "TEXT")
    private String matchedConditions;

    /** 动作类型: ADD_TAG / REMOVE_TAG / SET_LIFECYCLE / NOTIFY */
    @Column(name = "action_type", length = 20, nullable = false)
    private String actionType;

    /** 动作结果: SUCCESS / FAILED / SKIPPED */
    @Column(name = "action_result", length = 20, nullable = false)
    private String actionResult;

    /** 动作详情 (执行结果描述, 失败时记录错误信息) */
    @Column(name = "action_detail", length = 500)
    private String actionDetail;

    /** 执行时间 */
    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;
}
