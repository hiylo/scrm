/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLifecycleHistoryEntity.java
 * Date : 2026/07/29 21:19:51
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
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import jakarta.persistence.PreUpdate;

/**
 * 客户生命周期变更历史实体。
 * <p>
 * 每次客户生命周期阶段变更时由 {@code ScrmCustomerService.updateLifecycle} 写入一条记录,
 * 记录变更前后的阶段、操作人、备注与时间, 支撑生命周期追溯与转化漏斗分析。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_lifecycle_history", schema = "scrm", indexes = {
        @Index(name = "idx_lifecycle_hist_customer", columnList = "customer_id"),
        @Index(name = "idx_lifecycle_hist_changed_at", columnList = "changed_at")
})
@Data
public class ScrmCustomerLifecycleHistoryEntity {

    /** 主键 ID（Snowflake 雪花算法生成） */
    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", strategy = "org.hiylo.scrm.config.SnowflakeIdGenerator")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 变更前的生命周期阶段: LEAD / PROSPECT / ACTIVE / DORMANT / CHURNED */
    @Column(name = "previous_lifecycle", length = 30)
    private String previousLifecycle;

    /** 变更后的生命周期阶段: LEAD / PROSPECT / ACTIVE / DORMANT / CHURNED */
    @Column(name = "new_lifecycle", nullable = false, length = 30)
    private String newLifecycle;

    /** 变更备注 (操作人填写, 可空) */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 操作人用户 ID (从请求头 X-User-Id 获取) */
    @Column(name = "operator_id", length = 100)
    private String operatorId;

    /** 操作人用户名 (从请求头 X-Username 获取) */
    @Column(name = "operator_name", length = 100)
    private String operatorName;

    /** 变更时间 */
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    /**
     * 插入前自动设置变更时间。
     */
    @PrePersist
    protected void onCreate() {
        if (this.changedAt == null) {
            this.changedAt = LocalDateTime.now();
        }
    }

    /** 创建时间 (内存字段: 本表无 created_at 列, 审计时间由 changed_at 承担) */
    @Transient
    private LocalDateTime createdAt;

}
