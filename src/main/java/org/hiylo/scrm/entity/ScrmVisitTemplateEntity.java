/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVisitTemplateEntity.java
 * Date : 2026/08/05 08:55:12
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 客户回访模板实体。
 * <p>
 * 描述回访话术模板: 模板基础信息 ({@link #templateName} / {@link #templateCode} /
 * {@link #description}), 适用场景 ({@link #visitType} / {@link #visitMethod}),
 * 话术内容 ({@link #questions} / {@link #introduction} / {@link #closing} /
 * {@link #successCriteria}), 配置 ({@link #estimatedDurationMinutes} /
 * {@link #applicableProducts} / {@link #tags}), 统计 ({@link #usageCount} /
 * {@link #avgSatisfactionScore}), 状态 ({@link #enabled})。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_visit_template", schema = "scrm",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_visit_template_code",
                columnNames = {"template_code"}),
        indexes = {
                @Index(name = "idx_visit_template_type", columnList = "visit_type"),
                @Index(name = "idx_visit_template_method", columnList = "visit_method"),
                @Index(name = "idx_visit_template_enabled", columnList = "enabled")
        })
/**
 * SCRM 客户回访模板实体。
 * <p>沉淀可复用的回访话术模板: 模板名称与编码、描述、适用回访类型与回访方式、
 * 开场介绍、沟通问题清单、结束语、成功判定标准、预计时长、
 * 是否启用与排序、备注。模板编码 (templateCode) 唯一。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmVisitTemplateEntity {

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

    /** 模板名称 */
    @Column(name = "template_name", nullable = false, length = 200)
    private String templateName;

    /** 模板编码 (唯一) */
    @Column(name = "template_code", nullable = false, length = 50)
    private String templateCode;

    /** 模板描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 回访类型: REGULAR/FOLLOW_UP/SATISFACTION/RENEWAL/UPSELL/CROSS_SELL/CARE/COMPLAINT_FOLLOWUP/CUSTOM */
    @Column(name = "visit_type", nullable = false, length = 30)
    private String visitType;

    /** 回访方式: PHONE/ON_SITE/VIDEO/WECHAT/EMAIL/SMS/MIXED */
    @Column(name = "visit_method", nullable = false, length = 30)
    private String visitMethod;

    /** 问题列表 JSON: [{id,question,type,required,options,skipCondition}] */
    @Column(name = "questions", nullable = false, columnDefinition = "TEXT")
    private String questions;

    /** 开场白 (可空) */
    @Column(name = "introduction", length = 1000)
    private String introduction;

    /** 结束语 (可空) */
    @Column(name = "closing", length = 1000)
    private String closing;

    /** 成功标准 (可空) */
    @Column(name = "success_criteria", length = 500)
    private String successCriteria;

    /** 预计时长分钟 (默认 15) */
    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    /** 适用产品 (逗号分隔, 可空) */
    @Column(name = "applicable_products", length = 500)
    private String applicableProducts;

    /** 标签 (逗号分隔, 可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 使用次数 (默认 0) */
    @Column(name = "usage_count")
    private Integer usageCount;

    /** 平均满意度 (默认 0) */
    @Column(name = "avg_satisfaction_score")
    private Double avgSatisfactionScore;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
