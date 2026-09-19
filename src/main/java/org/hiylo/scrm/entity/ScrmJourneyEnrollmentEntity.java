/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmJourneyEnrollmentEntity.java
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
 * SCRM 旅程入营记录实体。
 * <p>
 * 描述客户进入某旅程后的一条执行记录, 跟踪客户在旅程中的进度。
 * currentStepId 标识当前所处步骤, status 标识入营状态 (ACTIVE / COMPLETED / EXITED / FAILED)。
 * WAIT 类型步骤执行后, nextStepAt 记录下一步应执行的时间, 由定时任务调度推进。
 * progress 字段记录进度百分比 (0-100)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_journey_enrollment", schema = "scrm", indexes = {
        @Index(name = "idx_journey_enrollment_journey_id", columnList = "journey_id"),
        @Index(name = "idx_journey_enrollment_customer_id", columnList = "customer_id"),
        @Index(name = "idx_journey_enrollment_status", columnList = "status"),
        @Index(name = "idx_journey_enrollment_next_step_at", columnList = "next_step_at")
})
@Data
public class ScrmJourneyEnrollmentEntity {

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

    /** 所属旅程 ID */
    @Column(name = "journey_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long journeyId;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户昵称 (冗余字段, 便于查询展示) */
    @Column(name = "customer_nickname", length = 200)
    private String customerNickname;

    /** 当前步骤 ID */
    @Column(name = "current_step_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentStepId;

    /** 入营来源: EVENT / MANUAL / API */
    @Column(name = "entry_source", nullable = false, length = 50)
    private String entrySource;

    /** 入营状态: ACTIVE / COMPLETED / EXITED / FAILED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 入营时间 */
    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt;

    /** 完成时间 */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 退出时间 */
    @Column(name = "exited_at")
    private LocalDateTime exitedAt;

    /** 退出原因 */
    @Column(name = "exit_reason", length = 500)
    private String exitReason;

    /** 上一步执行时间 */
    @Column(name = "last_step_at")
    private LocalDateTime lastStepAt;

    /** 下一步执行时间 (WAIT 步骤用, 定时任务扫描此字段) */
    @Column(name = "next_step_at")
    private LocalDateTime nextStepAt;

    /** 进度百分比 (0-100) */
    @Column(name = "progress")
    private Integer progress;
}
