/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxWorkdayOverrideEntity.java
 * Date : 2026/10/27 10:35:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 调休上班日（周末补班）。
 * <p>
 * 法定节假日常伴随调休：部分周六需要正常上班。若只处理「周末 + 假期」，
 * 补班日会被误判为休息日，把本应落在当天的申报截止日往后推，
 * 进而导致逾期判定错误。因此单独记录这类日期。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_tax_workday_override", schema = "scrm",
        uniqueConstraints = @UniqueConstraint(name = "uk_tax_workday_override", columnNames = "work_date"))
@Getter
@Setter
public class ScrmTaxWorkdayOverrideEntity {

    /** 主键 ID（Snowflake 雪花算法生成） */
    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", strategy = "org.hiylo.scrm.config.SnowflakeIdGenerator")
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(
            using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
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

    /** 需上班的日期（通常是周末） */
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** 说明 */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 是否启用 */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    /**
     * 持久化前回调：填充审计字段。
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
        if (isActive == null) {
            isActive = Boolean.TRUE;
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
