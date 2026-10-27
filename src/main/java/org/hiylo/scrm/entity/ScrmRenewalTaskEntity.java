/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmRenewalTaskEntity.java
 * Date : 2026/10/23 12:30:00
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 服务续费任务实体（续费流失域）。
 * <p>
 * 合同到期前由 {@code ScrmRenewalService} 扫描生成（幂等: 同一合同仅一条未完成任务,
 * 键=contractId + 状态∈待办集）, 每合同一条续费任务承载 30/7/3 三级提醒节奏与
 * 续费结果录入。状态机: PENDING →（需审批）APPROVAL_PENDING → APPROVED →
 * IN_PROGRESS → RENEWED（成功）; PENDING → LOST（放弃/到期未续）; → CANCELLED（合同终止）;
 * 到期后仍 PENDING 自动转 OVERDUE 并触发流失评分。折扣/赠期需审批（approvalRequired=true）,
 * 审批通过才回填 discountPct / graceMonths / renewalAmount, 审批实例 ID 挂 approvalInstanceId。
 * 提醒去重: lastRemindedAt / remindCount 记录节奏, 未跟进时 7/3 天二次/升级提醒。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_renewal_task", schema = "scrm", indexes = {
        @Index(name = "idx_renewal_task_contract", columnList = "contract_id"),
        @Index(name = "idx_renewal_task_customer", columnList = "customer_id"),
        @Index(name = "idx_renewal_task_status", columnList = "status"),
        @Index(name = "idx_renewal_task_due", columnList = "status,planned_renewal_date")
})
@Data
public class ScrmRenewalTaskEntity {

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

    // ==================== 续费任务业务字段 ====================

    /** 任务编号: RN-YYYYMMDD-序号 */
    @Column(name = "task_no", nullable = false, length = 50)
    private String taskNo;

    /** 待续约合同 ID（引用 scrm_contract.id） */
    @Column(name = "contract_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 冗余: 原合同编号 */
    @Column(name = "contract_no", length = 100)
    private String contractNo;

    /** 冗余: 原合同名称 */
    @Column(name = "contract_name", length = 500)
    private String contractName;

    /** 客户 ID（冗余） */
    @Column(name = "customer_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 续费主体（公司）ID（V14）；customerId 保留为对接联系人 */
    @Column(name = "company_id")
    private Long companyId;

    /** 客户名称（冗余） */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 销售 ID（冗余, 取合同 sales_person_id） */
    @Column(name = "sales_person_id", length = 100)
    private String salesPersonId;

    /** 销售名称（冗余） */
    @Column(name = "sales_person_name", length = 100)
    private String salesPersonName;

    /** 计划续费日期（=合同到期日） */
    @Column(name = "planned_renewal_date", nullable = false)
    private LocalDate plannedRenewalDate;

    /** 提醒窗口天数（默认 30） */
    @Column(name = "remind_days_before", nullable = false)
    private Integer remindDaysBefore = 30;

    /** 续费选项: STANDARD(标准价) / DISCOUNT(折扣) / GRACE(赠期) / CANCEL */
    @Column(name = "renewal_option", length = 30)
    private String renewalOption;

    /** 折扣比例（审批通过后回填） */
    @Column(name = "discount_pct", precision = 5, scale = 2)
    private BigDecimal discountPct;

    /** 赠期月数（审批通过后回填） */
    @Column(name = "grace_months")
    private Integer graceMonths;

    /** 续约价（审批通过后回填） */
    @Column(name = "renewal_amount", precision = 15, scale = 2)
    private BigDecimal renewalAmount;

    /** 关联审批实例 ID（需审批时） */
    @Column(name = "approval_instance_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approvalInstanceId;

    /** 是否需审批（默认: renewalOption != STANDARD → true） */
    @Column(name = "approval_required", nullable = false)
    private Boolean approvalRequired = false;

    /** 状态: PENDING / APPROVAL_PENDING / APPROVED / IN_PROGRESS / RENEWED / LOST / OVERDUE / CANCELLED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    /** 生成来源: SYSTEM_SCHEDULE / MANUAL */
    @Column(name = "source", nullable = false, length = 20)
    private String source = "SYSTEM_SCHEDULE";

    /** 上次提醒时间（提醒去重） */
    @Column(name = "last_reminded_at")
    private LocalDateTime lastRemindedAt;

    /** 累计提醒次数 */
    @Column(name = "remind_count", nullable = false)
    private Integer remindCount = 0;

    /** 备注 */
    @Column(name = "notes", length = 500)
    private String notes;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
