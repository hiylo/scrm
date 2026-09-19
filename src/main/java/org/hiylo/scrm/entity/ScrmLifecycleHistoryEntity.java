/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLifecycleHistoryEntity.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 客户生命周期阶段转换历史实体。
 * <p>
 * 记录客户每次阶段转换的完整审计信息: {@link #fromStageId} / {@link #toStageId}
 * 标识源/目标阶段, {@link #transitionId} 关联触发规则, {@link #transitionType}
 * / {@link #triggerEvent} / {@link #triggerDescription} 记录转换类型与触发信息,
 * {@link #durationInPreviousStage} 记录上一阶段停留天数,
 * {@link #operatorId} / {@link #operatorName} 记录操作人,
 * {@link #metadata} (JSON) 承载附加数据。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_lifecycle_history", schema = "scrm", indexes = {
        @Index(name = "idx_lifecycle_history_customer", columnList = "customer_id"),
        @Index(name = "idx_lifecycle_history_to_stage", columnList = "to_stage_id"),
        @Index(name = "idx_lifecycle_history_type", columnList = "transition_type"),
        @Index(name = "idx_lifecycle_history_time", columnList = "transition_time")
})
@Data
public class ScrmLifecycleHistoryEntity {

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

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 源阶段 ID (可空, null 表示新客户进入) */
    @Column(name = "from_stage_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromStageId;

    /** 源阶段编码 (可空) */
    @Column(name = "from_stage_code", length = 50)
    private String fromStageCode;

    /** 源阶段名称 (可空) */
    @Column(name = "from_stage_name", length = 100)
    private String fromStageName;

    /** 目标阶段 ID */
    @Column(name = "to_stage_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toStageId;

    /** 目标阶段编码 */
    @Column(name = "to_stage_code", nullable = false, length = 50)
    private String toStageCode;

    /** 目标阶段名称 (可空) */
    @Column(name = "to_stage_name", length = 100)
    private String toStageName;

    /** 关联转换规则 ID (可空) */
    @Column(name = "transition_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long transitionId;

    /** 转换类型: AUTO/MANUAL/SYSTEM */
    @Column(name = "transition_type", nullable = false, length = 20)
    private String transitionType;

    /** 触发事件 (可空): PURCHASE/LOGIN/INACTIVE_DAYS/FIRST_CONTACT/REFUND/CUSTOM */
    @Column(name = "trigger_event", length = 100)
    private String triggerEvent;

    /** 触发描述 (可空) */
    @Column(name = "trigger_description", length = 500)
    private String triggerDescription;

    /** 上一阶段停留天数 (可空) */
    @Column(name = "duration_in_previous_stage")
    private Integer durationInPreviousStage;

    /** 操作人 ID (可空) */
    @Column(name = "operator_id", length = 100)
    private String operatorId;

    /** 操作人名称 (可空) */
    @Column(name = "operator_name", length = 100)
    private String operatorName;

    /** 转换时间 */
    @Column(name = "transition_time", nullable = false)
    private LocalDateTime transitionTime;

    /** JSON 附加数据 (可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
