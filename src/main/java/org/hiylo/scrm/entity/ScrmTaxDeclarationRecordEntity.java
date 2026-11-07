/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxDeclarationRecordEntity.java
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
 * 税种申报记录实体（报税日历域, 状态机主体）。
 * <p>
 * 一个档案（客户 × 税种）的每个申报周期 = 一条记录, 状态机:
 * NOT_STARTED（未开始）→ PENDING（待申报, 进入申报窗口）→ FILED（已申报, 申报≠缴款）→
 * PAID（已缴）→ ARCHIVED（期后归档, 终态）; 截止日未申报转 OVERDUE（逾期, 可补报直接进
 * PAID）, 减免/政策豁免置 CANCELED（终态）。申报与缴款分离: {@link #filedAt} 由「申报完成」
 * 动作回填, {@link #paidAt} 由「缴款完成」动作回填, 逾期判定以申报动作为主、缴款超期单独告警。
 * 责任留痕: filedBy / escalatedAt / escalatedTo / taxReceiptNo 全部落库, 配合 AuditLog。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_tax_declaration_record", schema = "scrm", indexes = {
        @Index(name = "idx_tax_record_due", columnList = "declaration_status,due_date"),
        @Index(name = "idx_tax_record_customer_period", columnList = "customer_id,period_start,period_end"),
        @Index(name = "idx_tax_record_overdue", columnList = "declaration_status,overdue_days")
})
@Data
public class ScrmTaxDeclarationRecordEntity {

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

    // ==================== 申报记录业务字段 ====================

    /** 档案 ID（引用 scrm_tax_declaration_profile.id） */
    @Column(name = "profile_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long profileId;

    /** 客户 ID（冗余, 查询快） */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 税种（冗余） */
    @Column(name = "tax_type", nullable = false, length = 30)
    private String taxType;

    /** 属地（冗余, 预警文案属地口径提示） */
    @Column(name = "region", length = 100)
    private String region;

    /** 所属期开始（如 2026-10-01） */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    /** 所属期结束（如 2026-12-31） */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** 申报截止日期（按 deadlineRule + 节假日顺延计算） */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** 申报税额（申报后回填） */
    @Column(name = "tax_amount", precision = 15, scale = 2)
    private BigDecimal taxAmount;

    /** 实缴税额（缴款后回填） */
    @Column(name = "paid_amount", precision = 15, scale = 2)
    private BigDecimal paidAmount;

    /** 状态: NOT_STARTED / PENDING / FILED / PAID / OVERDUE / ARCHIVED / CANCELED */
    @Column(name = "declaration_status", nullable = false, length = 20)
    private String declarationStatus = "NOT_STARTED";

    /** 逾期天数（OVERDUE 状态有效） */
    @Column(name = "overdue_days", nullable = false)
    private Integer overdueDays = 0;

    /** 升级时间（逾期 ≥5 天 / 缴款超期） */
    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    /** 升级对象（主管 ID） */
    @Column(name = "escalated_to", length = 100)
    private String escalatedTo;

    /** 申报完成时间 */
    @Column(name = "filed_at")
    private LocalDateTime filedAt;

    /** 申报操作人（会计） */
    @Column(name = "filed_by", length = 100)
    private String filedBy;

    /** 缴款完成时间 */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** 完税凭证号（可空） */
    @Column(name = "tax_receipt_no", length = 100)
    private String taxReceiptNo;

    /** 生成来源: SYSTEM(自动生成) / MANUAL(手工补录) / IMPORT(批量导入) */
    @Column(name = "source", nullable = false, length = 20)
    private String source = "SYSTEM";

    /** 备注 */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
