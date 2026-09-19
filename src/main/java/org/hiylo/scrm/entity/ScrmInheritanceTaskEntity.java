/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInheritanceTaskEntity.java
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
 * 离职继承任务实体。
 * <p>
 * 记录一次员工离职继承操作的元信息: 离职人 ({@code fromUserId})、接收人
 * ({@code toUserId})、任务状态与统计计数。任务状态流转:
 * <ul>
 *   <li>{@code PENDING}: 已创建未启动</li>
 *   <li>{@code RUNNING}: 启动后处理中</li>
 *   <li>{@code COMPLETED}: 全部明细成功</li>
 *   <li>{@code FAILED}: 全部明细失败</li>
 *   <li>{@code PARTIAL}: 部分明细成功部分失败</li>
 * </ul>
 * 一个任务包含多条 {@link ScrmInheritanceItemEntity} 明细, 涵盖客户/群/会话三类资产。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_inheritance_task", schema = "scrm", indexes = {
        @Index(name = "idx_inheritance_task_from_user", columnList = "from_user_id"),
        @Index(name = "idx_inheritance_task_to_user", columnList = "to_user_id"),
        @Index(name = "idx_inheritance_task_status", columnList = "status")
})
@Data
public class ScrmInheritanceTaskEntity {

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

    /** 任务名称 (便于审计与展示) */
    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    /** 离职人 userId */
    @Column(name = "from_user_id", nullable = false, length = 100)
    private String fromUserId;

    /** 接收人 userId */
    @Column(name = "to_user_id", nullable = false, length = 100)
    private String toUserId;

    /** 指定平台类型 (可空, 空表示全平台) */
    @Column(name = "platform_type", length = 30)
    private String platformType;

    /** 任务状态: PENDING/RUNNING/COMPLETED/FAILED/PARTIAL */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 总明细数 */
    @Column(name = "total_items")
    private Integer totalItems;

    /** 成功明细数 */
    @Column(name = "success_items")
    private Integer successItems;

    /** 失败明细数 */
    @Column(name = "fail_items")
    private Integer failItems;

    /** 任务开始时间 */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 任务完成时间 */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 任务创建人 userId */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /** 任务备注 */
    @Column(name = "note", length = 500)
    private String note;
}
