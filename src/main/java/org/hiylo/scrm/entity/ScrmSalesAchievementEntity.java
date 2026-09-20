/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSalesAchievementEntity.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 销售达成记录实体。
 * <p>
 * 记录每次达成事件 (订单/跟进/转化/手动调整), 单次达成值累加至目标实际值,
 * 数据来源标识便于审计与回溯。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_sales_achievement", schema = "scrm", indexes = {
        @Index(name = "idx_sales_achievement_target", columnList = "target_id"),
        @Index(name = "idx_sales_achievement_date", columnList = "achievement_date"),
        @Index(name = "idx_sales_achievement_source", columnList = "source_type")
})
@Data
public class ScrmSalesAchievementEntity {

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

    /** 关联的销售目标 ID */
    @Column(name = "target_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;

    /** 目标对象类型: INDIVIDUAL / TEAM / DEPARTMENT */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    /** 目标对象 ID (userId / teamId / deptId) */
    @Column(name = "target_id_ref", nullable = false, length = 100)
    private String targetIdRef;

    /** 目标对象名称 (冗余便于展示) */
    @Column(name = "target_name_ref", length = 200)
    private String targetNameRef;

    /** 周期类型: WEEKLY / MONTHLY / QUARTERLY / YEARLY */
    @Column(name = "period_type", nullable = false, length = 10)
    private String periodType;

    /** 周期开始日期 */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    /** 周期结束日期 */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** 指标类型: REVENUE / NEW_CUSTOMERS / CONVERSIONS / FOLLOW_UPS / OPPORTUNITIES / CALLS */
    @Column(name = "metric_type", nullable = false, length = 30)
    private String metricType;

    /** 本次达成值 */
    @Column(name = "achieved_value", nullable = false)
    private Double achievedValue;

    /** 达成日期 */
    @Column(name = "achievement_date", nullable = false)
    private LocalDate achievementDate;

    /** 数据来源: ORDER / FOLLOW_UP / CONVERSION / MANUAL_ADJUST */
    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    /** 来源 ID (订单 ID / 跟进 ID / 转化 ID 等) */
    @Column(name = "source_id", length = 100)
    private String sourceId;

    /** 备注 */
    @Column(name = "note", length = 500)
    private String note;

    /** 记录时间 */
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    /** 记录人 */
    @Column(name = "recorded_by", nullable = false, length = 100)
    private String recordedBy;
}
