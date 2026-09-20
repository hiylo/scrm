/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagRuleEntity.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 客户标签自动规则实体。
 * <p>
 * 定义基于客户属性自动打标签的规则, 关联到目标标签 ({@link ScrmTagEntity})。规则由条件
 * (conditions JSON 数组: [{field, operator, value}]) 与条件组合类型 (conditionType:
 * ALL/ANY/NONE) 组成, 由 {@code ScrmTagSystemService.executeRule} 在指定频率
 * (executionFrequency: REALTIME/HOURLY/DAILY/WEEKLY/MANUAL) 下执行: 遍历客户 → 匹配
 * 条件 → 命中则打标。
 * </p>
 * <p>
 * conditions / targetFields 中字段名引用客户属性 (如 customer_name / order_count /
 * last_interaction / total_amount / registration_days)。matchedCount 与 lastExecutedAt
 * 由执行器维护, 用于规则统计与执行监控。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_tag_rule", schema = "scrm", indexes = {
        @Index(name = "idx_tag_rule_tag", columnList = "tag_id"),
        @Index(name = "idx_tag_rule_status", columnList = "status"),
        @Index(name = "idx_tag_rule_freq", columnList = "execution_frequency")
})
@Data
public class ScrmTagRuleEntity {

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

    /** 规则名称 */
    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    /** 关联标签 ID */
    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    /** 规则描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 条件组合类型: ALL 全部满足 / ANY 任一满足 / NONE 全部不满足 */
    @Column(name = "condition_type", nullable = false, length = 20)
    private String conditionType;

    /** 条件 JSON 数组: [{field, operator, value}] */
    @Column(name = "conditions", columnDefinition = "TEXT", nullable = false)
    private String conditions;

    /** 可用字段逗号分隔 (如 customer_name,order_count,last_interaction) */
    @Column(name = "target_fields", length = 500)
    private String targetFields;

    /** 执行频率: REALTIME / HOURLY / DAILY / WEEKLY / MANUAL */
    @Column(name = "execution_frequency", nullable = false, length = 20)
    private String executionFrequency;

    /** 最近执行时间 */
    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    /** 匹配客户数 (最近一次执行命中的客户数) */
    @Column(name = "matched_count")
    private Integer matchedCount;

    /** 状态: ACTIVE 活跃 / INACTIVE 停用 / DRAFT 草稿 */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
