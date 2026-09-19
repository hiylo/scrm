/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderLogEntity.java
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
 * SCRM 工单日志实体。
 * <p>
 * 记录工单生命周期内的全部动作轨迹: 状态变更 (STATUS_CHANGE)、分配 (ASSIGNMENT)、评论 (COMMENT)、
 * 升级 (ESCALATION)、解决 (RESOLUTION)、SLA 违规 (SLA_BREACH)、备注 (NOTE)、附件 (ATTACHMENT)、
 * 客户响应 (CUSTOMER_RESPONSE)、内部 (INTERNAL)。from/to 字段记录前后值, fieldChanges 以 JSON 数组形式
 * 记录字段级变更明细。支持内部/客户可见区分, 计费时长与花费时间统计。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_work_order_log", schema = "scrm", indexes = {
        @Index(name = "idx_work_order_log_order", columnList = "order_id"),
        @Index(name = "idx_work_order_log_type", columnList = "log_type"),
        @Index(name = "idx_work_order_log_operator", columnList = "operator_id"),
        @Index(name = "idx_work_order_log_time", columnList = "create_time")
})
@Data
public class ScrmWorkOrderLogEntity {

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

    /** 关联工单 ID */
    @Column(name = "order_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 工单编号 (冗余, 便于查询展示) */
    @Column(name = "order_no", length = 100)
    private String orderNo;

    /* 日志类型: /*
    /* STATUS_CHANGE/ASSIGNMENT/COMMENT/ESCALATION/RESOLUTION/SLA_BREACH/NOTE/ATTACHMENT/CUSTOMER_RESPONSE/INTERNAL */
    /* */ @Column(name = "log_type", nullable = false, length = 30)    private String logType;

    /** 日志标题 (可空) */
    @Column(name = "log_title", length = 200)
    private String logTitle;

    /** 日志内容 (可空) */
    @Column(name = "log_content", columnDefinition = "TEXT")
    private String logContent;

    /** 变更前状态 (可空) */
    @Column(name = "from_status", length = 20)
    private String fromStatus;

    /** 变更后状态 (可空) */
    @Column(name = "to_status", length = 20)
    private String toStatus;

    /** 变更前处理人 (可空) */
    @Column(name = "from_assignee", length = 100)
    private String fromAssignee;

    /** 变更后处理人 (可空) */
    @Column(name = "to_assignee", length = 100)
    private String toAssignee;

    /** 变更前部门 (可空) */
    @Column(name = "from_department", length = 100)
    private String fromDepartment;

    /** 变更后部门 (可空) */
    @Column(name = "to_department", length = 100)
    private String toDepartment;

    /** 字段变更 JSON: [{field,oldValue,newValue}] (可空) */
    @Column(name = "field_changes", length = 2000)
    private String fieldChanges;

    /** 内部日志 */
    @Column(name = "is_internal", nullable = false)
    private Boolean isInternal;

    /** 客户可见 */
    @Column(name = "is_customer_visible", nullable = false)
    private Boolean isCustomerVisible;

    /** 操作人 ID (可空) */
    @Column(name = "operator_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    /** 操作人名称 (可空) */
    @Column(name = "operator_name", length = 100)
    private String operatorName;

    /** 操作者类型: AGENT/CUSTOMER/SYSTEM/MANAGER */
    @Column(name = "operator_type", length = 30)
    private String operatorType;

    /** 附件 (可空, JSON 数组) */
    @Column(name = "attachments", length = 1000)
    private String attachments;

    /** 提醒用户 (可空, 逗号分隔) */
    @Column(name = "mentioned_users", length = 500)
    private String mentionedUsers;

    /** 提醒部门 (可空, 逗号分隔) */
    @Column(name = "mentioned_departments", length = 500)
    private String mentionedDepartments;

    /** 花费时间分钟 */
    @Column(name = "time_spent_minutes")
    private Integer timeSpentMinutes;

    /** 计费时间 */
    @Column(name = "billable_time")
    private Integer billableTime;

    /** IP 地址 (可空) */
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    /** User-Agent (可空) */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
