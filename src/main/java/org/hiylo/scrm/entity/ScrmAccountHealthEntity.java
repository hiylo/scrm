/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAccountHealthEntity.java
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
import jakarta.persistence.Version;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.hibernate.annotations.GenericGenerator;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 账号健康度检测记录实体。
 * <p>
 * 每次定时或手动健康度检测产生一条记录, 描述账号检测前后的登录态变化、
 * 检测结果与详情, 用于账号掉线告警与历史追溯。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_account_health", schema = "scrm", indexes = {
        @Index(name = "idx_health_account", columnList = "account_id"),
        @Index(name = "idx_health_checked_at", columnList = "checked_at"),
        @Index(name = "idx_health_result", columnList = "check_result")
})
@Data
public class ScrmAccountHealthEntity {

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

    /** 账号 ID（引用 scrm_account.id） */
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** 检测结果: HEALTHY / OFFLINE / FROZEN / UNKNOWN / ERROR */
    @Column(name = "check_result", nullable = false, length = 20)
    private String checkResult;

    /** 检测前登录态 */
    @Column(name = "previous_state", length = 20)
    private String previousState;

    /** 检测后登录态 */
    @Column(name = "current_state", length = 20)
    private String currentState;

    /** 检测详情（平台可用性 / 设备状态 / 异常原因等） */
    @Column(name = "detail", length = 4000)
    private String detail;

    /** 检测时间 */
    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;
}
