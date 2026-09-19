/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetExpenseEntity.java
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
 * SCRM 营销预算支出实体。
 * <p>
 * 记录营销预算的每笔支出, 关联预算方案、分配、营销活动, 支持支出类型
 * (AD_SPEND/CONTENT_PRODUCTION/EVENT/TOOL/SALARY/REIMBURSEMENT/PAYMENT/OTHER)、付款方式
 * 与付款状态、审批信息 (审批人/时间/备注/状态)、供应商与发票信息、附件与标签。
 * 状态流转: PENDING (待审批) → APPROVED (已通过) / REJECTED (已驳回) / PAID (已付款)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_budget_expense", schema = "scrm", indexes = {
        @Index(name = "idx_budget_expense_no", columnList = "expense_no"),
        @Index(name = "idx_budget_expense_plan", columnList = "plan_id"),
        @Index(name = "idx_budget_expense_allocation", columnList = "allocation_id"),
        @Index(name = "idx_budget_expense_campaign", columnList = "campaign_id"),
        @Index(name = "idx_budget_expense_type", columnList = "expense_type"),
        @Index(name = "idx_budget_expense_status", columnList = "status"),
        @Index(name = "idx_budget_expense_date", columnList = "expense_date")
})
@Data
public class ScrmBudgetExpenseEntity {

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
        if (amount == null) {
            amount = 0d;
        }
        if (currency == null) {
            currency = "CNY";
        }
        if (paymentStatus == null) {
            paymentStatus = "PENDING";
        }
        if (status == null) {
            status = "PENDING";
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

    /** 支出编号 (唯一, 格式 EXP+年月日+序号) */
    @Column(name = "expense_no", nullable = false, length = 100, unique = true)
    private String expenseNo;

    /** 预算方案 ID (可空) */
    @Column(name = "plan_id")
    private Long planId;

    /** 预算方案名称 (冗余便于展示, 可空) */
    @Column(name = "plan_name", length = 200)
    private String planName;

    /** 预算分配 ID (可空) */
    @Column(name = "allocation_id")
    private Long allocationId;

    /** 预算分配名称 (冗余便于展示, 可空) */
    @Column(name = "allocation_name", length = 200)
    private String allocationName;

    /** 支出类型: AD_SPEND/CONTENT_PRODUCTION/EVENT/TOOL/SALARY/REIMBURSEMENT/PAYMENT/OTHER */
    @Column(name = "expense_type", nullable = false, length = 30)
    private String expenseType;

    /** 关联营销活动 ID (可空) */
    @Column(name = "campaign_id")
    private Long campaignId;

    /** 关联营销活动名称 (可空) */
    @Column(name = "campaign_name", length = 200)
    private String campaignName;

    /** 支出日期 */
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    /** 支出金额 */
    @Column(name = "amount", nullable = false)
    private Double amount;

    /** 币种 (默认 CNY) */
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 供应商 (可空) */
    @Column(name = "vendor", length = 200)
    private String vendor;

    /** 发票号 (可空) */
    @Column(name = "invoice_no", length = 100)
    private String invoiceNo;

    /** 付款方式: BANK_TRANSFER/ALIPAY/WECHAT/CASH/CARD/OTHER (可空) */
    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    /** 付款状态: PENDING/PAID/CANCELLED */
    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus;

    /** 付款时间 (可空) */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** 凭证 URL (可空) */
    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    /** 附件 JSON (可空) */
    @Column(name = "attachments", length = 1000)
    private String attachments;

    /** 状态: PENDING/APPROVED/REJECTED/PAID */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 审批人 (可空) */
    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    /** 审批时间 (可空) */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 审批备注 (可空) */
    @Column(name = "approver_comment", length = 500)
    private String approverComment;

    /** 部门 ID (可空) */
    @Column(name = "department_id", length = 100)
    private String departmentId;

    /** 部门名称 (可空) */
    @Column(name = "department_name", length = 200)
    private String departmentName;

    /** 申请人 ID (可空) */
    @Column(name = "requester_id", length = 100)
    private String requesterId;

    /** 申请人名称 (可空) */
    @Column(name = "requester_name", length = 100)
    private String requesterName;

    /** 标签 (可空, 逗号分隔) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
