/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTicketEntity.java
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
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 客户工单实体。
 * <p>
 * 承载售后服务工单的核心数据: 工单编号、标题、描述、客户信息、分类、优先级、状态、来源、
 * 处理人与处理团队、关联订单与产品、SLA 到期时间、首次响应/解决/关闭时间、解决时长、
 * 满意度评价与标签。工单状态流转 OPEN → IN_PROGRESS → RESOLVED → CLOSED (可 REOPENED),
 * 亦可 CANCELLED。SLA 到期时间按优先级固定规则计算 (URGENT=4h/HIGH=8h/MEDIUM=24h/LOW=48h)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_ticket", schema = "scrm", indexes = {
        @Index(name = "idx_ticket_customer", columnList = "customer_id"),
        @Index(name = "idx_ticket_assignee", columnList = "assignee_id"),
        @Index(name = "idx_ticket_status", columnList = "status"),
        @Index(name = "idx_ticket_priority", columnList = "priority"),
        @Index(name = "idx_ticket_category", columnList = "category"),
        @Index(name = "idx_ticket_sla", columnList = "status,sla_due_at")
})
@Data
public class ScrmTicketEntity {

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

    /** 工单编号 (唯一, 年月日 + 序号) */
    @Column(name = "ticket_no", nullable = false, length = 50, unique = true)
    private String ticketNo;

    /** 工单标题 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 工单描述 (可空) */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** 客户名称 (可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 关联账号 ID (可空) */
    @Column(name = "account_id")
    private Long accountId;

    /** 工单类别: PRODUCT_ISSUE / SERVICE_COMPLAINT / REFUND / EXCHANGE / TECHNICAL / DELIVERY / BILLING / OTHER */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    /** 优先级: URGENT / HIGH / MEDIUM / LOW */
    @Column(name = "priority", nullable = false, length = 10)
    private String priority;

    /** 状态: OPEN / IN_PROGRESS / RESOLVED / CLOSED / REOPENED / CANCELLED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 来源: CUSTOMER / AGENT / SYSTEM / PHONE / EMAIL / CHAT */
    @Column(name = "source", nullable = false, length = 30)
    private String source;

    /** 处理人 ID (可空) */
    @Column(name = "assignee_id", length = 100)
    private String assigneeId;

    /** 处理人名称 (可空) */
    @Column(name = "assignee_name", length = 100)
    private String assigneeName;

    /** 处理团队 ID (可空) */
    @Column(name = "team_id", length = 100)
    private String teamId;

    /** 关联订单 ID (可空) */
    @Column(name = "related_order_id", length = 100)
    private String relatedOrderId;

    /** 关联产品 ID (可空) */
    @Column(name = "related_product_id", length = 100)
    private String relatedProductId;

    /** SLA 到期时间 (可空) */
    @Column(name = "sla_due_at")
    private LocalDateTime slaDueAt;

    /** 首次响应时间 (可空) */
    @Column(name = "first_response_at")
    private LocalDateTime firstResponseAt;

    /** 解决时间 (可空) */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** 关闭时间 (可空) */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** 解决时长 (分钟, 可空) */
    @Column(name = "resolution_time_minutes")
    private Integer resolutionTimeMinutes;

    /** 满意度评分 (1-5, 可空) */
    @Column(name = "satisfaction_score")
    private Integer satisfactionScore;

    /** 满意度评价内容 (可空) */
    @Column(name = "satisfaction_comment", length = 500)
    private String satisfactionComment;

    /** 标签 (可空, 逗号分隔) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
