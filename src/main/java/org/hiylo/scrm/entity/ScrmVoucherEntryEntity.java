/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmVoucherEntryEntity.java
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 凭证分录实体（凭证域）。
 * <p>
 * <b>借/贷分列而不是用正负号</b>：会计上金额没有负数语义，
 * 用「正数=借、负数=贷」会让人误读，也无法直接对一列求和做平衡校验。
 * 分列后「借方合计 = 贷方合计」就是一个直接的两列求和比较。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_voucher_entry", schema = "scrm", indexes = {
        @Index(name = "idx_entry_voucher", columnList = "voucher_id"),
        @Index(name = "idx_entry_company_subject", columnList = "company_id,subject_code")
})
@Data
public class ScrmVoucherEntryEntity {

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

    /** 所属凭证 ID */
    @Column(name = "voucher_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long voucherId;

    /** 所属公司主体 ID（冗余，便于直接按科目聚合而不必回连凭证） */
    @Column(name = "company_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long companyId;

    /** 分录行号（从 1 开始） */
    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    /** 科目编码 */
    @Column(name = "subject_code", nullable = false, length = 20)
    private String subjectCode;

    /** 科目名称（冗余快照，科目改名不影响历史凭证） */
    @Column(name = "subject_name", nullable = false, length = 100)
    private String subjectName;

    /** 摘要 */
    @Column(name = "summary", length = 200)
    private String summary;

    /** 借方金额 */
    @Column(name = "debit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    /** 贷方金额 */
    @Column(name = "credit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal creditAmount = BigDecimal.ZERO;

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
        if (debitAmount == null) {
            debitAmount = BigDecimal.ZERO;
        }
        if (creditAmount == null) {
            creditAmount = BigDecimal.ZERO;
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
