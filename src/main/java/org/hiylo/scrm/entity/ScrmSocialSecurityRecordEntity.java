/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSocialSecurityRecordEntity.java
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
 * 社保缴费明细实体（薪酬域，按月按人）。
 * <p>
 * 分项保存个人与单位部分，而不是只存一个合计——因为会计对账时需要
 * 「养老/医疗/失业/工伤/生育/公积金」逐项核对，合计对不上时要能定位到哪一项。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_social_security_record", schema = "scrm", indexes = {
        @Index(name = "idx_social_company_period", columnList = "company_id,period_year,period_month"),
        @Index(name = "idx_social_status", columnList = "status,period_year,period_month")
})
@Data
public class ScrmSocialSecurityRecordEntity {

    /** 状态：待申报 */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态：已申报 */
    public static final String STATUS_DECLARED = "DECLARED";
    /** 状态：已缴费 */
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

    /** 员工姓名（冗余，避免列表回查） */
    @Column(name = "employee_name", length = 50)
    private String employeeName;

    /** 所属年 */
    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    /** 所属月（1-12） */
    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    /** 本月实际缴费基数（已按当地上下限截断） */
    @Column(name = "base_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseAmount;

    /** 养老保险个人部分 */
    @Column(name = "pension_personal", nullable = false, precision = 15, scale = 2)
    private BigDecimal pensionPersonal = BigDecimal.ZERO;

    /** 养老保险单位部分 */
    @Column(name = "pension_company", nullable = false, precision = 15, scale = 2)
    private BigDecimal pensionCompany = BigDecimal.ZERO;

    /** 医疗保险个人部分 */
    @Column(name = "medical_personal", nullable = false, precision = 15, scale = 2)
    private BigDecimal medicalPersonal = BigDecimal.ZERO;

    /** 医疗保险单位部分 */
    @Column(name = "medical_company", nullable = false, precision = 15, scale = 2)
    private BigDecimal medicalCompany = BigDecimal.ZERO;

    /** 失业保险个人部分 */
    @Column(name = "unemployment_personal", nullable = false, precision = 15, scale = 2)
    private BigDecimal unemploymentPersonal = BigDecimal.ZERO;

    /** 失业保险单位部分 */
    @Column(name = "unemployment_company", nullable = false, precision = 15, scale = 2)
    private BigDecimal unemploymentCompany = BigDecimal.ZERO;

    /** 工伤保险单位部分（仅单位缴纳） */
    @Column(name = "injury_company", nullable = false, precision = 15, scale = 2)
    private BigDecimal injuryCompany = BigDecimal.ZERO;

    /** 生育保险单位部分（仅单位缴纳） */
    @Column(name = "maternity_company", nullable = false, precision = 15, scale = 2)
    private BigDecimal maternityCompany = BigDecimal.ZERO;

    /** 住房公积金个人部分 */
    @Column(name = "housing_fund_personal", nullable = false, precision = 15, scale = 2)
    private BigDecimal housingFundPersonal = BigDecimal.ZERO;

    /** 住房公积金单位部分 */
    @Column(name = "housing_fund_company", nullable = false, precision = 15, scale = 2)
    private BigDecimal housingFundCompany = BigDecimal.ZERO;

    /** 个人合计 */
    @Column(name = "total_personal", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPersonal = BigDecimal.ZERO;

    /** 单位合计 */
    @Column(name = "total_company", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCompany = BigDecimal.ZERO;

    /** 合计（个人 + 单位） */
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** 状态：PENDING/DECLARED/PAID */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    /** 申报时间 */
    @Column(name = "declared_at")
    private LocalDateTime declaredAt;

    /** 缴费时间 */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** 备注 */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /**
     * 重算并写回个人/单位/总计三个汇总列。
     * <p>分项被手工调整后必须调用，保证合计与分项始终一致。</p>
     */
    public void recalculateTotals() {
        totalPersonal = nz(pensionPersonal).add(nz(medicalPersonal))
                .add(nz(unemploymentPersonal)).add(nz(housingFundPersonal));
        totalCompany = nz(pensionCompany).add(nz(medicalCompany))
                .add(nz(unemploymentCompany)).add(nz(injuryCompany))
                .add(nz(maternityCompany)).add(nz(housingFundCompany));
        totalAmount = totalPersonal.add(totalCompany);
    }

    /** null 安全取零。 */
    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 持久化前回调：填充时间、版本与汇总列。
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
        if (baseAmount == null) {
            baseAmount = BigDecimal.ZERO;
        }
        recalculateTotals();
    }

    /**
     * 更新前回调：刷新时间并重算汇总。
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
        recalculateTotals();
    }
}
