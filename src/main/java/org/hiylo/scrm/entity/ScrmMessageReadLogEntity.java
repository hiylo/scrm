/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageReadLogEntity.java
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
 * SCRM 消息阅读日志实体。
 * <p>
 * 记录消息每次阅读的详细轨迹: 阅读者 (readerId/readerName/readerType)、阅读时间
 * (readAt)、本次阅读时长 (readDurationSeconds)、阅读来源 (APP/WEB/EMAIL_CLIENT/WECHAT)、
 * 设备环境 (deviceType/os/browser/clientIp/location)、是否重复阅读 (isRepeatedRead)
 * 与第几次阅读 (sequence)。一条消息跟踪记录可关联多条阅读日志, 用于阅读行为深度分析。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_message_read_log", schema = "scrm", indexes = {
        @Index(name = "idx_msg_read_log_tracking", columnList = "message_tracking_id"),
        @Index(name = "idx_msg_read_log_message_id", columnList = "message_id"),
        @Index(name = "idx_msg_read_log_reader", columnList = "reader_id"),
        @Index(name = "idx_msg_read_log_read_at", columnList = "read_at")
})
@Data
public class ScrmMessageReadLogEntity {

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
     * 持久化前回调: 自动填充创建/更新时间与版本号初值, 并补齐可空字段的默认值
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
        if (readerType == null) {
            readerType = "CUSTOMER";
        }
        if (readDurationSeconds == null) {
            readDurationSeconds = 0;
        }
        if (isRepeatedRead == null) {
            isRepeatedRead = false;
        }
        if (sequence == null) {
            sequence = 1;
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

    /** 关联消息跟踪 ID (引用 scrm_message_tracking.id) */
    @Column(name = "message_tracking_id", nullable = false)
    private Long messageTrackingId;

    /** 消息 ID (冗余, 便于直接按 messageId 查询) */
    @Column(name = "message_id", nullable = false, length = 200)
    private String messageId;

    /** 阅读者 ID */
    @Column(name = "reader_id", nullable = false, length = 200)
    private String readerId;

    /** 阅读者名称 (可空) */
    @Column(name = "reader_name", length = 200)
    private String readerName;

    /** 阅读者类型: CUSTOMER/AGENT/SYSTEM (默认 CUSTOMER) */
    @Column(name = "reader_type", nullable = false, length = 20)
    private String readerType;

    /** 阅读时间 */
    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    /** 本次阅读时长 (秒, 默认 0) */
    @Column(name = "read_duration_seconds")
    private Integer readDurationSeconds;

    /** 阅读来源: APP/WEB/EMAIL_CLIENT/WECHAT (可空) */
    @Column(name = "read_source", length = 50)
    private String readSource;

    /** 设备类型 (可空) */
    @Column(name = "device_type", length = 30)
    private String deviceType;

    /** 操作系统 (可空) */
    @Column(name = "os", length = 50)
    private String os;

    /** 浏览器 (可空) */
    @Column(name = "browser", length = 100)
    private String browser;

    /** 客户端 IP (可空) */
    @Column(name = "client_ip", length = 100)
    private String clientIp;

    /** 地理位置 (可空) */
    @Column(name = "location", length = 200)
    private String location;

    /** 是否重复阅读 (默认 false) */
    @Column(name = "is_repeated_read", nullable = false)
    private Boolean isRepeatedRead;

    /** 第几次阅读 (默认 1) */
    @Column(name = "sequence")
    private Integer sequence;
}
