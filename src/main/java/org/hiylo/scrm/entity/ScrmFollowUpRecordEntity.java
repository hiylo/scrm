/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFollowUpRecordEntity.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 跟进记录实体。
 * <p>
 * 一次接触记录, 可关联跟进任务 (taskId 非空) 或为独立跟进记录 (taskId 为空)。
 * contactMethod 描述接触方式, contactResult 描述接触结果, content 记录跟进详情,
 * sentiment 标记客户情绪, nextAction 与 nextFollowUpAt 用于后续行动规划。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_follow_up_record", schema = "scrm", indexes = {
        @Index(name = "idx_follow_up_record_customer", columnList = "customer_id"),
        @Index(name = "idx_follow_up_record_task", columnList = "task_id"),
        @Index(name = "idx_follow_up_record_recorded_at", columnList = "recorded_at")
})
@Data
public class ScrmFollowUpRecordEntity {

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

    /** 关联任务 ID (可空, 空表示独立跟进记录) */
    @Column(name = "task_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 冗余客户昵称 */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 接触方式: CALL/MESSAGE/VISIT/EMAIL/MEETING/OTHER */
    @Column(name = "contact_method", nullable = false, length = 30)
    private String contactMethod;

    /** 接触结果: REACHED/NO_ANSWER/LEFT_MESSAGE/FAILED */
    @Column(name = "contact_result", nullable = false, length = 30)
    private String contactResult;

    /** 跟进内容 */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 通话/会话时长 (分钟) */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /** 客户情绪: POSITIVE/NEUTRAL/NEGATIVE */
    @Column(name = "sentiment", length = 20)
    private String sentiment;

    /** 后续行动计划 */
    @Column(name = "next_action", length = 500)
    private String nextAction;

    /** 下次跟进时间 */
    @Column(name = "next_follow_up_at")
    private LocalDateTime nextFollowUpAt;

    /** 记录人 userId */
    @Column(name = "recorded_by", nullable = false, length = 100)
    private String recordedBy;

    /** 跟进发生时间 */
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
}
