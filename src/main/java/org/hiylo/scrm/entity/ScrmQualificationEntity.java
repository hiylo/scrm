/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQualificationEntity.java
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
 * 资质许可实体（合规域）。
 * <p>
 * 一户多证：食品经营许可证、卫生许可、劳务派遣许可、消防验收等。
 * 代账公司的责任是<b>在证照到期前提醒客户续办</b>——客户因证照过期被处罚，
 * 代账公司要担责，因此到期提醒是这项业务的核心价值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_qualification", schema = "scrm", indexes = {
        @Index(name = "idx_qualification_company", columnList = "company_id"),
        @Index(name = "idx_qualification_status_expire", columnList = "status,expire_date")
})
@Data
public class ScrmQualificationEntity {

    /** 状态：有效 */
    public static final String STATUS_VALID = "VALID";
    /** 状态：即将到期（已进入提醒窗口） */
    public static final String STATUS_EXPIRING = "EXPIRING";
    /** 状态：已过期 */
    public static final String STATUS_EXPIRED = "EXPIRED";
    /** 状态：已续期 */
    public static final String STATUS_RENEWED = "RENEWED";
    /** 状态：已注销 */
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

    /** 公司名称（冗余） */
    @Column(name = "company_name", length = 200)
    private String companyName;

    /** 证照名称，如「食品经营许可证」 */
    @Column(name = "qualification_name", nullable = false, length = 200)
    private String qualificationName;

    /** 类型：FOOD/HEALTH/LABOR/FIRE/IMPORT_EXPORT/OTHER */
    @Column(name = "qualification_type", nullable = false, length = 50)
    private String qualificationType = "OTHER";

    /** 证照编号 */
    @Column(name = "cert_no", length = 100)
    private String certNo;

    /** 发证机关 */
    @Column(name = "issuer", length = 200)
    private String issuer;

    /** 发证日期 */
    @Column(name = "issue_date")
    private LocalDate issueDate;

    /** 到期日期（为空表示长期有效） */
    @Column(name = "expire_date")
    private LocalDate expireDate;

    /** 状态：VALID/EXPIRING/EXPIRED/RENEWED/CANCELED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_VALID;

    /** 到期前提醒天数（默认 60，留足续办时间） */
    @Column(name = "remind_days_before", nullable = false)
    private Integer remindDaysBefore = 60;

    /** 已提醒次数 */
    @Column(name = "remind_count", nullable = false)
    private Integer remindCount = 0;

    /** 最近提醒时间 */
    @Column(name = "last_reminded_at")
    private LocalDateTime lastRemindedAt;

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
            status = STATUS_VALID;
        }
        if (remindDaysBefore == null) {
            remindDaysBefore = 60;
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
