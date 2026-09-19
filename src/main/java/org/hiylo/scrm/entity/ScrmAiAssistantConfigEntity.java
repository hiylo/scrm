/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiAssistantConfigEntity.java
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

import java.time.LocalDateTime;

/**
 * SCRM AI 助手配置实体。
 * <p>
 * 描述一个 AI 服务对接配置: provider (OPENAI/AZURE/LOCAL/ZHIPU/QWEN) / model / apiKey
 * (加密存储) / apiEndpoint / systemPrompt / temperature / maxTokens。可选关联知识库
 * ({@link #knowledgeBaseId}) 用于检索增强问答。可同时存在多份配置, 通过
 * {@link #isDefault} 标记默认配置, {@link #requestCount} / {@link #lastUsedAt} 记录使用统计。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_ai_assistant_config", schema = "scrm", indexes = {
        @Index(name = "idx_ai_config_provider", columnList = "provider"),
        @Index(name = "idx_ai_config_enabled", columnList = "enabled"),
        @Index(name = "idx_ai_config_default", columnList = "is_default")
})
@Data
public class ScrmAiAssistantConfigEntity {

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

    /** 配置名称 */
    @Column(name = "config_name", nullable = false, length = 200)
    private String configName;

    /** 服务提供方: OPENAI / AZURE / LOCAL / ZHIPU / QWEN */
    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    /** 模型名称 (默认 gpt-4o-mini) */
    @Column(name = "model", nullable = false, length = 100)
    private String model;

    /** API Key (加密存储, 可空) */
    @Column(name = "api_key", length = 500)
    private String apiKey;

    /** API Endpoint (可空) */
    @Column(name = "api_endpoint", length = 500)
    private String apiEndpoint;

    /** 系统提示词 (可空) */
    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    /** 温度参数 (默认 0.7) */
    @Column(name = "temperature")
    private Double temperature;

    /** 最大 token 数 (默认 1000) */
    @Column(name = "max_tokens")
    private Integer maxTokens;

    /** 关联知识库 ID (可空) */
    @Column(name = "knowledge_base_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeBaseId;

    /** 是否启用 (默认 true) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 是否为默认配置 (默认 false) */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    /** 累计请求次数 */
    @Column(name = "request_count")
    private Integer requestCount;

    /** 最近使用时间 */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
