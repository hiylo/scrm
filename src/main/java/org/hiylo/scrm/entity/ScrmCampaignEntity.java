/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignEntity.java
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
 * SCRM 营销任务实体。
 * <p>
 * 营销任务描述一次自动化运营动作（自动加好友 / 自动发帖 / 自动聊天 / 自动养号 / 自动回复），
 * 可关联执行任务 ID 与设备编排进行调度执行，支持 cron 定时与时间窗口。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_campaign", schema = "scrm", indexes = {
        @Index(name = "idx_campaign_status", columnList = "status"),
        @Index(name = "idx_campaign_platform_type", columnList = "platform_type"),
        @Index(name = "idx_campaign_behavior_flow_id", columnList = "behavior_flow_id")
})
@Data
public class ScrmCampaignEntity {

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

    /** 任务名称 */
    @Column(name = "campaign_name", nullable = false, length = 200)
    private String campaignName;

    /** 任务类型：AUTO_ADD_FRIEND / AUTO_POST / AUTO_CHAT / AUTO_NURTURE / AUTO_REPLY */
    @Column(name = "campaign_type", nullable = false, length = 30)
    private String campaignType;

    /** 平台类型 */
    @Column(name = "platform_type", nullable = false, length = 30)
    private String platformType;

    /** 关联设备编排 ID（可空） */
    @Column(name = "fleet_id")
    private Long fleetId;

    /** 任务状态：DRAFT / RUNNING / PAUSED / COMPLETED / FAILED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** cron 表达式（可空，支持定时调度） */
    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    /** 任务开始时间 */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** 任务结束时间 */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 执行任务 ID（可空，历史列名为 behavior_flow_id, 语义已变更为执行任务登记 ID） */
    @Column(name = "behavior_flow_id")
    private Long behaviorFlowId;
}
