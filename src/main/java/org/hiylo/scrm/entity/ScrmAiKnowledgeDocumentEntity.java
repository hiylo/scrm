/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiKnowledgeDocumentEntity.java
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
 * SCRM AI 知识库文档实体。
 * <p>
 * 知识库内的单条文档: {@link #content} 为正文 (TEXT/MARKDOWN/JSON), {@link #tags}
 * (逗号分隔) 用于辅助检索, {@link #qaPairs} (JSON 问答对) 用于 FAQ 类问答匹配。
 * {@link #sourceType} (MANUAL/IMPORT/URL) 标注来源, {@link #sourceUrl} 记录 URL 来源。
 * {@link #viewCount} / {@link #lastUsedAt} 记录使用统计。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_ai_knowledge_document", schema = "scrm", indexes = {
        @Index(name = "idx_ai_doc_kb", columnList = "knowledge_base_id"),
        @Index(name = "idx_ai_doc_enabled", columnList = "enabled"),
        @Index(name = "idx_ai_doc_source", columnList = "source_type")
})
@Data
public class ScrmAiKnowledgeDocumentEntity {

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

    /** 所属知识库 ID */
    @Column(name = "knowledge_base_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeBaseId;

    /** 文档标题 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 文档内容 */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 内容类型: TEXT / MARKDOWN / JSON (默认 TEXT) */
    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    /** 标签 (逗号分隔, 可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 来源类型: MANUAL / IMPORT / URL (默认 MANUAL) */
    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    /** 来源 URL (可空) */
    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    /** 问答对 (JSON 字符串, 可空) */
    @Column(name = "qa_pairs", columnDefinition = "TEXT")
    private String qaPairs;

    /** 是否启用 (默认 true) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 浏览次数 (默认 0) */
    @Column(name = "view_count")
    private Integer viewCount;

    /** 最近使用时间 */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
