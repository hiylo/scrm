/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmEmployeeEntity.java
 * Date : 2026/10/25 13:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工档案实体（薪酬域）。
 * <p>
 * 社保与个税的唯一数据源。代账公司每月固定做两件事——增员/减员与基数核定——
 * 都落在这张表上；没有员工档案，社保/个税明细就只能凭空录入，无法追溯。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_employee", schema = "scrm", indexes = {
        @Index(name = "idx_employee_company", columnList = "company_id"),
        @Index(name = "idx_employee_status", columnList = "company_id,status,social_status")
})
@Data
public class ScrmEmployeeEntity {

    /** 状态：在职 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 状态：离职 */
    public static final String STATUS_LEFT = "LEFT";

    /** 参保状态：参保 */
    public static final String SOCIAL_INSURED = "INSURED";
    /** 参保状态：暂停（如停薪留职） */
    public static final String SOCIAL_SUSPENDED = "SUSPENDED";
    /** 参保状态：未参保 */
    public static final String SOCIAL_NOT_INSURED = "NOT_INSURED";

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

    /** 员工姓名 */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * 证件号。
     * <p>
     * 个税/社保申报必需，但属于敏感个人信息：接口只接受写入、不回显明文，
     * 列表展示统一走 {@link #getIdCardMasked()} 脱敏值。
     * </p>
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "id_card_no", length = 30)
    private String idCardNo;

    /** 联系电话 */
    @Column(name = "phone", length = 20)
    private String phone;

    /** 岗位 */
    @Column(name = "position", length = 100)
    private String position;

    /** 入职日期 */
    @Column(name = "hire_date")
    private LocalDate hireDate;

    /** 离职日期 */
    @Column(name = "leave_date")
    private LocalDate leaveDate;

    /** 参保状态：INSURED/SUSPENDED/NOT_INSURED */
    @Column(name = "social_status", nullable = false, length = 20)
    private String socialStatus = SOCIAL_INSURED;

    /** 社保缴费基数（空则由服务层按当地上下限与默认值推定） */
    @Column(name = "social_base", precision = 15, scale = 2)
    private BigDecimal socialBase;

    /** 住房公积金缴费基数 */
    @Column(name = "housing_fund_base", precision = 15, scale = 2)
    private BigDecimal housingFundBase;

    /** 专项附加扣除月额合计（子女教育/房贷/赡养老人等） */
    @Column(name = "special_deduction", nullable = false, precision = 15, scale = 2)
    private BigDecimal specialDeduction = BigDecimal.ZERO;

    /** 状态：ACTIVE 在职 / LEFT 离职 */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    /** 备注 */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /**
     * 证件号脱敏展示（前 6 后 4，长度不足时整体打码）。
     *
     * @return 脱敏后的证件号；为空返回 null
     */
    @Transient
    public String getIdCardMasked() {
        if (idCardNo == null || idCardNo.isBlank()) {
            return null;
        }
        String value = idCardNo.trim();
        if (value.length() <= 10) {
            return "****";
        }
        return value.substring(0, 6) + "********" + value.substring(value.length() - 4);
    }

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
            status = STATUS_ACTIVE;
        }
        if (socialStatus == null) {
            socialStatus = SOCIAL_INSURED;
        }
        if (specialDeduction == null) {
            specialDeduction = BigDecimal.ZERO;
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
