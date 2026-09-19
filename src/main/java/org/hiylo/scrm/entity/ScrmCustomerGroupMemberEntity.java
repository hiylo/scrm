/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerGroupMemberEntity.java
 * Date : 2026/07/27 02:41:22
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.hibernate.annotations.GenericGenerator;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户-分组关联实体（中间表）。
 * <p>
 * 多对多关联表：一个客户可归属于多个分组，一个分组可包含多个客户。
 * 通过 {@code (group_id, customer_id)} 唯一约束保证幂等加入，避免重复关联。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_group_member", schema = "scrm", indexes = {
        @Index(name = "idx_group_member_group", columnList = "group_id"),
        @Index(name = "idx_group_member_customer", columnList = "customer_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_group_member_group_customer", columnNames = {"group_id", "customer_id"})
})
@Data
public class ScrmCustomerGroupMemberEntity {

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

    /** 分组 ID（引用 scrm_customer_group.id） */
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    /** 客户 ID（引用 scrm_customer.id） */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;
}
