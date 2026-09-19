/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderEntity.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 工单管理实体。
 * <p>
 * 承载工单全生命周期数据: 工单编号、标题、描述、类型与分类、优先级、状态、客户与联系人信息、
 * 产品与合同关联、来源与渠道、处理人与处理部门、各阶段时间戳 (分配/接受/开始/解决/关闭/最后响应)、
 * 响应与解决时长、SLA 策略与截止时间及达标标记、满意度、解决方案与根本原因、重复/升级/关联工单、
 * 标签与附件、内部备注、跟进信息等。状态流转 OPEN → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED,
 * 亦可 PENDING_CUSTOMER / CANCELLED / REOPENED。SLA 截止时间按优先级与 SLA 策略计算。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_work_order", schema = "scrm", indexes = {
        @Index(name = "idx_work_order_customer", columnList = "customer_id"),
        @Index(name = "idx_work_order_assignee", columnList = "assigned_to_id"),
        @Index(name = "idx_work_order_status", columnList = "order_status"),
        @Index(name = "idx_work_order_priority", columnList = "priority"),
        @Index(name = "idx_work_order_type", columnList = "order_type"),
        @Index(name = "idx_work_order_sla", columnList = "sla_breached,sla_resolution_due"),
        @Index(name = "idx_work_order_no", columnList = "order_no", unique = true)
})
@Data
public class ScrmWorkOrderEntity {

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

    /** 工单编号 (唯一) */
    @Column(name = "order_no", nullable = false, length = 100, unique = true)
    private String orderNo;

    /** 标题 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 描述 (可空) */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 工单类型: COMPLAINT/CONSULTATION/MAINTENANCE/INSTALLATION/REPAIR/SERVICE_REQUEST/TECH_SUPPORT/BILLING/RETURN/EXCHANGE/FEEDBACK/OTHER */
    @Column(name = "order_type", nullable = false, length = 50)
    private String orderType;

    /** 工单分类 (可空) */
    @Column(name = "order_category", length = 100)
    private String orderCategory;

    /** 优先级: URGENT/HIGH/NORMAL/LOW */
    @Column(name = "priority", nullable = false, length = 20)
    private String priority;

    /** 工单状态: OPEN/ASSIGNED/IN_PROGRESS/PENDING_CUSTOMER/RESOLVED/CLOSED/CANCELLED/REOPENED */
    @Column(name = "order_status", nullable = false, length = 20)
    private String orderStatus;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 客户电话 (可空) */
    @Column(name = "customer_phone", length = 50)
    private String customerPhone;

    /** 客户邮箱 (可空) */
    @Column(name = "customer_email", length = 100)
    private String customerEmail;

    /** 联系人 (可空) */
    @Column(name = "contact_person", length = 100)
    private String contactPerson;

    /** 联系人电话 (可空) */
    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    /** 联系人邮箱 (可空) */
    @Column(name = "contact_email", length = 100)
    private String contactEmail;

    /** 产品 ID (可空) */
    @Column(name = "product_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long productId;

    /** 产品名称 (可空) */
    @Column(name = "product_name", length = 200)
    private String productName;

    /** 产品分类 (可空) */
    @Column(name = "product_category", length = 100)
    private String productCategory;

    /** 序列号 (可空) */
    @Column(name = "serial_number", length = 200)
    private String serialNumber;

    /** 关联合同 ID (可空) */
    @Column(name = "contract_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 关联合同编号 (可空) */
    @Column(name = "contract_no", length = 100)
    private String contractNo;

    /** 关联活动 ID (可空) */
    @Column(name = "campaign_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long campaignId;

    /** 来源: PHONE/EMAIL/WEB/APP/WECHAT/WALK_IN/REFERRAL/SYSTEM/OTHER */
    @Column(name = "source", nullable = false, length = 50)
    private String source;

    /** 渠道 (可空) */
    @Column(name = "channel", length = 50)
    private String channel;

    /** 处理人 (可空) */
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    /** 处理人 ID (可空) */
    @Column(name = "assigned_to_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long assignedToId;

    /** 处理部门 (可空) */
    @Column(name = "assigned_department", length = 100)
    private String assignedDepartment;

    /** 分配时间 (可空) */
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /** 接受时间 (可空) */
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    /** 开始处理时间 (可空) */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 解决时间 (可空) */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** 关闭时间 (可空) */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** 最后响应时间 (可空) */
    @Column(name = "last_response_at")
    private LocalDateTime lastResponseAt;

    /** 响应时长分钟 (可空) */
    @Column(name = "response_time_minutes")
    private Integer responseTimeMinutes;

    /** 解决时长分钟 (可空) */
    @Column(name = "resolution_time_minutes")
    private Integer resolutionTimeMinutes;

    /** SLA 策略 (可空) */
    @Column(name = "sla_policy", length = 100)
    private String slaPolicy;

    /** SLA 响应截止 (可空) */
    @Column(name = "sla_response_due")
    private LocalDateTime slaResponseDue;

    /** SLA 解决截止 (可空) */
    @Column(name = "sla_resolution_due")
    private LocalDateTime slaResolutionDue;

    /** SLA 响应达标 (可空) */
    @Column(name = "sla_response_met")
    private Boolean slaResponseMet;

    /** SLA 解决达标 (可空) */
    @Column(name = "sla_resolution_met")
    private Boolean slaResolutionMet;

    /** SLA 违规 */
    @Column(name = "sla_breached", nullable = false)
    private Boolean slaBreached;

    /** 满意度评分 1-5 (可空) */
    @Column(name = "satisfaction_score")
    private Integer satisfactionScore;

    /** 满意度评价 (可空) */
    @Column(name = "satisfaction_comment", length = 500)
    private String satisfactionComment;

    /** 解决方案 (可空) */
    @Column(name = "resolution", length = 2000)
    private String resolution;

    /** 解决编码 (可空) */
    @Column(name = "resolution_code", length = 50)
    private String resolutionCode;

    /** 根本原因 (可空) */
    @Column(name = "root_cause", length = 500)
    private String rootCause;

    /** 重复工单 */
    @Column(name = "is_repeated", nullable = false)
    private Boolean isRepeated;

    /** 关联工单 (可空, 逗号分隔) */
    @Column(name = "related_order_ids", length = 500)
    private String relatedOrderIds;

    /** 是否升级 */
    @Column(name = "escalated", nullable = false)
    private Boolean escalated;

    /** 升级到 (可空) */
    @Column(name = "escalated_to", length = 100)
    private String escalatedTo;

    /** 升级时间 (可空) */
    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    /** 升级原因 (可空) */
    @Column(name = "escalation_reason", length = 500)
    private String escalationReason;

    /** 标签 (可空, 逗号分隔) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 附件 (可空, JSON 数组) */
    @Column(name = "attachments", length = 1000)
    private String attachments;

    /** 内部备注 (可空) */
    @Column(name = "internal_notes", length = 1000)
    private String internalNotes;

    /** 期望解决日期 (可空) */
    @Column(name = "expected_resolution_date")
    private LocalDate expectedResolutionDate;

    /** 实际解决日期 (可空) */
    @Column(name = "actual_resolution_date")
    private LocalDate actualResolutionDate;

    /** 解决截止日期 (可空) */
    @Column(name = "resolution_deadline")
    private LocalDate resolutionDeadline;

    /** 是否紧急 */
    @Column(name = "is_urgent", nullable = false)
    private Boolean isUrgent;

    /** 是否 VIP 客户 */
    @Column(name = "is_vip_customer", nullable = false)
    private Boolean isVipCustomer;

    /** 是否需要跟进 */
    @Column(name = "follow_up_required", nullable = false)
    private Boolean followUpRequired;

    /** 跟进日期 (可空) */
    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    /** 跟进人 (可空) */
    @Column(name = "follow_up_by", length = 100)
    private String followUpBy;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
