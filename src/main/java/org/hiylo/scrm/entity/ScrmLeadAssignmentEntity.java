/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadAssignmentEntity.java
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
 * 线索分配流水实体。
 * <p>
 * 记录每一次线索领取/分配/转移/回收的完整审计轨迹, 一条公海客户可对应多条流水
 * (回收后再次分配将产生新记录)。流水状态 {@code status} 与公海客户状态联动:
 * <ul>
 *   <li>{@code ACTIVE}: 当前生效的分配关系</li>
 *   <li>{@code RECALLED}: 已被回收到公海</li>
 *   <li>{@code TRANSFERRED}: 已转移给他人 (后续产生新 ACTIVE 流水)</li>
 *   <li>{@code CONVERTED}: 已转为正式客户, 公海关系结束</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_lead_assignment", schema = "scrm", indexes = {
        @Index(name = "idx_lead_assignment_customer", columnList = "public_sea_customer_id"),
        @Index(name = "idx_lead_assignment_assignee", columnList = "assigned_to"),
        @Index(name = "idx_lead_assignment_status", columnList = "status")
})
@Data
public class ScrmLeadAssignmentEntity {

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

    /** 公海客户 ID (引用 scrm_public_sea_customer.id) */
    @Column(name = "public_sea_customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long publicSeaCustomerId;

    /** 被分配人 userId */
    @Column(name = "assigned_to", nullable = false, length = 100)
    private String assignedTo;

    /** 分配人 userId (CLAIM 类型时与 assigned_to 相同) */
    @Column(name = "assigned_by", length = 100)
    private String assignedBy;

    /** 分配类型: CLAIM(领取)/ASSIGN(分配)/TRANSFER(转移) */
    @Column(name = "assignment_type", nullable = false, length = 20)
    private String assignmentType;

    /** 上一手归属人 userId (TRANSFER 时记录, 可空) */
    @Column(name = "previous_owner", length = 100)
    private String previousOwner;

    /** 分配状态: ACTIVE/RECALLED/TRANSFERRED/CONVERTED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 分配时间 */
    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    /** 回收时间 (RECALLED 状态时记录) */
    @Column(name = "recalled_at")
    private LocalDateTime recalledAt;

    /** 分配过期时间 (超时自动回收的阈值) */
    @Column(name = "expire_at")
    private LocalDateTime expireAt;

    /** 分配备注 (可空) */
    @Column(name = "note", length = 500)
    private String note;
}
