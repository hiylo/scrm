/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmAiProviderEntity.java
 * Date : 2026/11/07 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import org.hiylo.scrm.config.EncryptedStringConverter;

import java.time.LocalDateTime;

/**
 * AI Provider 注册表实体。
 * <p>
 * 统一管理模型接入点（Magpie / Ollama / OpenAI 兼容 / 本地 llama-server），
 * 模型能力（文本/多模态/向量）从 provider 配置获取，运行期按任务能力动态路由，
 * 不再与某个具体模型或环境变量硬绑定。全部配置落库，管理端可增删改查、
 * 连通测试、拉取模型列表、设默认——改完即时生效，无需改环境变量、无需重启。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_ai_provider", schema = "scrm", indexes = {
        @Index(name = "idx_ai_provider_enabled", columnList = "enabled"),
        @Index(name = "idx_ai_provider_priority", columnList = "priority")
})
@Data
public class ScrmAiProviderEntity {

    /** 主键 ID（Snowflake） */
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

    /** 乐观锁版本号 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Provider 名称（展示/选择用） */
    @Column(name = "provider_name", nullable = false, length = 100)
    private String providerName;

    /** Provider 类型：OPENAI_LLM / OLLAMA / MAGPIE / OPENAI_VISION / LOCAL_LLM */
    @Column(name = "provider_type", nullable = false, length = 30)
    private String providerType;

    /** API 基础地址（OpenAI 兼容；默认不含 /v1 前缀，由客户端按协议拼接） */
    @Column(name = "api_base_url", nullable = false, length = 500)
    private String apiBaseUrl;

    /** 对话补全路径（默认 /v1/chat/completions） */
    @Column(name = "chat_path", nullable = false, length = 100)
    private String chatPath = "/v1/chat/completions";

    /** 健康/模型探测路径（默认 /v1/models） */
    @Column(name = "health_path", nullable = false, length = 100)
    private String healthPath = "/v1/models";

    /** API 密钥（AES-GCM 加密存储，ENC: 前缀；空=无需鉴权） */
    @Column(name = "api_key", length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String apiKey;

    /** 能力位串：逗号分隔 text,image,embedding */
    @Column(name = "capability", nullable = false, length = 100)
    private String capability = "text";

    /** 默认模型名（如 group/sensenova-pool / qwen3:8b；空则由 /v1/models 自动推荐） */
    @Column(name = "default_model", length = 100)
    private String defaultModel;

    /** 采样温度（默认 0.7） */
    @Column(name = "temperature", nullable = false)
    private Double temperature = 0.7;

    /** 单次生成最大 token 数（默认 500） */
    @Column(name = "max_tokens", nullable = false)
    private Integer maxTokens = 500;

    /** 是否启用（false 不参与路由） */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    /** 是否默认文本 provider（全局仅一个） */
    @Column(name = "is_default_text", nullable = false)
    private Boolean defaultText = false;

    /** 是否默认多模态 provider（全局仅一个） */
    @Column(name = "is_default_image", nullable = false)
    private Boolean defaultImage = false;

    /** 路由优先级（数值越小越优先） */
    @Column(name = "priority", nullable = false)
    private Integer priority = 100;

    /** 连接状态：CONNECTED / DISCONNECTED / UNKNOWN */
    @Column(name = "connection_status", nullable = false, length = 20)
    private String connectionStatus = "UNKNOWN";

    /** 最后连通测试时间 */
    @Column(name = "last_tested_at")
    private LocalDateTime lastTestedAt;

    /** 最近模型清单缓存（JSON 数组，仅管理端展示） */
    @Column(name = "models_cache", columnDefinition = "text")
    private String modelsCache;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /** Provider 备注 */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 持久化前回调 */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
        if (chatPath == null || chatPath.isBlank()) {
            chatPath = "/v1/chat/completions";
        }
        if (healthPath == null || healthPath.isBlank()) {
            healthPath = "/v1/models";
        }
        if (capability == null || capability.isBlank()) {
            capability = "text";
        }
        if (temperature == null) {
            temperature = 0.7;
        }
        if (maxTokens == null) {
            maxTokens = 500;
        }
        if (enabled == null) {
            enabled = true;
        }
        if (priority == null) {
            priority = 100;
        }
        if (connectionStatus == null || connectionStatus.isBlank()) {
            connectionStatus = "UNKNOWN";
        }
    }

    /** 更新前回调 */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}