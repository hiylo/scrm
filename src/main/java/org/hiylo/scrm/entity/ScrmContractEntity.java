/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractEntity.java
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
 * SCRM 合同实例实体。
 * <p>
 * 合同实例记录合同的完整生命周期: 草稿 → 提交审批 → 审批通过 → 签署 → 生效 → 到期/终止/归档。
 * 持有合同编号、客户信息、金额、期限、审批与签署信息、续约关联与提醒配置,
 * 支持基于模板创建并渲染合同内容, 支持续约 (关联原合同) 与复制。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_contract", schema = "scrm", indexes = {
        @Index(name = "idx_contract_customer", columnList = "customer_id"),
        @Index(name = "idx_contract_sales_person", columnList = "sales_person_id"),
        @Index(name = "idx_contract_status", columnList = "status"),
        @Index(name = "idx_contract_template_ref", columnList = "template_id"),
        @Index(name = "idx_contract_renewal", columnList = "renewal_of_id"),
        @Index(name = "idx_contract_expires", columnList = "status,end_date")
})
@Data
public class ScrmContractEntity {

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

    /** 合同编号 (唯一) */
    @Column(name = "contract_no", nullable = false, length = 100)
    private String contractNo;

    /** 合同名称 */
    @Column(name = "contract_name", nullable = false, length = 500)
    private String contractName;

    /** 合同类型: SALES / SERVICE / PARTNERSHIP / NDA / RESELLER / AGENCY / MAINTENANCE / RENTAL / PURCHASE / CUSTOM */
    @Column(name = "contract_type", nullable = false, length = 30)
    private String contractType;

    /** 关联模板 ID (可空, 手动创建时为 null) */
    @Column(name = "template_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

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

    /** 客户地址 (可空) */
    @Column(name = "customer_address", length = 500)
    private String customerAddress;

    /** 合同标题 (可空) */
    @Column(name = "title", length = 500)
    private String title;

    /** 合同描述 (可空) */
    @Column(name = "description", length = 1000)
    private String description;

    /** 合同内容 (渲染后的完整内容) */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** JSON 变量值 (创建合同时传入的变量键值对, 可空) */
    @Column(name = "variables", columnDefinition = "TEXT")
    private String variables;

    /** 合同金额 */
    @Column(name = "contract_amount", nullable = false)
    private Double contractAmount;

    /** 币种: CNY / USD / EUR 等, 默认 CNY */
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    /** 付款条款 (可空) */
    @Column(name = "payment_terms", length = 500)
    private String paymentTerms;

    /** 合同开始日期 */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 合同结束日期 */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 合同期限 (月, 可空) */
    @Column(name = "duration_months")
    private Integer durationMonths;

    /** 是否自动续约 */
    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew;

    /** 自动续约月数 */
    @Column(name = "auto_renew_months", nullable = false)
    private Integer autoRenewMonths;

    /** 签署日期 (可空) */
    @Column(name = "signed_date")
    private LocalDate signedDate;

    /** 生效日期 (可空) */
    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    /** 失效日期 (可空) */
    @Column(name = "expired_date")
    private LocalDate expiredDate;

    /* 状态: DRAFT / PENDING_REVIEW / PENDING_SIGNATURE / SIGNED / ACTIVE / EXPIRED / TERMINATED / CANCELLED / ARCHIVED */
    /* */ @Column(name = "status", nullable = false, length = 20)    private String status;

    /** 优先级 (数值越大优先级越高) */
    @Column(name = "priority", nullable = false)
    private Integer priority;

    /** 销售人员 ID (可空) */
    @Column(name = "sales_person_id", length = 100)
    private String salesPersonId;

    /** 销售人员名称 (可空) */
    @Column(name = "sales_person_name", length = 100)
    private String salesPersonName;

    /** 部门 ID (可空) */
    @Column(name = "department_id", length = 100)
    private String departmentId;

    /** 部门名称 (可空) */
    @Column(name = "department_name", length = 200)
    private String departmentName;

    /** 审批人 ID (可空) */
    @Column(name = "approver_id", length = 100)
    private String approverId;

    /** 审批人名称 (可空) */
    @Column(name = "approver_name", length = 100)
    private String approverName;

    /** 审批时间 (可空) */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 审批意见 (可空) */
    @Column(name = "approval_comment", length = 500)
    private String approvalComment;

    /** 签署人 ID (可空) */
    @Column(name = "signer_id", length = 100)
    private String signerId;

    /** 签署人名称 (可空) */
    @Column(name = "signer_name", length = 100)
    private String signerName;

    /** 签署方式: ELECTRONIC / PAPER / STAMP (可空) */
    @Column(name = "signature_method", length = 30)
    private String signatureMethod;

    /** 签署文件 URL (可空) */
    @Column(name = "signature_url", length = 500)
    private String signatureUrl;

    /** JSON 附件列表 (可空) */
    @Column(name = "attachments", length = 1000)
    private String attachments;

    /** 标签逗号分隔 (可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 关联合同 ID 逗号分隔 (可空) */
    @Column(name = "related_contracts", length = 500)
    private String relatedContracts;

    /** 续约自合同 ID (可空, 表示本合同是 renewalOfId 合同的续约) */
    @Column(name = "renewal_of_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long renewalOfId;

    /** 续约到合同 ID (可空, 表示原合同已续约为 renewedToId 合同) */
    @Column(name = "renewed_to_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long renewedToId;

    /** 是否启用提醒 */
    @Column(name = "reminders_enabled", nullable = false)
    private Boolean remindersEnabled;

    /** 提前提醒天数 */
    @Column(name = "reminder_days_before", nullable = false)
    private Integer reminderDaysBefore;

    /** 最后提醒发送时间 (可空) */
    @Column(name = "last_reminder_sent_at")
    private LocalDateTime lastReminderSentAt;

    /** JSON 合同条款详情: [{clauseName, content, isCustom}] (可空) */
    @Column(name = "terms", columnDefinition = "TEXT")
    private String terms;

    /** JSON 自定义字段 (可空) */
    @Column(name = "custom_fields", columnDefinition = "TEXT")
    private String customFields;

    /** 备注 (可空) */
    @Column(name = "notes", length = 1000)
    private String notes;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
