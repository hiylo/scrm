/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoTagRuleEntity.java
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
 * SCRM 客户自动标签规则实体。
 * <p>
 * 定义基于客户属性与行为自动打标签的规则, 由 {@code ScrmAutoTagService} 在客户事件
 * (创建/更新/消息/生命周期变更/标签添加/交互超时) 触发时评估。规则由条件 (conditions JSON)
 * 与动作 (actionType + actionParams) 两部分组成, 按 priority 升序评估, enabled=false 的规则跳过。
 * </p>
 * <p>
 * conditions 为 JSON 数组: {@code [{field, operator, value}]}, field 如 lifecycle /
 * platformType / lastInteractionDays / tags / ownerAccountId, operator 如 eq/ne/in/
 * gt/lt/contains/between; conditionType=ALL 表示全部满足, ANY 表示任一满足。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_auto_tag_rule", schema = "scrm", indexes = {
        @Index(name = "idx_auto_tag_rule_event", columnList = "trigger_event"),
        @Index(name = "idx_auto_tag_rule_enabled", columnList = "enabled"),
        @Index(name = "idx_auto_tag_rule_priority", columnList = "priority")
})
@Data
public class ScrmAutoTagRuleEntity {

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
    @Column(name = "rule_name", length = 200, nullable = false)
    private String ruleName;

    /** 规则描述 */
    @Column(name = "description", length = 500)
    private String description;

/** 触发事件: CUSTOMER_CREATED / CUSTOMER_UPDATED / MESSAGE_RECEIVED / LIFECYCLE_CHANGED / TAG_ADDED /
         * INTERACTION_TIMEOUT */
    @Column(name = "trigger_event", length = 50, nullable = false)
    private String triggerEvent;

    /** 条件类型: ALL 所有条件满足 / ANY 任一满足 */
    @Column(name = "condition_type", length = 20, nullable = false)
    private String conditionType;

    /** 条件 JSON 数组: [{field, operator, value}] */
    @Column(name = "conditions", columnDefinition = "TEXT", nullable = false)
    private String conditions;

    /** 动作类型: ADD_TAG / REMOVE_TAG / SET_LIFECYCLE / NOTIFY */
    @Column(name = "action_type", length = 20, nullable = false)
    private String actionType;

    /** 动作参数 JSON: {tagIds:[], lifecycle:"", notifyUserId:""} */
    @Column(name = "action_params", columnDefinition = "TEXT", nullable = false)
    private String actionParams;

    /** 优先级（数字越小越优先, 默认 0） */
    @Column(name = "priority")
    private Integer priority;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 匹配次数（命中累计） */
    @Column(name = "match_count")
    private Integer matchCount;

    /** 最近匹配时间 */
    @Column(name = "last_match_at")
    private LocalDateTime lastMatchAt;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
