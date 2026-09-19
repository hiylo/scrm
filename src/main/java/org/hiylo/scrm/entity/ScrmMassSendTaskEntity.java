/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMassSendTaskEntity.java
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
 * SCRM 群发任务实体。
 * <p>
 * 描述一次批量触达客户的群发任务, 支持按目标类型 (全量 / 分群 / 标签 / 指定列表) 筛选
 * 目标客户, 异步发送并跟踪发送结果 (总数 / 已发 / 成功 / 失败)。
 * 任务生命周期: DRAFT → PENDING → RUNNING → (PAUSED) → COMPLETED / FAILED。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_mass_send_task", schema = "scrm", indexes = {
        @Index(name = "idx_mass_send_task_status", columnList = "status"),
        @Index(name = "idx_mass_send_task_platform", columnList = "platform_type")
})
@Data
public class ScrmMassSendTaskEntity {

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

    /** 任务名称 */
    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    /** 平台类型 */
    @Column(name = "platform_type", nullable = false, length = 30)
    private String platformType;

    /** 关联消息模板 ID（可空） */
    @Column(name = "message_template_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long messageTemplateId;

    /** 群发内容文本 */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 目标类型: ALL(全量) / SEGMENT(分群) / TAG(按标签) / LIST(指定列表) */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    /** 目标筛选条件 JSON (targetType=TAG/SEGMENT/LIST 时使用) */
    @Column(name = "target_filter", columnDefinition = "TEXT")
    private String targetFilter;

    /** 发送账号 ID */
    @Column(name = "sender_account_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long senderAccountId;

    /** 任务状态: DRAFT / PENDING / RUNNING / PAUSED / COMPLETED / FAILED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 目标客户总数 */
    @Column(name = "total_count")
    private Integer totalCount;

    /** 已发送数 */
    @Column(name = "sent_count")
    private Integer sentCount;

    /** 发送成功数 */
    @Column(name = "success_count")
    private Integer successCount;

    /** 发送失败数 */
    @Column(name = "fail_count")
    private Integer failCount;

    /** 计划发送时间 */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    /** 实际开始发送时间 */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 发送完成时间 */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 创建人（可空） */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
