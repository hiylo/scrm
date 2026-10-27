/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxHolidayEntity.java
 * Date : 2026/10/27 10:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 税务假期（法定节假日区间）。
 * <p>
 * 只服务于「申报截止日顺延」，与营销用的 {@code scrm_calendar_holiday} 分开：
 * 后者是节日营销库（支持固定 MM-dd、农历、营销建议等），语义和数据结构都不同。
 * </p>
 * <p>
 * 假期用<b>起止日期</b>表示而不是「起始日 + 天数」：法定节假日每年由国务院公告，
 * 常伴随调休，跨天区间直接给定最不容易算错。周末补班另行记录在
 * {@code scrm_tax_workday_override}，否则补班日会被误判为休息日。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_tax_holiday", schema = "scrm", indexes = {
        @Index(name = "idx_tax_holiday_year", columnList = "holiday_year,start_date"),
})
@Getter
@Setter
public class ScrmTaxHolidayEntity {

    /** 假期类型: 法定节假日 */
    public static final String KIND_STATUTORY = "STATUTORY";

    /** 假期类型: 调休放假 */
    public static final String KIND_ADJUSTED = "ADJUSTED";

    // ==================== 公共字段 ====================

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

    // ==================== 业务字段 ====================

    /** 节假日名称 */
    @Column(name = "holiday_name", nullable = false, length = 100)
    private String holidayName;

    /** 所属年份（便于按年核对数据是否齐备） */
    @Column(name = "holiday_year", nullable = false)
    private Integer holidayYear;

    /** 假期起始日（含） */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 假期结束日（含） */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 假期类型：STATUTORY 法定 / ADJUSTED 调休放假 */
    @Column(name = "holiday_kind", nullable = false, length = 20)
    private String holidayKind = KIND_STATUTORY;

    /** 说明（如通知文号） */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 是否启用 */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    /**
     * 持久化前回调：填充创建/更新时间与版本号初值。
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

    /**
     * 判断指定日期是否落在本假期区间内（含首尾）。
     *
     * @param date 待判断日期
     * @return true = 在假期内
     */
    public boolean contains(LocalDate date) {
        if (date == null || startDate == null || endDate == null) {
            return false;
        }
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
