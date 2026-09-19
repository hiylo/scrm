/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCouponUsageLogEntity.java
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
 * SCRM 优惠券使用日志实体。
 * <p>
 * 记录优惠券生命周期中关键动作 (ISSUE 发放 / CLAIM 领取 / USE 使用 / RETURN 退还 / EXPIRE 过期)
 * 的执行轨迹, 包括操作人、订单金额、抵扣金额与详情, 用于优惠券使用追踪与统计聚合。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_coupon_usage_log", schema = "scrm", indexes = {
        @Index(name = "idx_coupon_log_coupon", columnList = "coupon_id"),
        @Index(name = "idx_coupon_log_template", columnList = "template_id"),
        @Index(name = "idx_coupon_log_action", columnList = "action_type"),
        @Index(name = "idx_coupon_log_time", columnList = "action_time")
})
@Data
public class ScrmCouponUsageLogEntity {

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

    /** 关联优惠券 ID */
    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    /** 关联模板 ID */
    @Column(name = "template_id", nullable = false)
    private Long templateId;

    /** 客户 ID (可空) */
    @Column(name = "customer_id")
    private Long customerId;

    /** 客户名称 (可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 操作类型: ISSUE / CLAIM / USE / RETURN / EXPIRE */
    @Column(name = "action_type", nullable = false, length = 20)
    private String actionType;

    /** 操作时间 */
    @Column(name = "action_time", nullable = false)
    private LocalDateTime actionTime;

    /** 操作人 ID (可空) */
    @Column(name = "operator_id", length = 100)
    private String operatorId;

    /** 操作人名称 (可空) */
    @Column(name = "operator_name", length = 100)
    private String operatorName;

    /** 订单金额 (USE 时携带, 可空) */
    @Column(name = "order_amount")
    private Double orderAmount;

    /** 抵扣金额 (USE 时携带, 可空) */
    @Column(name = "discount_amount")
    private Double discountAmount;

    /** 详情 (可空) */
    @Column(name = "detail", length = 500)
    private String detail;
}
