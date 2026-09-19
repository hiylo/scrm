/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmJourneyProgressLogEntity.java
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
 * SCRM 旅程进度日志实体。
 * <p>
 * 记录客户在旅程中每一步的执行明细, 包括步骤名称 / 类型 / 执行结果与详情,
 * 用于旅程执行审计与转化分析。每个 enrollment 的每一步执行产生一条日志记录。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_journey_progress_log", schema = "scrm", indexes = {
        @Index(name = "idx_journey_progress_log_enrollment_id", columnList = "enrollment_id"),
        @Index(name = "idx_journey_progress_log_journey_id", columnList = "journey_id"),
        @Index(name = "idx_journey_progress_log_customer_id", columnList = "customer_id")
})
@Data
public class ScrmJourneyProgressLogEntity {

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

    /** 入营记录 ID */
    @Column(name = "enrollment_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long enrollmentId;

    /** 所属旅程 ID */
    @Column(name = "journey_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long journeyId;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 步骤 ID */
    @Column(name = "step_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long stepId;

    /** 步骤名称 (冗余字段, 便于日志展示) */
    @Column(name = "step_name", length = 200)
    private String stepName;

    /** 步骤类型: SEND_MESSAGE / WAIT / CONDITION / ADD_TAG / SET_LIFECYCLE / WEBHOOK / END */
    @Column(name = "step_type", nullable = false, length = 30)
    private String stepType;

    /** 执行结果: SUCCESS / FAILED / SKIPPED / WAITING */
    @Column(name = "action_result", nullable = false, length = 20)
    private String actionResult;

    /** 执行详情 (如失败原因 / 等待时长 / 条件判断结果) */
    @Column(name = "action_detail", length = 500)
    private String actionDetail;

    /** 执行时间 */
    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;
}
