/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerTimelineEntity.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 客户时间线事件实体。
 * <p>
 * 记录客户全生命周期内的关键事件 (创建 / 标签变更 / 生命周期变更 / 跟进完成 / 消息收发 /
 * 商机推进 / 旅程进度 / 群发触达 / 营销触发 / 备注新增 / 文件分享等),
 * 支撑客户 360° 视图的时间线展示与互动历史追溯。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_timeline", schema = "scrm", indexes = {
        @Index(name = "idx_customer_timeline_customer", columnList = "customer_id"),
        @Index(name = "idx_customer_timeline_event_type", columnList = "event_type"),
        @Index(name = "idx_customer_timeline_event_time", columnList = "event_time"),
        @Index(name = "idx_customer_timeline_customer_time", columnList = "customer_id,event_time")
})
@Data
public class ScrmCustomerTimelineEntity {

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

    /** 乐观锁版本号（并发更新保护，后写入者触发 OptimisticLockException） */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 持久化前回调: 自动填充创建/更新时间、版本号初值与事件时间
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
        if (eventTime == null) {
            eventTime = now;
        }
        if (importance == null) {
            importance = IMPORTANCE_NORMAL;
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

    /** 客户 ID（引用 scrm_customer.id） */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 事件类型: CUSTOMER_CREATED/TAG_ADDED/TAG_REMOVED/LIFECYCLE_CHANGED/FOLLOW_UP_COMPLETED/MESSAGE_SENT/MESSAGE_RECEIVED/OPPORTUNITY_CREATED/OPPORTUNITY_STAGE_CHANGED/JOURNEY_ENROLLED/JOURNEY_STEP_COMPLETED/MASS_SEND_RECEIVED/CAMPAIGN_TRIGGERED/NOTE_ADDED/FILE_SHARED */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /** 事件标题 */
    @Column(name = "event_title", nullable = false, length = 200)
    private String eventTitle;

    /** 事件详情 (JSON 字符串, 可空) */
    @Column(name = "event_detail", columnDefinition = "TEXT")
    private String eventDetail;

    /** 事件发生时间 */
    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    /** 操作人 ID (可空, 系统事件无操作人) */
    @Column(name = "operator_id", length = 100)
    private String operatorId;

    /** 操作人姓名 (可空) */
    @Column(name = "operator_name", length = 100)
    private String operatorName;

    /** 平台类型 (可空, 标识事件来源平台) */
    @Column(name = "platform_type", length = 30)
    private String platformType;

    /** 重要级别: HIGH / NORMAL / LOW (默认 NORMAL) */
    @Column(name = "importance", nullable = false, length = 10)
    private String importance;

    /** 默认重要级别: 普通 */
    public static final String IMPORTANCE_NORMAL = "NORMAL";
}
