/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCareRuleEntity.java
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
 * SCRM 客户关怀规则实体。
 * <p>
 * 定义客户关怀触发规则, 由 {@code ScrmCustomerCareService.generateDailyTasks} 在每日任务
 * 生成时扫描评估。{@link #careType} 标注关怀类型 (BIRTHDAY/FESTIVAL/ANNIVERSARY/
 * MEMBERSHIP_EXPIRY/INACTIVITY_REMINDER/CUSTOM), {@link #triggerCondition} (JSON:
 * {daysBefore, time, segment}) 描述触发条件, {@link #actionType} 触发关怀动作,
 * {@link #actionContent} (JSON: {messageTemplateId, couponTemplateId, giftId}) 描述动作内容。
 * </p>
 * <p>
 * {@link #applicableSegments} / {@link #applicableLevels} 限定适用客群与等级;
 * {@link #priority} 越小越优先; {@link #executionCount} / {@link #lastExecutedAt} 记录执行统计。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_care_rule", schema = "scrm", indexes = {
        @Index(name = "idx_care_rule_care_type", columnList = "care_type"),
        @Index(name = "idx_care_rule_enabled", columnList = "enabled"),
        @Index(name = "idx_care_rule_priority", columnList = "priority")
})
@Data
public class ScrmCareRuleEntity {

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

    /** 关怀类型: BIRTHDAY / FESTIVAL / ANNIVERSARY / MEMBERSHIP_EXPIRY / INACTIVITY_REMINDER / CUSTOM */
    @Column(name = "care_type", nullable = false, length = 30)
    private String careType;

    /** 规则描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 触发条件 JSON: {daysBefore, time, segment} */
    @Column(name = "trigger_condition", nullable = false, columnDefinition = "TEXT")
    private String triggerCondition;

    /** 关怀动作: SEND_MESSAGE / SEND_COUPON / SEND_GIFT / CALL / CREATE_TASK / NOTIFY_ASSIGNEE */
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    /** 动作内容 JSON: {messageTemplateId, couponTemplateId, giftId} */
    @Column(name = "action_content", nullable = false, columnDefinition = "TEXT")
    private String actionContent;

    /** 优先级 (数字越小越优先, 默认 0) */
    @Column(name = "priority")
    private Integer priority;

    /** 适用客群 (可空, 逗号分隔) */
    @Column(name = "applicable_segments", length = 500)
    private String applicableSegments;

    /** 适用等级 (可空, 逗号分隔) */
    @Column(name = "applicable_levels", length = 200)
    private String applicableLevels;

    /** 是否启用 (默认 true) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 执行次数 (累计) */
    @Column(name = "execution_count")
    private Integer executionCount;

    /** 最近执行时间 */
    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
