/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProfileTemplateEntity.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
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
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.hibernate.annotations.GenericGenerator;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 画像模板实体。
 * <p>
 * 定义画像生成的模板配置, 包含维度权重 / 评分模型 / 标签规则 / 摘要模板 /
 * 最低置信度 / 更新频率。一个账号可配置多套模板, 其中至多一套为默认模板。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_profile_template", schema = "scrm", indexes = {
        @Index(name = "idx_profile_template_code", columnList = "template_code"),
        @Index(name = "idx_profile_template_enabled", columnList = "enabled")
})
@Data
public class ScrmProfileTemplateEntity {

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

    /** 模板编码 (全局唯一) */
    @Column(name = "template_code", nullable = false, length = 50)
    private String templateCode;

    /** 模板描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 适用客群 (可空) */
    @Column(name = "applicable_segment", length = 500)
    private String applicableSegment;

    /** 维度配置 JSON: [{dimension,weight,fields,scoringRules}] */
    @Column(name = "dimensions", nullable = false, columnDefinition = "TEXT")
    private String dimensions;

    /** 评分模型 JSON (可空) */
    @Column(name = "scoring_model", columnDefinition = "TEXT")
    private String scoringModel;

    /** 标签规则 JSON: [{condition,tags}] (可空) */
    @Column(name = "tag_rules", columnDefinition = "TEXT")
    private String tagRules;

    /** 摘要模板 (可空) */
    @Column(name = "summary_template", length = 1000)
    private String summaryTemplate;

    /** 最低置信度 (默认 0.5) */
    @Column(name = "min_confidence")
    private Double minConfidence;

    /** 更新频率: REALTIME / DAILY / WEEKLY / MONTHLY */
    @Column(name = "update_frequency", nullable = false, length = 20)
    private String updateFrequency;

    /** 是否默认模板 */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 使用次数 */
    @Column(name = "usage_count")
    private Integer usageCount;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
