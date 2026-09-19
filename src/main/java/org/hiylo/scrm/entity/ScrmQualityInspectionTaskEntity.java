/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQualityInspectionTaskEntity.java
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
 * SCRM 质检任务实体。
 * <p>
 * 批量会话质检任务, 按范围 (全部/被质检人/账号/客户) 捞取会话并逐个质检,
 * 汇总已质检数、通过数、不通过数与平均分。任务状态流转 PENDING → RUNNING → COMPLETED/FAILED。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_quality_inspection_task", schema = "scrm", indexes = {
        @Index(name = "idx_qi_task_status", columnList = "status")
})
@Data
public class ScrmQualityInspectionTaskEntity {

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
        if (status == null || status.isBlank()) {
            status = "PENDING";
        }
        if (totalConversations == null) {
            totalConversations = 0;
        }
        if (inspectedCount == null) {
            inspectedCount = 0;
        }
        if (passedCount == null) {
            passedCount = 0;
        }
        if (failedCount == null) {
            failedCount = 0;
        }
        if (averageScore == null) {
            averageScore = 0.0;
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
    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    /** 质检范围: ALL/ASSIGNEE/ACCOUNT/CUSTOMER */
    @Column(name = "inspection_scope", nullable = false, length = 30)
    private String inspectionScope;

    /** 范围值 (assigneeId/accountId/customerId, ALL 时为空) */
    @Column(name = "scope_value", length = 500)
    private String scopeValue;

    /** 质检会话起始时间 */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /** 质检会话截止时间 */
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    /** 质检规则 ID 列表 JSON 数组 */
    @Column(name = "rule_ids", nullable = false, columnDefinition = "TEXT")
    private String ruleIds;

    /** 待质检会话数 */
    @Column(name = "total_conversations")
    private Integer totalConversations;

    /** 已质检数 */
    @Column(name = "inspected_count")
    private Integer inspectedCount;

    /** 通过数 */
    @Column(name = "passed_count")
    private Integer passedCount;

    /** 不通过数 */
    @Column(name = "failed_count")
    private Integer failedCount;

    /** 平均分 */
    @Column(name = "average_score")
    private Double averageScore;

    /** 状态: PENDING/RUNNING/COMPLETED/FAILED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 开始执行时间 */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 完成执行时间 */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
