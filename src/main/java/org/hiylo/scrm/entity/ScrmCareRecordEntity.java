/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCareRecordEntity.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 客户关怀记录实体。
 * <p>
 * 记录已执行的关怀动作及客户回应, 用于关怀效果跟踪与分析。{@link #careResult} 标注关怀结果
 * (SUCCESS/NO_RESPONSE/REJECTED/FAILED), {@link #customerResponse} 记录客户回应内容,
 * {@link #responseTimeHours} 记录客户回应时长, {@link #sentiment} 标注情感倾向
 * (POSITIVE/NEUTRAL/NEGATIVE)。
 * </p>
 * <p>
 * 关怀任务执行成功后由 {@code ScrmCustomerCareService.executeTask} 写入对应记录,
 * 供 {@code getCareEffectiveness} 效果分析使用。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_care_record", schema = "scrm", indexes = {
        @Index(name = "idx_care_record_customer", columnList = "customer_id"),
        @Index(name = "idx_care_record_care_type", columnList = "care_type"),
        @Index(name = "idx_care_record_result", columnList = "care_result"),
        @Index(name = "idx_care_record_executed", columnList = "executed_at"),
        @Index(name = "idx_care_record_assignee", columnList = "assignee_id")
})
@Data
public class ScrmCareRecordEntity {

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

    /** 客户名称 (冗余, 便于列表展示) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 关怀类型: BIRTHDAY / FESTIVAL / ANNIVERSARY / MEMBERSHIP_EXPIRY / INACTIVITY_REMINDER / CUSTOM */
    @Column(name = "care_type", nullable = false, length = 30)
    private String careType;

    /** 关怀日期 */
    @Column(name = "care_date", nullable = false)
    private LocalDate careDate;

    /** 关怀动作: SEND_MESSAGE / SEND_COUPON / SEND_GIFT / CALL / CREATE_TASK / NOTIFY_ASSIGNEE */
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    /** 动作详情 (可空) */
    @Column(name = "action_detail", length = 500)
    private String actionDetail;

    /** 关怀结果: SUCCESS / NO_RESPONSE / REJECTED / FAILED */
    @Column(name = "care_result", nullable = false, length = 20)
    private String careResult;

    /** 客户回应 (可空) */
    @Column(name = "customer_response", length = 500)
    private String customerResponse;

    /** 客户回应时长 (小时, 可空) */
    @Column(name = "response_time_hours")
    private Integer responseTimeHours;

    /** 情感倾向: POSITIVE / NEUTRAL / NEGATIVE (可空) */
    @Column(name = "sentiment", length = 20)
    private String sentiment;

    /** 负责人 ID (可空) */
    @Column(name = "assignee_id", length = 100)
    private String assigneeId;

    /** 执行时间 */
    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;
}
