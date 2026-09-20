/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractChangeEntity.java
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
 * SCRM 合同变更实体。
 * <p>
 * 承载合同变更的完整信息: 变更编号 (唯一)、变更类型 (修订/补充/续约/终止/转让/价格变更/范围变更/
 * 期限变更/其他)、变更原因与描述、变更状态 (待审/审核中/已批准/已驳回/已执行/已取消)、变更日期、
 * 生效日期、原值与新值 JSON、影响字段、金额变化 (前/后/差)、影响与风险评估、审批信息、
 * 附件, 以及续约/转让时的新合同 ID 关联。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_contract_change", schema = "scrm", indexes = {
        @Index(name = "idx_contract_change_contract", columnList = "contract_id"),
        @Index(name = "idx_contract_change_no", columnList = "change_no", unique = true),
        @Index(name = "idx_contract_change_status", columnList = "change_status"),
        @Index(name = "idx_contract_change_type", columnList = "change_type"),
        @Index(name = "idx_contract_change_new_contract", columnList = "new_contract_id")
})
@Data
public class ScrmContractChangeEntity {

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

    /** 变更编号 (唯一) */
    @Column(name = "change_no", nullable = false, length = 100, unique = true)
    private String changeNo;

/** 变更类型: AMENDMENT / SUPPLEMENT / RENEWAL / TERMINATION / TRANSFER / PRICE_CHANGE / SCOPE_CHANGE / TERM_CHANGE /
         * OTHER */
    @Column(name = "change_type", nullable = false, length = 30)
    private String changeType;

    /** 变更原因 */
    @Column(name = "change_reason", nullable = false, length = 500)
    private String changeReason;

    /** 变更描述 (可空) */
    @Column(name = "change_description", length = 2000)
    private String changeDescription;

    /** 变更状态: PENDING / IN_REVIEW / APPROVED / REJECTED / EXECUTED / CANCELLED (默认 PENDING) */
    @Column(name = "change_status", nullable = false, length = 20)
    private String changeStatus;

    /** 变更日期 (可空) */
    @Column(name = "change_date")
    private LocalDate changeDate;

    /** 生效日期 (可空) */
    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    /** 原值 JSON (可空) */
    @Column(name = "old_value", length = 2000)
    private String oldValue;

    /** 新值 JSON (可空) */
    @Column(name = "new_value", length = 2000)
    private String newValue;

    /** 影响字段 (可空) */
    @Column(name = "affected_fields", length = 500)
    private String affectedFields;

    /** 金额变化 (默认 0) */
    @Column(name = "value_change")
    private Double valueChange;

    /** 变更前金额 (默认 0) */
    @Column(name = "value_before")
    private Double valueBefore;

    /** 变更后金额 (默认 0) */
    @Column(name = "value_after")
    private Double valueAfter;

    /** 影响评估 (可空) */
    @Column(name = "impact_assessment", length = 1000)
    private String impactAssessment;

    /** 风险评估 (可空) */
    @Column(name = "risk_assessment", length = 1000)
    private String riskAssessment;

    /** 审批人 ID (可空) */
    @Column(name = "approver_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approverId;

    /** 审批人名称 (可空) */
    @Column(name = "approver_name", length = 100)
    private String approverName;

    /** 审批时间 (可空) */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 审批意见 (可空) */
    @Column(name = "approval_notes", length = 1000)
    private String approvalNotes;

    /** 附件 JSON (可空) */
    @Column(name = "attachments", length = 1000)
    private String attachments;

    /** 新合同 ID (可空, 续约/转让时指向新合同) */
    @Column(name = "new_contract_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long newContractId;

    /** 备注 (可空) */
    @Column(name = "notes", length = 1000)
    private String notes;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
