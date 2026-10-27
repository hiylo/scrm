/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxDeclarationProfileEntity.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 税种申报档案实体（报税日历域）。
 * <p>
 * 描述「客户 × 税种 × 申报周期」的静态申报配置: 一个客户可建多档案（税种/纳税人类型/
 * 周期/征期规则可拆分）, 记录属地（影响 deadlineRule 覆盖）、有效期（合同生效到终止）、
 * 申报主体、最近人工复核日期（申报期属地区别大, 建议每年复核）。{@link #status}
 * 流转: ACTIVE（启用, 周期触达自动生成申报记录）→ PAUSED（暂停生成）/ CANCELED（取消）。
 * 申报记录由 {@code ScrmTaxDeclarationService} 按 {@link #declarationCycle} 周期实例化。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_tax_declaration_profile", schema = "scrm", indexes = {
        @Index(name = "idx_tax_profile_customer", columnList = "customer_id"),
        @Index(name = "idx_tax_profile_tax_status", columnList = "tax_type,status")
})
@Data
public class ScrmTaxDeclarationProfileEntity {

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

    // ==================== 申报档案业务字段 ====================

    /** 客户 ID（引用 scrm_customer.id） */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 报税主体（公司）ID（V14）；customerId 保留为经办联系人 */
    @Column(name = "company_id")
    private Long companyId;

    /** 客户名称（冗余, 列表展示） */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 税种: VAT(增值税) / IIT(个人所得税) / CIT(企业所得税) / SOCIAL_SECURITY(社保) */
    @Column(name = "tax_type", nullable = false, length = 30)
    private String taxType;

    /** 申报周期: MONTHLY / QUARTERLY / YEARLY / ANNUAL_SETTLEMENT(年度汇算) */
    @Column(name = "declaration_cycle", nullable = false, length = 10)
    private String declarationCycle = "MONTHLY";

    /** 纳税人类型: SMALL(小规模) / GENERAL(一般纳税人) / INDIVIDUAL(个体户) */
    @Column(name = "taxpayer_type", nullable = false, length = 20)
    private String taxpayerType = "SMALL";

    /** 征期规则: DAY15(次/季终后15日) / DAY31(汇算次年3/31) / DAY05_31(汇算5/31) / LOCAL(属地自定义) */
    @Column(name = "deadline_rule", nullable = false, length = 30)
    private String deadlineRule = "DAY15";

    /** 属地（电子税务局口径, 影响 deadlineRule 覆盖） */
    @Column(name = "region", length = 100)
    private String region;

    /** 有效开始日期（合同生效/档案建立） */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 有效结束日期（合同到期/注销备案, 可空表示长期有效） */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** 申报主体（客户企业名/代账承接方） */
    @Column(name = "declaration_org", length = 200)
    private String declarationOrg;

    /** 备注 */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 状态: ACTIVE / PAUSED(暂停生成) / CANCELED(取消) */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    /** 最近人工复核日期（申报期属地区别大, 建议每年复核） */
    @Column(name = "last_verified_at")
    private LocalDate lastVerifiedAt;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
