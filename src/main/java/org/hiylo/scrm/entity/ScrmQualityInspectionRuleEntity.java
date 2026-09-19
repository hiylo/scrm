/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQualityInspectionRuleEntity.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 质检规则实体。
 * <p>
 * 定义会话质检的评估规则, 按类别 (话术规范/服务态度/敏感词/响应时长/专业度/合规) 维度配置,
 * 规则类型支持关键词匹配、正则、会话时长、响应时长与 AI 评估。
 * ruleConfig 为 JSON 字符串, 承载各规则类型的配置参数。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_quality_inspection_rule", schema = "scrm", indexes = {
        @Index(name = "idx_qi_rule_category", columnList = "category"),
        @Index(name = "idx_qi_rule_enabled", columnList = "enabled")
})
@Data
public class ScrmQualityInspectionRuleEntity {

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

    /** 乐观锁版本号（并发更新保护，后写入者触发 OptimisticLockException） */
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
        if (enabled == null) {
            enabled = true;
        }
        if (passCondition == null || passCondition.isBlank()) {
            passCondition = "GTE:80";
        }
        if (scoreWeight == null) {
            scoreWeight = 1.0;
        }
        if (matchCount == null) {
            matchCount = 0;
        }
        if (passCount == null) {
            passCount = 0;
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

    /** 规则名称 */
    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    /** 质检类别: SCRIPT_COMPLIANCE/SERVICE_ATTITUDE/SENSITIVE_WORD/RESPONSE_TIME/PROFESSIONALISM/COMPLIANCE */
    @Column(name = "category", nullable = false, length = 30)
    private String category;

    /** 规则描述（可空） */
    @Column(name = "description", length = 500)
    private String description;

    /** 规则类型: KEYWORD_MATCH/REGEX/DURATION/RESPONSE_TIME/AI_EVALUATE */
    @Column(name = "rule_type", nullable = false, length = 20)
    private String ruleType;

    /** 规则配置 JSON: {keywords:[], regex:"", maxResponseSeconds:300, promptTemplate:"", scoreWeight:1.0} */
    @Column(name = "rule_config", nullable = false, columnDefinition = "TEXT")
    private String ruleConfig;

    /** 通过条件: GTE:80/LTE:30/CONTAINS/NOT_CONTAINS */
    @Column(name = "pass_condition", nullable = false, length = 20)
    private String passCondition;

    /** 评分权重（默认 1.0） */
    @Column(name = "score_weight", nullable = false)
    private Double scoreWeight;

    /** 是否启用（默认 TRUE） */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 累计匹配次数 */
    @Column(name = "match_count")
    private Integer matchCount;

    /** 累计通过次数 */
    @Column(name = "pass_count")
    private Integer passCount;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
