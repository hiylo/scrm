/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBehaviorPathEntity.java
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
 * SCRM 客户行为路径实体。
 * <p>
 * 由同一客户同一会话 ({@code sessionId}) 内的行为事件聚合而成, 记录客户一次会话的完整行为轨迹。
 * {@link #touchpoints} 为经过的触点逗号分隔串, {@link #behaviorSequence} (TEXT JSON) 为完整行为序列
 * {@code [{type, time, touchpoint, page}]}, {@link #entryTouchpoint} / {@link #exitTouchpoint}
 * 标记入口/出口触点。
 * </p>
 * <p>
 * {@link #hasConversion} 标记会话是否含转化行为, {@link #conversionPoint} 记录转化点。
 * 用于常见路径分析、转化路径分析与流失点分析。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_behavior_path", schema = "scrm", indexes = {
        @Index(name = "idx_behavior_path_customer", columnList = "customer_id"),
        @Index(name = "idx_behavior_path_session", columnList = "session_id"),
        @Index(name = "idx_behavior_path_conversion", columnList = "has_conversion"),
        @Index(name = "idx_behavior_path_time", columnList = "session_start_time")
})
@Data
public class ScrmBehaviorPathEntity {

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
        if (hasConversion == null) {
            hasConversion = false;
        }
        if (totalBehaviors == null) {
            totalBehaviors = 0;
        }
        if (totalDurationSeconds == null) {
            totalDurationSeconds = 0;
        }
        if (touchpointCount == null) {
            touchpointCount = 0;
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

    /** 路径名称 (可空) */
    @Column(name = "path_name", length = 200)
    private String pathName;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 会话开始时间 */
    @Column(name = "session_start_time", nullable = false)
    private LocalDateTime sessionStartTime;

    /** 会话结束时间 (可空) */
    @Column(name = "session_end_time")
    private LocalDateTime sessionEndTime;

    /** 经过的触点 (逗号分隔, 可空) */
    @Column(name = "touchpoints", length = 1000)
    private String touchpoints;

    /** JSON 行为序列: [{type, time, touchpoint, page}] */
    @Column(name = "behavior_sequence", nullable = false, columnDefinition = "TEXT")
    private String behaviorSequence;

    /** 总行为数 (默认 0) */
    @Column(name = "total_behaviors")
    private Integer totalBehaviors;

    /** 总停留时长 (秒, 默认 0) */
    @Column(name = "total_duration_seconds")
    private Integer totalDurationSeconds;

    /** 触点数 (默认 0) */
    @Column(name = "touchpoint_count")
    private Integer touchpointCount;

    /** 是否含转化行为 (默认 false) */
    @Column(name = "has_conversion", nullable = false)
    private Boolean hasConversion;

    /** 转化点 (可空) */
    @Column(name = "conversion_point", length = 500)
    private String conversionPoint;

    /** 入口触点 (可空) */
    @Column(name = "entry_touchpoint", length = 50)
    private String entryTouchpoint;

    /** 出口触点 (可空) */
    @Column(name = "exit_touchpoint", length = 50)
    private String exitTouchpoint;

    /** 设备类型 (可空) */
    @Column(name = "device_type", length = 30)
    private String deviceType;

    /** 会话 ID (可空) */
    @Column(name = "session_id", length = 200)
    private String sessionId;
}
