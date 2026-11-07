/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmAccountPeriodEntity.java
 * Date : 2026/10/25 21:00:00
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
 * 会计期间实体（凭证域）。
 * <p>
 * <b>为什么要单独建表记录期间状态</b>：结账之后该期间的账必须封住——
 * 再补凭证会让已经出过的报表与账面不符，结转凭证也会失真。
 * 靠「结转凭证存不存在」去推断是不可靠的（可能被手工作废）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_account_period", schema = "scrm", indexes = {
        @Index(name = "idx_period_company", columnList = "company_id,period_year,period_month")
})
@Data
public class ScrmAccountPeriodEntity {

    /** 状态：未结账 */
    public static final String STATUS_OPEN = "OPEN";
    /** 状态：已结账（禁止新增凭证） */
    public static final String STATUS_CLOSED = "CLOSED";

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

    /** 会计年度 */
    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    /** 会计月份（1-12） */
    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    /** 状态：OPEN/CLOSED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_OPEN;

    /** 结转损益凭证 ID（无损益发生额时为空） */
    @Column(name = "closing_voucher_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long closingVoucherId;

    /** 结账人 */
    @Column(name = "closed_by", length = 100)
    private String closedBy;

    /** 结账时间 */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** 反结账人 */
    @Column(name = "reopened_by", length = 100)
    private String reopenedBy;

    /** 反结账时间 */
    @Column(name = "reopened_at")
    private LocalDateTime reopenedAt;

    /** 备注 */
    @Column(name = "remark", length = 500)
    private String remark;

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
            status = STATUS_OPEN;
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
