/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLifecycleStageEntity.java
 * Date : 2026/08/05 08:55:12
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

import java.time.LocalDateTime;

/**
 * SCRM 客户生命周期阶段实体。
 * <p>
 * 描述客户生命周期中的一个阶段 (如: 潜客 / 新客 / 活跃 / 沉默 / 流失)。
 * {@link #stageCategory} 划分阶段大类 (获客 / 互动 / 激活 / 留存 / 推荐 / 流失 / 唤醒),
 * {@link #stageOrder} 控制阶段先后顺序, {@link #entryCriteria} / {@link #exitCriteria}
 * 以 JSON 形式承载进入/退出条件, {@link #targetDurationDays} 定义目标停留天数用于超期判定,
 * {@link #isStartStage} / {@link #isEndStage} / {@link #isChurnStage} 标记特殊阶段。
 * 统计字段 {@link #customerCount} / {@link #totalEnteredCount} / {@link #avgDurationDays}
 * / {@link #conversionRate} 由服务端按需刷新。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_lifecycle_stage", schema = "scrm", indexes = {
        @Index(name = "idx_lifecycle_stage_code", columnList = "stage_code", unique = true),
        @Index(name = "idx_lifecycle_stage_category", columnList = "stage_category"),
        @Index(name = "idx_lifecycle_stage_enabled", columnList = "enabled"),
        @Index(name = "idx_lifecycle_stage_order", columnList = "stage_order")
})
@Data
public class ScrmLifecycleStageEntity {

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

    /** 阶段名称 */
    @Column(name = "stage_name", nullable = false, length = 100)
    private String stageName;

    /** 阶段编码 (全局唯一) */
    @Column(name = "stage_code", nullable = false, length = 50)
    private String stageCode;

    /** 阶段描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 阶段顺序 (值越小越靠前) */
    @Column(name = "stage_order", nullable = false)
    private Integer stageOrder;

    /** 阶段类别: ACQUISITION/ENGAGEMENT/ACTIVATION/RETENTION/ADVOCACY/CHURN/REACTIVATION */
    @Column(name = "stage_category", nullable = false, length = 30)
    private String stageCategory;

    /** 阶段颜色标识 (可空) */
    @Column(name = "color", length = 20)
    private String color;

    /** 阶段图标 (可空) */
    @Column(name = "icon", length = 100)
    private String icon;

    /** 进入条件 JSON (可空) */
    @Column(name = "entry_criteria", columnDefinition = "TEXT")
    private String entryCriteria;

    /** 退出条件 JSON (可空) */
    @Column(name = "exit_criteria", columnDefinition = "TEXT")
    private String exitCriteria;

    /** 目标停留天数 (可空, 用于超期判定) */
    @Column(name = "target_duration_days")
    private Integer targetDurationDays;

    /** 是否起始阶段 (默认 FALSE) */
    @Column(name = "is_start_stage", nullable = false)
    private Boolean isStartStage;

    /** 是否终止阶段 (默认 FALSE) */
    @Column(name = "is_end_stage", nullable = false)
    private Boolean isEndStage;

    /** 是否流失阶段 (默认 FALSE) */
    @Column(name = "is_churn_stage", nullable = false)
    private Boolean isChurnStage;

    /** 当前客户数 (默认 0) */
    @Column(name = "customer_count")
    private Integer customerCount;

    /** 累计进入数 (默认 0) */
    @Column(name = "total_entered_count")
    private Integer totalEnteredCount;

    /** 平均停留天数 (默认 0) */
    @Column(name = "avg_duration_days")
    private Double avgDurationDays;

    /** 转化率到下一阶段 (默认 0) */
    @Column(name = "conversion_rate")
    private Double conversionRate;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
