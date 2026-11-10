/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmAccountOpeningBalanceEntity.java
 * Date : 2026/11/10 17:30:00
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
 * 期初余额实体（V29）。
 * <p>
 * <b>为什么需要它</b>：本系统所有期初数都是「按已记账凭证累计」倒推的。
 * 但代账公司中途接手客户时，客户的账是别家做的——历史凭证在上一家的系统里，
 * 不可能逐笔补录。于是新客户在本系统里没有任何历史凭证：
 * 试算平衡的期初全 0、资产负债表年初余额全 0，账接不上、年报不能用。
 * </p>
 * <p>
 * <b>怎么用</b>：一条记录 = 某科目在「账套启用期间前一刻」的余额。
 * 查询时把它当作<b>启用期间之前的虚拟分录</b>并入聚合结果，
 * 因此试算平衡 / 总账 / 明细账 / 资产负债表 / 结转损益都会自动带上期初，
 * 不需要在五处各写一遍取数逻辑。
 * </p>
 * <p>
 * 借贷分两列而不是「正负号 + 方向」：负数在会计上没有意义，
 * 混用会让人误读，也无法直接求和校验平衡。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_account_opening_balance", schema = "scrm", indexes = {
        @Index(name = "idx_opening_balance_company", columnList = "company_id,opening_period")
})
@Data
public class ScrmAccountOpeningBalanceEntity {

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

    /** 账套启用期间（形如 202601），本行是启用期间前一刻的余额 */
    @Column(name = "opening_period", nullable = false)
    private Integer openingPeriod;

    /** 科目编码 */
    @Column(name = "subject_code", nullable = false, length = 20)
    private String subjectCode;

    /** 科目名称快照（科目日后改名不影响历史期初的可读性） */
    @Column(name = "subject_name", nullable = false, length = 100)
    private String subjectName;

    /** 期初借方余额 */
    @Column(name = "debit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    /** 期初贷方余额 */
    @Column(name = "credit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    /** 备注 */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /** 最后修改人 */
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /** 落库前补默认值：时间戳、版本号、借贷默认 0。 */
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

    /** 更新前刷新更新时间。 */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
