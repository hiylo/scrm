/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChannelCodeScanEntity.java
 * Date : 2026/07/29 21:19:51
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
 * SCRM 渠道活码扫码记录实体。
 * <p>
 * 记录每次扫码事件及分配到的账号, 支持扫码转化统计。
 * added 状态: PENDING(待添加) / ADDED(已添加) / REJECTED(已拒绝)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_channel_code_scan", schema = "scrm", indexes = {
        @Index(name = "idx_channel_code_scan_code", columnList = "channel_code_id"),
        @Index(name = "idx_channel_code_scan_scanner", columnList = "scanner_uid"),
        @Index(name = "idx_channel_code_scan_added", columnList = "added")
})
@Data
public class ScrmChannelCodeScanEntity {

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

    /** 渠道活码 ID（引用 scrm_channel_code.id） */
    @Column(name = "channel_code_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long channelCodeId;

    /** 扫码者唯一标识（可空） */
    @Column(name = "scanner_uid", length = 200)
    private String scannerUid;

    /** 扫码者昵称（可空） */
    @Column(name = "scanner_nickname", length = 200)
    private String scannerNickname;

    /** 分配到的账号 ID（可空, 分配失败时为 null） */
    @Column(name = "assigned_account_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long assignedAccountId;

    /** 扫码者 IP（可空） */
    @Column(name = "ip", length = 50)
    private String ip;

    /** 扫码者 User-Agent（可空） */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** 扫码时间 */
    @Column(name = "scanned_at", nullable = false)
    private LocalDateTime scannedAt;

    /** 添加状态: PENDING / ADDED / REJECTED */
    @Column(name = "added", nullable = false, length = 20)
    private String added;
}
