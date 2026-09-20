/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractPaymentEntity.java
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
 * SCRM 合同付款实体。
 * <p>
 * 承载合同付款计划的完整信息: 付款编号 (唯一)、付款类型 (预付款/里程碑/周期/尾款/保证金/退款/违约金)、
 * 付款状态 (待付/到期/逾期/部分/已付/取消)、计划与实际金额、计划与实际付款日期、逾期天数、
 * 付款方式、银行账户、交易号、发票信息、里程碑完成率、提醒配置, 以及确认信息。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_contract_payment", schema = "scrm", indexes = {
        @Index(name = "idx_contract_payment_contract", columnList = "contract_id"),
        @Index(name = "idx_contract_payment_no", columnList = "payment_no", unique = true),
        @Index(name = "idx_contract_payment_status", columnList = "payment_status"),
        @Index(name = "idx_contract_payment_planned_date", columnList = "planned_date"),
        @Index(name = "idx_contract_payment_due", columnList = "payment_status,planned_date")
})
@Data
public class ScrmContractPaymentEntity {

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

    /** 合同 ID */
    @Column(name = "contract_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 合同编号 (可空, 冗余字段便于查询) */
    @Column(name = "contract_no", length = 100)
    private String contractNo;

    /** 付款编号 (唯一) */
    @Column(name = "payment_no", nullable = false, length = 100, unique = true)
    private String paymentNo;

    /** 付款名称 (可空) */
    @Column(name = "payment_name", length = 200)
    private String paymentName;

    /** 付款类型: ADVANCE / MILESTONE / PERIODIC / FINAL / DEPOSIT / REFUND / PENALTY */
    @Column(name = "payment_type", nullable = false, length = 30)
    private String paymentType;

    /** 付款状态: PENDING / DUE / OVERDUE / PARTIAL / PAID / CANCELLED (默认 PENDING) */
    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus;

    /** 计划金额 (默认 0) */
    @Column(name = "planned_amount", nullable = false)
    private Double plannedAmount;

    /** 已付金额 (默认 0) */
    @Column(name = "paid_amount")
    private Double paidAmount;

    /** 未付金额 (默认 0) */
    @Column(name = "unpaid_amount")
    private Double unpaidAmount;

    /** 币种 (默认 CNY) */
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    /** 税率 (默认 0) */
    @Column(name = "tax_rate")
    private Double taxRate;

    /** 税额 (默认 0) */
    @Column(name = "tax_amount")
    private Double taxAmount;

    /** 计划付款日期 */
    @Column(name = "planned_date", nullable = false)
    private LocalDate plannedDate;

    /** 实际付款日期 (可空) */
    @Column(name = "actual_date")
    private LocalDate actualDate;

    /** 到期日 (可空) */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** 逾期天数 (默认 0) */
    @Column(name = "overdue_days")
    private Integer overdueDays;

    /** 付款方式: BANK_TRANSFER / CHECK / CASH / CREDIT_CARD / ALIPAY / WECHAT / OTHER (可空) */
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    /** 银行账户 (可空) */
    @Column(name = "bank_account", length = 200)
    private String bankAccount;

    /** 交易号 (可空) */
    @Column(name = "transaction_no", length = 200)
    private String transactionNo;

    /** 发票号 (可空) */
    @Column(name = "invoice_no", length = 100)
    private String invoiceNo;

    /** 是否已开票 (默认 FALSE) */
    @Column(name = "invoice_issued", nullable = false)
    private Boolean invoiceIssued;

    /** 发票日期 (可空) */
    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    /** 里程碑 (可空) */
    @Column(name = "milestone", length = 200)
    private String milestone;

    /** 里程碑描述 (可空) */
    @Column(name = "milestone_description", length = 500)
    private String milestoneDescription;

    /** 完成率 (默认 0) */
    @Column(name = "completion_rate")
    private Double completionRate;

    /** 是否已发送提醒 (默认 FALSE) */
    @Column(name = "reminder_sent", nullable = false)
    private Boolean reminderSent;

    /** 提醒日期 (可空) */
    @Column(name = "reminder_date")
    private LocalDate reminderDate;

    /** 提醒次数 (默认 0) */
    @Column(name = "reminder_count")
    private Integer reminderCount;

    /** 备注 (可空) */
    @Column(name = "notes", length = 1000)
    private String notes;

    /** 附件 JSON (可空) */
    @Column(name = "attachments", length = 1000)
    private String attachments;

    /** 确认人 (可空) */
    @Column(name = "confirmed_by", length = 100)
    private String confirmedBy;

    /** 确认时间 (可空) */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
