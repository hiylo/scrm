/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignExecutionLogEntity.java
 * Date : 2026/07/27 02:41:22
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
 * SCRM 营销任务执行日志实体。
 * <p>
 * 记录营销任务生命周期中关键动作 (START / PAUSE / RESUME / STOP / CALLBACK) 的执行轨迹,
 * 包括操作结果 (SUCCESS / FAILED / RUNNING)、错误码、错误消息、操作人与操作时间,
 * 用于任务执行追踪、失败排查与统计聚合。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_campaign_execution_log", schema = "scrm", indexes = {
        @Index(name = "idx_exec_log_campaign", columnList = "campaign_id"),
        @Index(name = "idx_exec_log_operated_at", columnList = "operated_at")
})
@Data
public class ScrmCampaignExecutionLogEntity {

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

    /** 关联营销任务 ID (引用 scrm_campaign.id) */
    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    /** 关联执行任务 ID (可空, 回调场景下可能尚未回写) */
    @Column(name = "behavior_flow_id")
    private Long behaviorFlowId;

    /** 动作类型: START / PAUSE / RESUME / STOP / CALLBACK */
    @Column(name = "action", nullable = false, length = 20)
    private String action;

    /** 执行状态: SUCCESS / FAILED / RUNNING */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 错误码 (FAILED 时携带, 可空) */
    @Column(name = "error_code", length = 100)
    private String errorCode;

    /** 错误消息 (FAILED 时携带, 可空) */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 操作人 (可空, 系统自动调度时为 null) */
    @Column(name = "operated_by", length = 100)
    private String operatedBy;

    /** 操作时间 */
    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt;
}
