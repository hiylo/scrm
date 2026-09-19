/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSurveyEntity.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 满意度调查问卷实体。
 * <p>
 * 描述一份 NPS / CSAT / CES / CUSTOM 调查问卷的元数据与题目配置。{@link #surveyType}
 * 标注调查类型, {@link #scaleType} 标注量表类型, {@link #questions} 为 JSON 字符串描述题目列表
 * (结构: {@code [{id,type,text,options,required,scale}]})。{@link #status} 标注问卷生命周期
 * (DRAFT/ACTIVE/PAUSED/COMPLETED/ARCHIVED)。
 * </p>
 * <p>
 * {@link #triggerEvent} / {@link #triggerDelayHours} / {@link #targetSegment} / {@link #channels}
 * 用于自动化分发配置; {@link #responseCount} / {@link #completionRate} 为运行时统计字段。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_survey", schema = "scrm", indexes = {
        @Index(name = "idx_survey_type", columnList = "survey_type"),
        @Index(name = "idx_survey_status", columnList = "status"),
        @Index(name = "idx_survey_trigger_event", columnList = "trigger_event"),
        @Index(name = "idx_survey_start_date", columnList = "start_date"),
        @Index(name = "idx_survey_end_date", columnList = "end_date")
})
@Data
public class ScrmSurveyEntity {

    // ==================== 公共字段 ====================

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

    /** 乐观锁版本号（并发更新保护, 后写入者触发 OptimisticLockException） */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 持久化前回调: 自动填充创建/更新时间与版本号初值
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
     * 更新前回调: 自动刷新更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // ==================== 业务字段 ====================

    /** 调查名称 */
    @Column(name = "survey_name", nullable = false, length = 200)
    private String surveyName;

    /** 调查类型: NPS 净推荐值 / CSAT 满意度 / CES 客户费力指数 / CUSTOM 自定义 */
    @Column(name = "survey_type", nullable = false, length = 20)
    private String surveyType;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 调查标题 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 介绍文案 (可空) */
    @Column(name = "intro_text", columnDefinition = "TEXT")
    private String introText;

    /** 结束文案 (可空) */
    @Column(name = "outro_text", columnDefinition = "TEXT")
    private String outroText;

    /** JSON 问题列表: [{id,type,text,options,required,scale}] */
    @Column(name = "questions", nullable = false, columnDefinition = "TEXT")
    private String questions;

    /** 量表类型: NPS_0_10 / CSAT_1_5 / CES_1_7 (可空) */
    @Column(name = "scale_type", length = 20)
    private String scaleType;

    /** 触发事件: PURCHASE / SERVICE_TICKET / FIRST_CONTACT / MANUAL (可空) */
    @Column(name = "trigger_event", length = 50)
    private String triggerEvent;

    /** 触发延迟小时 (默认 0) */
    @Column(name = "trigger_delay_hours")
    private Integer triggerDelayHours;

    /** 目标客群条件 JSON (可空) */
    @Column(name = "target_segment", length = 500)
    private String targetSegment;

    /** 分发渠道: IN_APP / SMS / EMAIL / WECHAT (可空) */
    @Column(name = "channels", length = 200)
    private String channels;

    /** 预计完成时间分钟 (默认 2) */
    @Column(name = "estimated_time_minutes")
    private Integer estimatedTimeMinutes;

    /** 开始日期 (可空) */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** 结束日期 (可空) */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** 状态: DRAFT / ACTIVE / PAUSED / COMPLETED / ARCHIVED (默认 DRAFT) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 回复数 (默认 0) */
    @Column(name = "response_count")
    private Integer responseCount;

    /** 完成率 (默认 0) */
    @Column(name = "completion_rate")
    private Double completionRate;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
