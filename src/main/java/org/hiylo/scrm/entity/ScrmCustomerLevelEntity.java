/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLevelEntity.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 客户等级实体。
 * <p>
 * 定义客户分级体系中的等级 (如 VIP / 高级会员 / 普通会员 / 潜在客户), 包含等级编码、
 * 排序 (数字越大等级越高)、等级权益 (JSON)、升降级阈值、有效天数与默认/启用状态。
 * 账号下等级编码 (level_code) 全局唯一, 等级排序 (level_order) 用于排序展示。
 * </p>
 * <p>
 * 默认等级 (is_default=true) 用于新客户入等级时自动赋予。等级可被禁用 (enabled=false)
 * 但保留历史关联, 不影响已有客户等级记录。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_level", schema = "scrm", indexes = {
        @Index(name = "idx_customer_level_default", columnList = "is_default"),
        @Index(name = "idx_customer_level_enabled", columnList = "enabled"),
        @Index(name = "idx_customer_level_order", columnList = "level_order")
})
@Data
public class ScrmCustomerLevelEntity {

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

    /** 等级名称 (如 VIP / 高级会员 / 普通会员 / 潜在客户) */
    @Column(name = "level_name", nullable = false, length = 100)
    private String levelName;

    /** 等级编码 (全局唯一, 如 VIP / SENIOR / NORMAL / POTENTIAL) */
    @Column(name = "level_code", nullable = false, length = 50)
    private String levelCode;

    /** 等级排序 (数字越大等级越高) */
    @Column(name = "level_order", nullable = false)
    private Integer levelOrder;

    /** 等级描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 等级颜色标识 (前端展示用, 如 #FFD700) */
    @Column(name = "color", length = 20)
    private String color;

    /** 等级图标 (前端展示用, 如 icon-vip) */
    @Column(name = "icon", length = 100)
    private String icon;

    /** 等级权益 JSON 列表 (如 [{"benefit":"专属客服", "value":"7x24"}]) */
    @Column(name = "benefits", columnDefinition = "TEXT")
    private String benefits;

    /** 升级阈值 (如累计消费金额达到此值可升级, 由规则引擎评估) */
    @Column(name = "upgrade_threshold")
    private Double upgradeThreshold;

    /** 降级阈值 (如累计消费金额低于此值降级, 由规则引擎评估) */
    @Column(name = "downgrade_threshold")
    private Double downgradeThreshold;

    /** 等级有效天数 (null 表示永久; 设置则到期后自动降级或重新评估) */
    @Column(name = "validity_days")
    private Integer validityDays;

    /** 是否默认等级 (新客户入等级时自动赋予, 账号下仅可有一个默认等级) */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    /** 是否启用 (禁用后不可分配, 但保留已有客户等级关联) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;
}
