/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderSlaEntity.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 工单 SLA 策略实体。
 * <p>
 * 定义工单 SLA 策略: 响应与解决时间阈值、工作时间与工作日配置、升级级别与违规动作、
 * 自动关闭与重开规则、奖惩金额、达标率与统计指标 (订单总数/违规数/达标数/平均响应与解决时间)。
 * 每个账号可配置多条策略, 其中 isDefault=true 的策略将作为新建工单的默认 SLA。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_work_order_sla", schema = "scrm", indexes = {
        @Index(name = "idx_work_order_sla_name", columnList = "policy_name", unique = true),
        @Index(name = "idx_work_order_sla_code", columnList = "policy_code", unique = true),
        @Index(name = "idx_work_order_sla_default", columnList = "is_default"),
        @Index(name = "idx_work_order_sla_enabled", columnList = "enabled")
})
@Data
public class ScrmWorkOrderSlaEntity {

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

    /** 策略名称 (唯一) */
    @Column(name = "policy_name", nullable = false, length = 100, unique = true)
    private String policyName;

    /** 策略编码 (唯一) */
    @Column(name = "policy_code", nullable = false, length = 50, unique = true)
    private String policyCode;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 适用工单类型 (可空) */
    @Column(name = "order_type", length = 50)
    private String orderType;

    /** 适用优先级 (可空) */
    @Column(name = "priority", length = 20)
    private String priority;

    /** 适用客群 (可空) */
    @Column(name = "customer_segment", length = 100)
    private String customerSegment;

    /** 响应时间分钟 */
    @Column(name = "response_time_minutes", nullable = false)
    private Integer responseTimeMinutes;

    /** 解决时间分钟 */
    @Column(name = "resolution_time_minutes", nullable = false)
    private Integer resolutionTimeMinutes;

    /** 响应时间小时 (可空) */
    @Column(name = "response_time_hours")
    private Integer responseTimeHours;

    /** 解决时间小时 (可空) */
    @Column(name = "resolution_time_hours")
    private Integer resolutionTimeHours;

    /** 仅工作时间 */
    @Column(name = "business_hours_only", nullable = false)
    private Boolean businessHoursOnly;

    /** 工作时间开始 (默认 09:00) */
    @Column(name = "business_hours_start", length = 10)
    private String businessHoursStart;

    /** 工作时间结束 (默认 18:00) */
    @Column(name = "business_hours_end", length = 10)
    private String businessHoursEnd;

    /** 工作日 (默认 MON-FRI) */
    @Column(name = "business_days", length = 50)
    private String businessDays;

    /** 时区 (默认 Asia/Shanghai) */
    @Column(name = "timezone", length = 50)
    private String timezone;

    /** 启用升级 */
    @Column(name = "escalation_enabled", nullable = false)
    private Boolean escalationEnabled;

    /** 升级级别 JSON: [{level,afterMinutes,notify,role}] (可空) */
    @Column(name = "escalation_levels", length = 2000)
    private String escalationLevels;

    /** 首次响应违规动作 (可空) */
    @Column(name = "first_response_breach_action", length = 200)
    private String firstResponseBreachAction;

    /** 解决违规动作 (可空) */
    @Column(name = "resolution_breach_action", length = 200)
    private String resolutionBreachAction;

    /** 违规前提醒分钟 (默认 30) */
    @Column(name = "warning_before_breach")
    private Integer warningBeforeBreach;

    /** 解决后自动关闭小时 (默认 72) */
    @Column(name = "auto_close_after_resolution")
    private Integer autoCloseAfterResolution;

    /** 允许重开 */
    @Column(name = "reopen_allowed", nullable = false)
    private Boolean reopenAllowed;

    /** 允许重开小时 (默认 168) */
    @Column(name = "reopen_within_hours")
    private Integer reopenWithinHours;

    /** 每次违规罚金 (默认 0) */
    @Column(name = "penalty_per_breach")
    private Double penaltyPerBreach;

    /** 每次达标奖励 (默认 0) */
    @Column(name = "credit_per_met")
    private Double creditPerMet;

    /** 目标达标率 % (默认 95) */
    @Column(name = "target_compliance_rate")
    private Double targetComplianceRate;

    /** 当前达标率 % (默认 0) */
    @Column(name = "current_compliance_rate")
    private Double currentComplianceRate;

    /** 总订单数 */
    @Column(name = "total_orders")
    private Integer totalOrders;

    /** 违规订单数 */
    @Column(name = "breached_orders")
    private Integer breachedOrders;

    /** 达标订单数 */
    @Column(name = "met_orders")
    private Integer metOrders;

    /** 平均响应时间分钟 */
    @Column(name = "avg_response_time")
    private Double avgResponseTime;

    /** 平均解决时间分钟 */
    @Column(name = "avg_resolution_time")
    private Double avgResolutionTime;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 是否默认策略 */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
