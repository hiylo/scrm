/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConfigHistoryEntity.java
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
 * SCRM 配置变更历史实体。
 * <p>
 * 描述一次配置变更的完整信息, 包括新旧值、显示值、变更类型、变更原因、变更人、
 * 变更时间、回滚信息与审核状态, 用于配置变更追踪、回滚与审计。变更类型
 * (changeType) 标识变更操作语义, 审核状态 (reviewStatus) 用于变更审核流程。
 * </p>
 * <p>
 * 变更类型: CREATE / UPDATE / DELETE / ENABLE / DISABLE / IMPORT / EXPORT / RESET。
 * 审核状态: PENDING / APPROVED / REJECTED。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_config_history", schema = "scrm", indexes = {
        @Index(name = "idx_config_history_config", columnList = "config_id"),
        @Index(name = "idx_config_history_key", columnList = "config_key"),
        @Index(name = "idx_config_history_type", columnList = "change_type"),
        @Index(name = "idx_config_history_user", columnList = "changed_by"),
        @Index(name = "idx_config_history_time", columnList = "changed_at")
})
@Data
public class ScrmConfigHistoryEntity {

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

    /** 配置 ID (可空) */
    @Column(name = "config_id")
    private Long configId;

    /** 配置键 */
    @Column(name = "config_key", nullable = false, length = 200)
    private String configKey;

    /** 配置名称 (可空, 快照) */
    @Column(name = "config_name", length = 200)
    private String configName;

    /** 配置分组 (可空, 快照) */
    @Column(name = "config_group", length = 100)
    private String configGroup;

    /** 旧值 (可空, TEXT) */
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    /** 新值 (可空, TEXT) */
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    /** 旧显示值 (可空) */
    @Column(name = "old_display_value", length = 2000)
    private String oldDisplayValue;

    /** 新显示值 (可空) */
    @Column(name = "new_display_value", length = 2000)
    private String newDisplayValue;

    /** 变更类型: CREATE/UPDATE/DELETE/ENABLE/DISABLE/IMPORT/EXPORT/RESET */
    @Column(name = "change_type", nullable = false, length = 30)
    private String changeType;

    /** 变更原因 (可空) */
    @Column(name = "change_reason", length = 500)
    private String changeReason;

    /** 变更人 */
    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    /** 变更时间 */
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    /** IP 地址 (可空) */
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    /** User-Agent (可空) */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** 会话 ID (可空) */
    @Column(name = "session_id", length = 200)
    private String sessionId;

    /** 可回滚 (默认 TRUE) */
    @Column(name = "rollback_possible", nullable = false)
    private Boolean rollbackPossible;

    /** 回滚关联历史 ID (可空) */
    @Column(name = "rollback_by_id")
    private Long rollbackById;

    /** 已回滚 (默认 FALSE) */
    @Column(name = "is_rolled_back", nullable = false)
    private Boolean isRolledBack;

    /** 回滚时间 (可空) */
    @Column(name = "rolled_back_at")
    private LocalDateTime rolledBackAt;

    /** 回滚人 (可空) */
    @Column(name = "rolled_back_by", length = 100)
    private String rolledBackBy;

    /** 审核状态: PENDING/APPROVED/REJECTED (可空) */
    @Column(name = "review_status", length = 20)
    private String reviewStatus;

    /** 审核人 (可空) */
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    /** 审核时间 (可空) */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 附加数据 JSON (可空, TEXT) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
