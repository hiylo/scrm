/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiIntentEntity.java
 * Date : 2026/07/27 02:41:22
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

import java.time.LocalDateTime;

/**
 * SCRM AI 意图实体。
 * <p>
 * 定义客户消息意图识别规则: {@link #intentCategory} (INQUIRY/COMPLAINT/PURCHASE/SUPPORT/
 * FEEDBACK/GREETING/FAREWELL/QUESTION/REQUEST) 标注意图类别, {@link #keywords} (逗号分隔)
 * 与 {@link #examples} (JSON 数组示例文本) 用于关键词匹配与示例相似度计算。
 * {@link #responseTemplate} 为命中意图后的推荐回复模板, {@link #suggestedAction}
 * (RECOMMEND_PRODUCT/CREATE_TICKET/ASSIGN_AGENT/ESCALATE/NONE) 为建议动作。
 * {@link #priority} 越大越优先, {@link #matchCount} 记录命中累计。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_ai_intent", schema = "scrm", indexes = {
        @Index(name = "idx_ai_intent_category", columnList = "intent_category"),
        @Index(name = "idx_ai_intent_enabled", columnList = "enabled"),
        @Index(name = "idx_ai_intent_priority", columnList = "priority")
})
@Data
public class ScrmAiIntentEntity {

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

    /** 意图名称 */
    @Column(name = "intent_name", nullable = false, length = 200)
    private String intentName;

    /** 意图类别: INQUIRY / COMPLAINT / PURCHASE / SUPPORT / FEEDBACK / GREETING / FAREWELL / QUESTION / REQUEST */
    @Column(name = "intent_category", nullable = false, length = 50)
    private String intentCategory;

    /** 关键词 (逗号分隔, 可空) */
    @Column(name = "keywords", length = 1000)
    private String keywords;

    /** 示例文本 (JSON 数组, 可空) */
    @Column(name = "examples", columnDefinition = "TEXT")
    private String examples;

    /** 推荐回复模板 (可空) */
    @Column(name = "response_template", length = 2000)
    private String responseTemplate;

    /** 建议动作: RECOMMEND_PRODUCT / CREATE_TICKET / ASSIGN_AGENT / ESCALATE / NONE */
    @Column(name = "suggested_action", length = 50)
    private String suggestedAction;

    /** 优先级 (数字越大越优先, 默认 0) */
    @Column(name = "priority")
    private Integer priority;

    /** 是否启用 (默认 true) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 匹配次数 (命中累计) */
    @Column(name = "match_count")
    private Integer matchCount;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
