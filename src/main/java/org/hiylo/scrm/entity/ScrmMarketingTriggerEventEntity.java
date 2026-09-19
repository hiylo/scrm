/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingTriggerEventEntity.java
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
 * SCRM 触发式营销事件记录实体。
 * <p>
 * 记录每次事件触发的执行明细: 关联的触发器、客户、事件数据、动作执行状态与结果。
 * 状态流转: PENDING(待执行) → EXECUTING(执行中) → SUCCESS/FAILED/SKIPPED/COOLDOWN。
 * 由 {@link ScrmMarketingTriggerEntity} 触发后异步执行。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_marketing_trigger_event", schema = "scrm", indexes = {
        @Index(name = "idx_marketing_trigger_event_trigger_id", columnList = "trigger_id"),
        @Index(name = "idx_marketing_trigger_event_customer", columnList = "customer_id"),
        @Index(name = "idx_marketing_trigger_event_status", columnList = "status"),
        @Index(name = "idx_marketing_trigger_event_scheduled", columnList = "scheduled_at")
})
@Data
public class ScrmMarketingTriggerEventEntity {

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

    /** 触发器 ID（引用 scrm_marketing_trigger.id） */
    @Column(name = "trigger_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long triggerId;

    /** 客户 ID（引用 scrm_customer.id） */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户昵称（可空, 冗余存储便于列表展示） */
    @Column(name = "customer_nickname", length = 200)
    private String customerNickname;

    /** 事件类型 */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /** 事件数据 JSON（可空, 携带触发时的上下文数据） */
    @Column(name = "event_data", columnDefinition = "TEXT")
    private String eventData;

    /** 状态: PENDING / EXECUTING / SUCCESS / FAILED / SKIPPED / COOLDOWN */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 动作类型 */
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    /** 动作执行结果（可空） */
    @Column(name = "action_result", length = 500)
    private String actionResult;

    /** 错误信息（可空, 执行失败时填充） */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** 计划执行时间（含延迟, 到期后由处理任务捞取执行） */
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    /** 实际执行时间（可空, 未执行时为 null） */
    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    /** 该客户已触发次数（冗余存储, 用于 maxTriggersPerCustomer 限流判断） */
    @Column(name = "trigger_count")
    private Integer triggerCount;
}
