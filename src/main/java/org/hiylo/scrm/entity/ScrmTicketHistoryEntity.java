/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTicketHistoryEntity.java
 * Date : 2026/07/29 21:19:51
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
 * SCRM 工单流转历史实体。
 * <p>
 * 记录工单生命周期中的关键动作轨迹: 创建 (CREATED)、分配 (ASSIGNED)、状态变更 (STATUS_CHANGED)、
 * 优先级变更 (PRIORITY_CHANGED)、分类变更 (CATEGORY_CHANGED)、评论 (COMMENTED)、升级 (ESCALATED)、
 * 重新打开 (REOPENED)、关闭 (CLOSED)。fromValue/toValue 记录变更前后值, actionTime 为动作发生时间。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_ticket_history", schema = "scrm", indexes = {
        @Index(name = "idx_ticket_history_ticket", columnList = "ticket_id"),
        @Index(name = "idx_ticket_history_action", columnList = "action_type"),
        @Index(name = "idx_ticket_history_time", columnList = "action_time")
})
@Data
public class ScrmTicketHistoryEntity {

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

    /** 关联工单 ID */
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

/** 动作类型: CREATED / ASSIGNED / STATUS_CHANGED / PRIORITY_CHANGED / CATEGORY_CHANGED / COMMENTED / ESCALATED /
         * REOPENED / CLOSED */
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    /** 变更前值 (可空) */
    @Column(name = "from_value", length = 200)
    private String fromValue;

    /** 变更后值 (可空) */
    @Column(name = "to_value", length = 200)
    private String toValue;

    /** 操作人 ID */
    @Column(name = "operator_id", nullable = false, length = 100)
    private String operatorId;

    /** 操作人名称 (可空) */
    @Column(name = "operator_name", length = 100)
    private String operatorName;

    /** 动作发生时间 */
    @Column(name = "action_time", nullable = false)
    private LocalDateTime actionTime;

    /** 备注 (可空) */
    @Column(name = "note", length = 500)
    private String note;
}
