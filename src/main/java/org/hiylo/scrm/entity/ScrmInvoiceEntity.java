/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInvoiceEntity.java
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
 * SCRM 发票实例实体。
 * <p>
 * 记录发票全生命周期: 申请 → 审批 → 开具 → 寄送 → 送达, 以及作废与红冲。
 * 持有发票编号、抬头、客户与订单关联、金额税率明细、审批与开具信息、
 * 交付与快递信息、作废/红冲信息与原发票关联, 支持红冲生成冲红发票。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_invoice", schema = "scrm", indexes = {
        @Index(name = "idx_invoice_customer", columnList = "customer_id"),
        @Index(name = "idx_invoice_order", columnList = "order_id"),
        @Index(name = "idx_invoice_status", columnList = "status"),
        @Index(name = "idx_invoice_type", columnList = "invoice_type"),
        @Index(name = "idx_invoice_date", columnList = "invoice_date"),
        @Index(name = "idx_invoice_red_flush", columnList = "original_invoice_id"),
        @Index(name = "idx_invoice_red_flush_to", columnList = "red_flush_invoice_id")
})
@Data
public class ScrmInvoiceEntity {

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

    /** 发票编号 (唯一) */
    @Column(name = "invoice_no", nullable = false, length = 100)
    private String invoiceNo;

    /** 申请编号 (可空) */
    @Column(name = "application_no", length = 100)
    private String applicationNo;

    /** 发票类型: GENERAL / SPECIAL / ELECTRONIC / PLAIN_DIGITAL / RED_REDUCED */
    @Column(name = "invoice_type", nullable = false, length = 30)
    private String invoiceType;

    /** 发票类别: NORMAL / RED / SPECIAL_VAT / ELECTRONIC */
    @Column(name = "invoice_category", nullable = false, length = 30)
    private String invoiceCategory;

    /** 抬头类型: PERSONAL / ENTERPRISE */
    @Column(name = "title_type", nullable = false, length = 20)
    private String titleType;

    /** 发票抬头 */
    @Column(name = "invoice_title", nullable = false, length = 500)
    private String invoiceTitle;

    /** 税号 (可空) */
    @Column(name = "tax_number", length = 50)
    private String taxNumber;

    /** 开户行名称 (可空) */
    @Column(name = "bank_name", length = 200)
    private String bankName;

    /** 开户账号 (可空) */
    @Column(name = "bank_account", length = 100)
    private String bankAccount;

    /** 公司地址 (可空) */
    @Column(name = "company_address", length = 500)
    private String companyAddress;

    /** 公司电话 (可空) */
    @Column(name = "company_phone", length = 50)
    private String companyPhone;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 客户联系方式 (可空) */
    @Column(name = "customer_contact", length = 200)
    private String customerContact;

    /** 客户电话 (可空) */
    @Column(name = "customer_phone", length = 50)
    private String customerPhone;

    /** 客户邮箱 (可空) */
    @Column(name = "customer_email", length = 200)
    private String customerEmail;

    /** 关联订单 ID (可空) */
    @Column(name = "order_id", length = 100)
    private String orderId;

    /** 关联合同 ID (可空) */
    @Column(name = "contract_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 发票金额 (不含税) */
    @Column(name = "amount", nullable = false)
    private Double amount;

    /** 税率 (0-1, 如 0.13) */
    @Column(name = "tax_rate")
    private Double taxRate;

    /** 税额 */
    @Column(name = "tax_amount")
    private Double taxAmount;

    /** 价税合计 */
    @Column(name = "total_amount", nullable = false)
    private Double totalAmount;

    /** 折扣金额 */
    @Column(name = "discount_amount")
    private Double discountAmount;

    /** 实开金额 */
    @Column(name = "actual_amount")
    private Double actualAmount;

    /** 币种: CNY / USD / EUR 等, 默认 CNY */
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    /** 开票日期 (可空) */
    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    /** JSON 发票明细: [{name,spec,unit,quantity,price,amount,taxRate,taxAmount}] (可空) */
    @Column(name = "invoice_items", columnDefinition = "TEXT")
    private String invoiceItems;

    /** 发票备注 (可空) */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 状态: PENDING / APPROVED / ISSUED / SENT / RECEIVED / VOIDED / RED_FLUSHED / REJECTED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 申请原因 (可空) */
    @Column(name = "apply_reason", length = 500)
    private String applyReason;

    /** 申请人 (可空) */
    @Column(name = "applied_by", length = 100)
    private String appliedBy;

    /** 申请时间 (可空) */
    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    /** 审批人 (可空) */
    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    /** 审批时间 (可空) */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 审批意见 (可空) */
    @Column(name = "approval_comment", length = 500)
    private String approvalComment;

    /** 开票人 (可空) */
    @Column(name = "issued_by", length = 100)
    private String issuedBy;

    /** 开票时间 (可空) */
    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    /** 发票文件 URL (可空) */
    @Column(name = "invoice_url", length = 500)
    private String invoiceUrl;

    /** 发票图片 (可空) */
    @Column(name = "invoice_image", length = 500)
    private String invoiceImage;

    /** 交付方式: EMAIL / MAIL / SELF / DIGITAL (可空) */
    @Column(name = "delivery_method", length = 20)
    private String deliveryMethod;

    /** 交付状态: PENDING / SENT / DELIVERED / FAILED */
    @Column(name = "delivery_status", nullable = false, length = 20)
    private String deliveryStatus;

    /** 发送时间 (可空) */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /** 送达时间 (可空) */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** 快递单号 (可空) */
    @Column(name = "tracking_number", length = 200)
    private String trackingNumber;

    /** 邮寄地址 (可空) */
    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    /** 收件人 (可空) */
    @Column(name = "delivery_recipient", length = 100)
    private String deliveryRecipient;

    /** 收件人电话 (可空) */
    @Column(name = "delivery_phone", length = 50)
    private String deliveryPhone;

    /** 作废原因 (可空) */
    @Column(name = "void_reason", length = 500)
    private String voidReason;

    /** 作废人 (可空) */
    @Column(name = "voided_by", length = 100)
    private String voidedBy;

    /** 作废时间 (可空) */
    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    /** 红冲原因 (可空) */
    @Column(name = "red_flush_reason", length = 500)
    private String redFlushReason;

    /** 红冲人 (可空) */
    @Column(name = "red_flushed_by", length = 100)
    private String redFlushedBy;

    /** 红冲时间 (可空) */
    @Column(name = "red_flushed_at")
    private LocalDateTime redFlushedAt;

    /** 原发票 ID (红冲关联, 可空) */
    @Column(name = "original_invoice_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long originalInvoiceId;

    /** 红冲发票 ID (可空) */
    @Column(name = "red_flush_invoice_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long redFlushInvoiceId;

    /** 税务局代码 (可空) */
    @Column(name = "tax_bureau_code", length = 50)
    private String taxBureauCode;

    /** 税务局名称 (可空) */
    @Column(name = "tax_bureau_name", length = 200)
    private String taxBureauName;

    /** 设备号 (可空) */
    @Column(name = "device_no", length = 100)
    private String deviceNo;

    /** 发票代码 (可空) */
    @Column(name = "invoice_code", length = 50)
    private String invoiceCode;

    /** 发票号码 (可空) */
    @Column(name = "invoice_number", length = 50)
    private String invoiceNumber;

    /** 校验码 (可空) */
    @Column(name = "check_code", length = 100)
    private String checkCode;

    /** 二维码内容 (可空) */
    @Column(name = "qr_code", length = 1000)
    private String qrCode;

    /** 标签逗号分隔 (可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 备注 (可空) */
    @Column(name = "notes", length = 1000)
    private String notes;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
