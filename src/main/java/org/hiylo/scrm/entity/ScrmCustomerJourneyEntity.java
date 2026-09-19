/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerJourneyEntity.java
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
 * SCRM 营销 SOP 客户旅程实体。
 * <p>
 * 面向客户关系的旅程编排, 定义一套多步骤 SOP (发消息 / 等待 / 条件分支 / 打标签 / 改生命周期 /
 * Webhook / 结束)。客户入旅程后按步骤自动执行, 区别于设备行为流。
 * 一个旅程包含若干 {@link ScrmJourneyStepEntity} 步骤, 客户入旅程后生成
 * {@link ScrmJourneyEnrollmentEntity} 记录并按步骤推进。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_journey", schema = "scrm", indexes = {
        @Index(name = "idx_customer_journey_status", columnList = "status"),
        @Index(name = "idx_customer_journey_entry_type", columnList = "entry_type")
})
@Data
public class ScrmCustomerJourneyEntity {

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

    /** 旅程名称 */
    @Column(name = "journey_name", nullable = false, length = 200)
    private String journeyName;

    /** 旅程描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 旅程目标 (如 新客转化 / 激活沉睡 / 复购引导) */
    @Column(name = "goal", length = 200)
    private String goal;

    /** 入旅程条件 (JSON: {event:"CUSTOMER_ADDED", filter:{platformType:"wework"}}) */
    @Column(name = "entry_condition", nullable = false, columnDefinition = "TEXT")
    private String entryCondition;

    /** 入旅程方式: EVENT / MANUAL / API */
    @Column(name = "entry_type", nullable = false, length = 20)
    private String entryType;

    /** 旅程状态: DRAFT / PUBLISHED / PAUSED / ARCHIVED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 入旅程客户数 */
    @Column(name = "enrolled_count", nullable = false)
    private Integer enrolledCount;

    /** 完成旅程客户数 */
    @Column(name = "completed_count", nullable = false)
    private Integer completedCount;

    /** 退出旅程客户数 */
    @Column(name = "exited_count", nullable = false)
    private Integer exitedCount;

    /** 转化率 (完成数 / 入旅程数 * 100) */
    @Column(name = "conversion_rate")
    private Double conversionRate;

    /** 旅程业务版本号 (发布后递增, 区别于乐观锁 version) */
    @Column(name = "journey_version", nullable = false)
    private Integer journeyVersion;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
