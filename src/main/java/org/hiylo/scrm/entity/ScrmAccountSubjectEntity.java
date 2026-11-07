/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmAccountSubjectEntity.java
 * Date : 2026/10/25 18:00:00
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

import java.time.LocalDateTime;

/**
 * 会计科目实体（凭证域）。
 * <p>
 * <b>为什么要按企业独立建账</b>：科目表决定了一家企业的报表口径，
 * 不同企业可以增减明细科目（如 560201 办公费），共用一套科目会导致
 * 一家企业改科目影响另一家。
 * </p>
 * <p>
 * {@code direction} 是余额方向，不是借贷方向：它决定期末余额怎么算——
 * 借方科目（资产/成本/费用）期末 = 期初 + 本期借 − 本期贷；
 * 贷方科目（负债/权益/收入）期末 = 期初 + 本期贷 − 本期借。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_account_subject", schema = "scrm", indexes = {
        @Index(name = "idx_subject_company_category", columnList = "company_id,category"),
        @Index(name = "idx_subject_parent", columnList = "company_id,parent_code")
})
@Data
public class ScrmAccountSubjectEntity {

    /** 类别：资产 */
    public static final String CATEGORY_ASSET = "ASSET";
    /** 类别：负债 */
    public static final String CATEGORY_LIABILITY = "LIABILITY";
    /** 类别：所有者权益 */
    public static final String CATEGORY_EQUITY = "EQUITY";
    /** 类别：成本 */
    public static final String CATEGORY_COST = "COST";
    /** 类别：损益-收入 */
    public static final String CATEGORY_INCOME = "INCOME";
    /** 类别：损益-支出 */
    public static final String CATEGORY_EXPENSE = "EXPENSE";

    /** 余额方向：借方 */
    public static final String DIRECTION_DEBIT = "DEBIT";
    /** 余额方向：贷方 */
    public static final String DIRECTION_CREDIT = "CREDIT";

    /** 状态：启用 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 状态：停用（历史分录保留，不可新增） */
    public static final String STATUS_DISABLED = "DISABLED";

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

    /** 科目编码，如 1002 / 100201 */
    @Column(name = "subject_code", nullable = false, length = 20)
    private String subjectCode;

    /** 科目名称 */
    @Column(name = "subject_name", nullable = false, length = 100)
    private String subjectName;

    /** 类别：ASSET/LIABILITY/EQUITY/COST/INCOME/EXPENSE */
    @Column(name = "category", nullable = false, length = 20)
    private String category;

    /** 余额方向：DEBIT 借方科目 / CREDIT 贷方科目 */
    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    /** 上级科目编码（空表示一级科目） */
    @Column(name = "parent_code", length = 20)
    private String parentCode;

    /** 科目级次（一级为 1） */
    @Column(name = "subject_level", nullable = false)
    private Integer subjectLevel = 1;

    /** 状态：ACTIVE/DISABLED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

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
            status = STATUS_ACTIVE;
        }
        if (subjectLevel == null) {
            subjectLevel = 1;
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
