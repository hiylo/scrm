/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWeWorkArchiveCursorEntity.java
 * Date : 2026/07/27 02:41:22
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
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * 企微会话存档拉取游标实体。
 * <p>
 * 每个存档配置对应一条游标记录, 记录当前已拉取的 seq、企微最新 seq 与拉取状态。
 * 拉取任务基于游标位置增量拉取, 避免重复入库与遗漏。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_wework_archive_cursor", schema = "scrm", indexes = {
        @Index(name = "idx_wework_archive_cursor_config", columnList = "config_id"),
        @Index(name = "idx_wework_archive_cursor_status", columnList = "status")
})
@Data
public class ScrmWeWorkArchiveCursorEntity {

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

    /** 存档配置 ID（引用 scrm_wework_archive_config.id） */
    @Column(name = "config_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long configId;

    /** 当前游标 seq */
    @Column(name = "cursor_seq", nullable = false)
    private Long cursorSeq;

    /** 企微最新 seq */
    @Column(name = "last_seq", nullable = false)
    private Long lastSeq;

    /** 已拉取数 */
    @Column(name = "fetched_count", nullable = false)
    private Integer fetchedCount = 0;

    /** 错误次数 */
    @Column(name = "error_count", nullable = false)
    private Integer errorCount = 0;

    /** 创建时间 (内存字段: 本表无 created_at 列, 拉取时间由 last_fetch_at 承担) */
    @Transient
    private LocalDateTime createdAt;

    /** 更新时间 (内存字段: 本表无 updated_at 列) */
    @Transient
    private LocalDateTime updatedAt;

    /** 最后错误信息（可空） */
    @Column(name = "last_error", length = 500)
    private String lastError;

    /** 最后拉取时间 */
    @Column(name = "last_fetch_at", nullable = false)
    private LocalDateTime lastFetchAt;

    /** 状态: IDLE(空闲) / FETCHING(拉取中) / ERROR(异常) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;
}
