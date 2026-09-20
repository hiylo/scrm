/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInheritanceItemEntity.java
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
 * 离职继承明细实体。
 * <p>
 * 逐条记录一个离职继承任务处理的资产明细, 涵盖三类资产:
 * <ul>
 *   <li>{@code CUSTOMER}: 客户 (item_id 为客户 ID)</li>
 *   <li>{@code GROUP}: 客户群 (item_id 为群 ID)</li>
 *   <li>{@code CONVERSATION}: 会话 (item_id 为会话 ID)</li>
 * </ul>
 * 明细状态 {@code status} 标识每条转移结果:
 * <ul>
 *   <li>{@code PENDING}: 待处理</li>
 *   <li>{@code SUCCESS}: 转移成功</li>
 *   <li>{@code FAILED}: 转移失败 (可重试)</li>
 *   <li>{@code SKIPPED}: 跳过 (如对象已不存在)</li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_inheritance_item", schema = "scrm", indexes = {
        @Index(name = "idx_inheritance_item_task", columnList = "task_id"),
        @Index(name = "idx_inheritance_item_type", columnList = "item_type"),
        @Index(name = "idx_inheritance_item_status", columnList = "status")
})
@Data
public class ScrmInheritanceItemEntity {

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

    /** 任务 ID (引用 scrm_inheritance_task.id) */
    @Column(name = "task_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 明细类型: CUSTOMER/GROUP/CONVERSATION */
    @Column(name = "item_type", nullable = false, length = 20)
    private String itemType;

    /** 明细对象 ID (客户ID/群ID/会话ID) */
    @Column(name = "item_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemId;

    /** 明细标签 (客户昵称/群名, 便于展示) */
    @Column(name = "item_label", length = 200)
    private String itemLabel;

    /** 明细状态: PENDING/SUCCESS/FAILED/SKIPPED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 失败原因 (FAILED 状态时记录) */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** 处理时间 */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
