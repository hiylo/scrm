/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmAnnualReportEntity.java
 * Date : 2026/10/25 10:00:00
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
 * 工商年报实体（合规域）。
 * <p>
 * 一户一年一条记录，由调度每年 1 月 1 日为在服企业统一生成，
 * 截止日为当年 6 月 30 日；逾期未报送会进入经营异常名录。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_annual_report", schema = "scrm", indexes = {
        @Index(name = "idx_annual_company", columnList = "company_id"),
        @Index(name = "idx_annual_status_due", columnList = "status,due_date")
})
@Data
public class ScrmAnnualReportEntity {

    /** 状态：待报送 */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态：已报送 */
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    /** 状态：已逾期 */
    public static final String STATUS_OVERDUE = "OVERDUE";
    /** 状态：免报（当年注销等，不计入逾期） */
    public static final String STATUS_EXEMPT = "EXEMPT";
    /** 状态：已撤销 */
    public static final String STATUS_CANCELED = "CANCELED";

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

    /** 公司名称（冗余，避免列表页回查主体表） */
    @Column(name = "company_name", length = 200)
    private String companyName;

    /** 报送年度；2025 表示 2025 年度报告，截止 2026-06-30 */
    @Column(name = "report_year", nullable = false)
    private Integer reportYear;

    /** 报送截止日（固定为次年 6 月 30 日） */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** 状态：PENDING/SUBMITTED/OVERDUE/EXEMPT/CANCELED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    /** 实际报送时间 */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** 报送操作人 */
    @Column(name = "submitted_by", length = 100)
    private String submittedBy;

    /** 公示系统回执编号 */
    @Column(name = "receipt_no", length = 100)
    private String receiptNo;

    /** 备注（免报原因、逾期处理说明等） */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 已提醒次数 */
    @Column(name = "remind_count", nullable = false)
    private Integer remindCount = 0;

    /** 最近提醒时间 */
    @Column(name = "last_reminded_at")
    private LocalDateTime lastRemindedAt;

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
        if (remindCount == null) {
            remindCount = 0;
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
