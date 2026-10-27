/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIitRecordEntity.java
 * Date : 2026/10/25 13:00:00
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
import java.time.LocalDateTime;

/**
 * 个人所得税扣缴明细实体（薪酬域，按月按人）。
 * <p>
 * <b>为什么必须存累计中间量</b>：2019 年起工资薪金个税按「累计预扣法」计算，
 * 每期税额 = 累计应纳税所得额按年度税率表算出的累计税额 − 累计已预扣。
 * 如果只存本期税额，历史一旦调整（补发、更正申报）就无法复算，
 * 也和个税申报表对不上。因此累计收入、累计减除、累计应纳税所得额、
 * 累计已预扣全部落库。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_iit_record", schema = "scrm", indexes = {
        @Index(name = "idx_iit_company_period", columnList = "company_id,period_year,period_month"),
        @Index(name = "idx_iit_status", columnList = "status,period_year,period_month")
})
@Data
public class ScrmIitRecordEntity {

    /** 状态：待申报 */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态：已申报 */
    public static final String STATUS_DECLARED = "DECLARED";
    /** 状态：已缴款 */
    public static final String STATUS_PAID = "PAID";

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

    /** 乐观锁版本号 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** 所属公司主体 ID */
    @Column(name = "company_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long companyId;

    /** 公司名称（冗余） */
    @Column(name = "company_name", length = 200)
    private String companyName;

    /** 员工 ID */
    @Column(name = "employee_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;

    /** 员工姓名（冗余） */
    @Column(name = "employee_name", length = 50)
    private String employeeName;

    /** 所属年 */
    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    /** 所属月（1-12） */
    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    /** 本期收入（税前工资） */
    @Column(name = "income_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal incomeAmount = BigDecimal.ZERO;

    /** 本期减除费用（5000 元/月） */
    @Column(name = "basic_deduction", nullable = false, precision = 15, scale = 2)
    private BigDecimal basicDeduction = BigDecimal.ZERO;

    /** 本期专项扣除（社保 + 公积金个人部分） */
    @Column(name = "social_deduction", nullable = false, precision = 15, scale = 2)
    private BigDecimal socialDeduction = BigDecimal.ZERO;

    /** 本期专项附加扣除 */
    @Column(name = "special_deduction", nullable = false, precision = 15, scale = 2)
    private BigDecimal specialDeduction = BigDecimal.ZERO;

    /** 累计收入 */
    @Column(name = "cumulative_income", nullable = false, precision = 15, scale = 2)
    private BigDecimal cumulativeIncome = BigDecimal.ZERO;

    /** 累计减除费用与扣除合计 */
    @Column(name = "cumulative_deduction", nullable = false, precision = 15, scale = 2)
    private BigDecimal cumulativeDeduction = BigDecimal.ZERO;

    /** 累计应纳税所得额 */
    @Column(name = "cumulative_taxable", nullable = false, precision = 15, scale = 2)
    private BigDecimal cumulativeTaxable = BigDecimal.ZERO;

    /** 适用税率（小数，如 0.03） */
    @Column(name = "tax_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal taxRate = BigDecimal.ZERO;

    /** 速算扣除数 */
    @Column(name = "quick_deduction", nullable = false, precision = 15, scale = 2)
    private BigDecimal quickDeduction = BigDecimal.ZERO;

    /** 累计应纳税额 */
    @Column(name = "cumulative_tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal cumulativeTax = BigDecimal.ZERO;

    /** 累计已预扣税额 */
    @Column(name = "paid_tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal paidTax = BigDecimal.ZERO;

    /** 本期应预扣税额（可为负，负数留抵下期） */
    @Column(name = "current_tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentTax = BigDecimal.ZERO;

    /** 状态：PENDING/DECLARED/PAID */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    /** 申报时间 */
    @Column(name = "declared_at")
    private LocalDateTime declaredAt;

    /** 缴款时间 */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** 备注 */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /**
     * 持久化前回调：填充时间与版本初值。
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
        if (status == null) {
            status = STATUS_PENDING;
        }
    }

    /**
     * 更新前回调：刷新更新时间。
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
