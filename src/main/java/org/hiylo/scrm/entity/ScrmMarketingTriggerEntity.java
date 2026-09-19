/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingTriggerEntity.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 触发式自动营销规则实体。
 * <p>
 * 描述事件驱动的自动营销规则: 定义触发事件类型与条件, 自动执行营销动作
 * (发消息 / 打标签 / 改生命周期 / 入旅程 / 通知用户 / 触发群发)。
 * 区别于手动群发任务, 这里由事件自动触发执行。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_marketing_trigger", schema = "scrm", indexes = {
        @Index(name = "idx_marketing_trigger_event_type", columnList = "event_type"),
        @Index(name = "idx_marketing_trigger_action_type", columnList = "action_type"),
        @Index(name = "idx_marketing_trigger_enabled", columnList = "enabled")
})
@Data
public class ScrmMarketingTriggerEntity {

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

    /** 触发器名称 */
    @Column(name = "trigger_name", nullable = false, length = 200)
    private String triggerName;

    /** 描述（可空） */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 事件类型: CUSTOMER_ADDED / CUSTOMER_TAGGED / LIFECYCLE_CHANGED /
     * CONVERSATION_STARTED / OPPORTUNITY_STAGE_CHANGED / MASS_SEND_COMPLETED /
     * CART_ABANDONED / INTERACTION_TIMEOUT / BIRTHDAY / ANNIVERSARY
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /** 触发条件 JSON (可空, 如 {platformType:"wework", tagIds:[], lifecycle:""}) */
    @Column(name = "event_condition", columnDefinition = "TEXT")
    private String eventCondition;

    /** 条件类型: ALL(全部满足) / ANY(任一满足) */
    @Column(name = "condition_type", nullable = false, length = 20)
    private String conditionType;

    /** 同一客户冷却期小时数, 0=不限 */
    @Column(name = "cooldown_hours")
    private Integer cooldownHours;

    /** 每客户最大触发次数, 0=不限 */
    @Column(name = "max_triggers_per_customer")
    private Integer maxTriggersPerCustomer;

    /**
     * 动作类型: SEND_MESSAGE / ADD_TAG / SET_LIFECYCLE /
     * ENROLL_JOURNEY / NOTIFY_USER / TRIGGER_MASS_SEND
     */
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    /** 动作参数 JSON: {messageTemplateId, tagIds, lifecycle, journeyId, notifyUserId, massSendTaskId} */
    @Column(name = "action_params", columnDefinition = "TEXT", nullable = false)
    private String actionParams;

    /** 延迟执行分钟数 */
    @Column(name = "action_delay_minutes")
    private Integer actionDelayMinutes;

    /** 优先级（数值越大优先级越高） */
    @Column(name = "priority")
    private Integer priority;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 已触发次数 */
    @Column(name = "trigger_count")
    private Integer triggerCount;

    /** 最近触发时间（可空） */
    @Column(name = "last_trigger_at")
    private LocalDateTime lastTriggerAt;

    /** 创建人（可空） */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
