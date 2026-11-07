/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmRecordingEntity.java
 * Date : 2026/10/22 10:00:00
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

import java.time.LocalDateTime;

/**
 * SCRM 通话录音文件实体。
 * <p>
 * 文件本体存放于对象存储（MinIO / OSS），本表仅保存 {@code objectKey} 与归属信息。
 * 来源区分：
 * <ul>
 *   <li>{@code OEM_RECORD} - 手机系统自带通话录音，App 扫描文件后上传</li>
 *   <li>{@code LINE}       - 线路侧录音（云通信/回拨平台回调）</li>
 *   <li>{@code MANUAL}     - 手动上传</li>
 * </ul>
 * 同一文件内容重复上传时按 {@code fileHash} 幂等，只保留一条记录。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_recording", schema = "scrm", indexes = {
        @Index(name = "idx_recording_call", columnList = "call_record_id"),
        @Index(name = "idx_recording_user", columnList = "user_id,create_time"),
        @Index(name = "idx_recording_customer", columnList = "customer_id"),
        @Index(name = "uk_recording_hash", columnList = "file_hash", unique = true)
})
@Data
public class ScrmRecordingEntity {

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

    /** 归属用户 ID（关联 scrm_user.id） */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** 上报设备 ID */
    @Column(name = "device_id", length = 128)
    private String deviceId;

    /** 关联通话记录 ID（可为空, 上传后可再关联） */
    @Column(name = "call_record_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long callRecordId;

    /** 关联客户/线索 ID（可为空），与 customerType 一起确定归属对象 */
    @Column(name = "customer_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 关联对象类型：CUSTOMER(正式客户) / PUBLIC_SEA(公海线索)，可空 */
    @Column(name = "customer_type", length = 20)
    private String customerType;

    /** 对象存储 key（命名空间 scrm/recording/...） */
    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    /** 原始文件名 */
    @Column(name = "file_name", length = 255)
    private String fileName;

    /** 文件大小（字节） */
    @Column(name = "file_size")
    private Long fileSize;

    /** 录音时长（秒, 可由客户端解析音频元数据后上报） */
    @Column(name = "duration_sec")
    private Integer durationSec;

    /** 来源: OEM_RECORD / LINE / MANUAL */
    @Column(name = "source", nullable = false, length = 16)
    private String source;

    /** 文件内容 SHA-256（服务端去重键） */
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    /** 状态: UPLOADED(已上传) / DELETED(已删除) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;
}
