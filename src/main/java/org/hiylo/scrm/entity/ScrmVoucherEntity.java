/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmVoucherEntity.java
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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 记账凭证头实体（凭证域）。
 * <p>
 * <b>为什么借贷合计要落库</b>：列表页与试算平衡需要快速取数，
 * 若每次都聚合分录，凭证一多就会变慢。但落库的值<b>只能由服务层按分录汇总写入</b>，
 * 绝不接受前端传入——否则可以构造「合计平衡但分录不平衡」的坏数据。
 * </p>
 * <p>
 * <b>状态机</b>：DRAFT（可改分录）→ POSTED（锁定，参与余额）→ VOID（作废，退出余额）。
 * 已记账不允许改分录，只能作废重开，保证历史账目可审计。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_voucher", schema = "scrm", indexes = {
        @Index(name = "idx_voucher_company_period", columnList = "company_id,period_year,period_month"),
        @Index(name = "idx_voucher_status", columnList = "company_id,status"),
        @Index(name = "idx_voucher_date", columnList = "company_id,voucher_date")
})
@Data
public class ScrmVoucherEntity {

    /** 状态：草稿（分录可改，不参与余额） */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 状态：已记账（锁定，参与余额） */
    public static final String STATUS_POSTED = "POSTED";
    /** 状态：已作废（退出余额，保留痕迹） */
    public static final String STATUS_VOID = "VOID";

    /** 凭证字：记（通用） */
    public static final String WORD_JI = "记";

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

    /** 会计年度 */
    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    /** 会计月份（1-12） */
    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    /** 凭证字：记/收/付/转 */
    @Column(name = "voucher_word", nullable = false, length = 10)
    private String voucherWord = WORD_JI;

    /** 凭证号（同企业同期同字内唯一） */
    @Column(name = "voucher_no", nullable = false)
    private Integer voucherNo;

    /** 凭证日期 */
    @Column(name = "voucher_date", nullable = false)
    private LocalDate voucherDate;

    /** 借方合计（服务层按分录汇总写入） */
    @Column(name = "total_debit", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    /** 贷方合计（服务层按分录汇总写入） */
    @Column(name = "total_credit", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    /** 分录条数 */
    @Column(name = "entry_count", nullable = false)
    private Integer entryCount = 0;

    /** 状态：DRAFT/POSTED/VOID */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_DRAFT;

    /** 附件张数（会计手工声明的纸质附件数，非电子单据数） */
    @Column(name = "attachment_count", nullable = false)
    private Integer attachmentCount = 0;

    /**
     * 已挂载的电子单据份数（不落库，列表查询时批量回填）。
     * <p>
     * 与 {@link #attachmentCount} 是两个概念：后者是会计声明的纸质张数，
     * 本字段是真实可下载的原始单据份数。手上有 3 张纸质单据但只扫描 2 份时，
     * 两个数字本来就不该相等，因此分列展示而不是覆盖。
     * </p>
     */
    @jakarta.persistence.Transient
    private Integer attachmentFileCount;

    /**
     * 是否为期末结转损益凭证。
     * <p>
     * 结转凭证的分录同样落在损益类科目上（借收入/贷费用去清零），
     * 利润表按本期发生额聚合时必须排除它，否则「结账后利润变 0」。
     * 不能靠凭证字（转）判断——企业也可能用「转」字做正常业务。
     * </p>
     */
    @Column(name = "is_closing", nullable = false)
    private Boolean isClosing = false;

    /** 备注 */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 制单人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /** 记账人 */
    @Column(name = "posted_by", length = 100)
    private String postedBy;

    /** 记账时间 */
    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    /** 作废人 */
    @Column(name = "voided_by", length = 100)
    private String voidedBy;

    /** 作废时间 */
    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    /**
     * 凭证全称（如「记-0012」），列表与打印用。
     *
     * @return 凭证字号
     */
    @jakarta.persistence.Transient
    public String getVoucherLabel() {
        return voucherWord + "-" + String.format("%04d", voucherNo == null ? 0 : voucherNo);
    }

    /**
     * 借贷是否平衡。
     *
     * @return 平衡且金额大于 0 时返回 true
     */
    @jakarta.persistence.Transient
    public boolean isBalanced() {
        if (totalDebit == null || totalCredit == null) {
            return false;
        }
        return totalDebit.compareTo(BigDecimal.ZERO) > 0
                && totalDebit.compareTo(totalCredit) == 0;
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
            status = STATUS_DRAFT;
        }
        if (voucherWord == null || voucherWord.isBlank()) {
            voucherWord = WORD_JI;
        }
        if (isClosing == null) {
            isClosing = false;
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
