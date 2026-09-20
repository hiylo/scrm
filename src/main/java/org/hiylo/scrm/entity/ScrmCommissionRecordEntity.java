/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionRecordEntity.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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
 * SCRM 销售佣金记录实体。
 * <p>
 * 一笔佣金计算的结果, 关联方案、规则、销售人员、团队与订单, 记录订单金额/利润/日期、产品分类、客户类型、
 * 佣金计算基础/比例/金额/奖金/扣减/最终佣金、计算详情 (JSON: [{rule,condition,rate,amount}])、
 * 状态 (CALCULATED/PENDING_APPROVAL/APPROVED/REJECTED/PAID/CLAWBACK/ADJUSTED)、所属周期、
 * 发放日期、审批信息、发放信息 (实发金额/税额)、扣减说明、追回信息 (追回金额/原因/时间)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_commission_record", schema = "scrm", indexes = {
        @Index(name = "idx_commission_record_no", columnList = "record_no"),
        @Index(name = "idx_commission_record_plan", columnList = "plan_id"),
        @Index(name = "idx_commission_record_sales", columnList = "sales_person_id"),
        @Index(name = "idx_commission_record_status", columnList = "status"),
        @Index(name = "idx_commission_record_period", columnList = "period"),
        @Index(name = "idx_commission_record_order", columnList = "order_id")
})
@Data
public class ScrmCommissionRecordEntity {

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
        if (orderAmount == null) {
            orderAmount = 0d;
        }
        if (orderProfit == null) {
            orderProfit = 0d;
        }
        if (commissionBasis == null) {
            commissionBasis = 0d;
        }
        if (commissionRate == null) {
            commissionRate = 0d;
        }
        if (commissionAmount == null) {
            commissionAmount = 0d;
        }
        if (bonusAmount == null) {
            bonusAmount = 0d;
        }
        if (deductionAmount == null) {
            deductionAmount = 0d;
        }
        if (finalCommission == null) {
            finalCommission = 0d;
        }
        if (status == null) {
            status = "CALCULATED";
        }
        if (paidAmount == null) {
            paidAmount = 0d;
        }
        if (taxAmount == null) {
            taxAmount = 0d;
        }
        if (clawbackAmount == null) {
            clawbackAmount = 0d;
        }
        if (calculatedAt == null) {
            calculatedAt = now;
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

    /** 佣金编号 (唯一, 格式 COMM+年月+序号) */
    @Column(name = "record_no", nullable = false, length = 100, unique = true)
    private String recordNo;

    /** 方案 ID */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** 方案名称 (冗余便于展示) */
    @Column(name = "plan_name", length = 200)
    private String planName;

    /** 规则 ID (可空) */
    @Column(name = "rule_id")
    private Long ruleId;

    /** 规则名称 (可空) */
    @Column(name = "rule_name", length = 200)
    private String ruleName;

    /** 销售人员 ID */
    @Column(name = "sales_person_id", nullable = false, length = 100)
    private String salesPersonId;

    /** 销售人员名称 */
    @Column(name = "sales_person_name", length = 200)
    private String salesPersonName;

    /** 团队 ID (可空) */
    @Column(name = "team_id", length = 100)
    private String teamId;

    /** 团队名称 (可空) */
    @Column(name = "team_name", length = 200)
    private String teamName;

    /** 关联订单 ID (可空) */
    @Column(name = "order_id", length = 100)
    private String orderId;

    /** 订单金额 */
    @Column(name = "order_amount")
    private Double orderAmount;

    /** 订单利润 */
    @Column(name = "order_profit")
    private Double orderProfit;

    /** 订单日期 (可空) */
    @Column(name = "order_date")
    private LocalDate orderDate;

    /** 产品分类 (可空) */
    @Column(name = "product_category", length = 100)
    private String productCategory;

    /** 客户类型 (可空) */
    @Column(name = "customer_type", length = 50)
    private String customerType;

    /** 佣金计算基础 */
    @Column(name = "commission_basis")
    private Double commissionBasis;

    /** 佣金比例 */
    @Column(name = "commission_rate")
    private Double commissionRate;

    /** 佣金金额 */
    @Column(name = "commission_amount", nullable = false)
    private Double commissionAmount;

    /** 奖金 */
    @Column(name = "bonus_amount")
    private Double bonusAmount;

    /** 扣减 */
    @Column(name = "deduction_amount")
    private Double deductionAmount;

    /** 最终佣金 */
    @Column(name = "final_commission", nullable = false)
    private Double finalCommission;

    /** 计算详情 JSON (可空): [{rule,condition,rate,amount}] */
    @Column(name = "calculation_details", columnDefinition = "TEXT")
    private String calculationDetails;

    /** 状态: CALCULATED/PENDING_APPROVAL/APPROVED/REJECTED/PAID/CLAWBACK/ADJUSTED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 所属周期 (可空, 格式 yyyy-MM) */
    @Column(name = "period", length = 20)
    private String period;

    /** 发放日期 (可空) */
    @Column(name = "payout_date")
    private LocalDate payoutDate;

    /** 审批人 (可空) */
    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    /** 审批时间 (可空) */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 审批备注 (可空) */
    @Column(name = "approval_note", length = 500)
    private String approvalNote;

    /** 发放时间 (可空) */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** 实发金额 */
    @Column(name = "paid_amount")
    private Double paidAmount;

    /** 税额 */
    @Column(name = "tax_amount")
    private Double taxAmount;

    /** 扣减说明 (可空) */
    @Column(name = "deduction_note", length = 500)
    private String deductionNote;

    /** 追回金额 */
    @Column(name = "clawback_amount")
    private Double clawbackAmount;

    /** 追回原因 (可空) */
    @Column(name = "clawback_reason", length = 500)
    private String clawbackReason;

    /** 追回时间 (可空) */
    @Column(name = "clawback_at")
    private LocalDateTime clawbackAt;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;

    /** 计算时间 */
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
