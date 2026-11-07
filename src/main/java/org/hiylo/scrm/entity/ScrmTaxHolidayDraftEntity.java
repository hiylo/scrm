/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxHolidayDraftEntity.java
 * Date : 2026/10/27 14:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * 假期 AI 抽取草稿。
 * <p>
 * AI 从国务院办公厅公告中抽取出的假期与调休安排只落在本表，
 * <b>绝不直接写入 {@code scrm_tax_holiday}</b>：假期数据决定申报截止日与逾期判定，
 * 属国家级硬事实，必须人工核对后由人提交。
 * </p>
 * <p>
 * 保留公告原文与模型原始输出，是为了人工核对时有据可查；模型幻觉（编造不存在的假期、
 * 算错区间）在纯结构化数据上很难发现，只有对照原文才能识别。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_tax_holiday_draft", schema = "scrm", indexes = {
        @Index(name = "idx_th_draft_year_status", columnList = "holiday_year,draft_status"),
})
@Getter
@Setter
public class ScrmTaxHolidayDraftEntity {

    /** 状态: 待确认 */
    public static final String STATUS_PENDING = "PENDING";

    /** 状态: 已采纳（已写入生效表） */
    public static final String STATUS_APPLIED = "APPLIED";

    /** 状态: 已丢弃 */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 抽取方式: AI 抽取 */
    public static final String MODE_AI = "AI";

    /** 抽取方式: 手工录入 */
    public static final String MODE_MANUAL = "MANUAL";

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

    /** 目标年份 */
    @Column(name = "holiday_year", nullable = false)
    private Integer holidayYear;

    /** 来源公告标题 */
    @Column(name = "source_title", length = 500)
    private String sourceTitle;

    /** 来源公告 URL */
    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    /** 公告原文（截断保存，用于核对） */
    @Column(name = "source_text", columnDefinition = "text")
    private String sourceText;

    /** 模型原始输出（不解析，排查幻觉用） */
    @Column(name = "raw_model_output", columnDefinition = "text")
    private String rawModelOutput;

    /** 假期明细 JSON: [{name,startDate,endDate}] */
    @Column(name = "holidays_json", columnDefinition = "text")
    private String holidaysJson;

    /** 调休上班日 JSON: [{workDate,remark}] */
    @Column(name = "workdays_json", columnDefinition = "text")
    private String workdaysJson;

    /** 抽取方式：AI / MANUAL */
    @Column(name = "extract_mode", nullable = false, length = 20)
    private String extractMode = MODE_AI;

    /** 草稿状态：PENDING / APPLIED / REJECTED */
    @Column(name = "draft_status", nullable = false, length = 20)
    private String draftStatus = STATUS_PENDING;

    /** 模型标识 */
    @Column(name = "model_name", length = 100)
    private String modelName;

    /** 人工处理人 */
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    /** 人工处理时间 */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 人工处理备注 */
    @Column(name = "review_remark", length = 500)
    private String reviewRemark;

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
    }

    /**
     * 更新前回调：刷新更新时间。
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
