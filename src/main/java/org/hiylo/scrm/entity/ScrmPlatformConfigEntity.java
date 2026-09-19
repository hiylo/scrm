/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPlatformConfigEntity.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Version : V1.0
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;
import org.hiylo.scrm.config.EncryptedStringConverter;

import java.time.LocalDateTime;

/**
 * SCRM 平台配置实体。
 * <p>
 * 存储各平台（企微/抖音/快手/小红书等）的连接配置信息，每平台类型一条记录。
 * 配置优先级：数据库记录 > YAML 文件默认值。支持连接测试与状态追踪。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_platform_config", schema = "scrm", indexes = {
        @Index(name = "idx_platconfig_platform_type", columnList = "platform_type")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_platconfig_platform", columnNames = {"platform_type"})
})
@Data
public class ScrmPlatformConfigEntity {

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
    }

    /**
     * 更新前回调: 自动刷新更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // ==================== 业务字段 ====================

    /** 平台类型：WEWORK / DOUYIN / KUAISHOU / XIAOHONGSHU / BILIBILI / WECHAT_PERSONAL */
    @Column(name = "platform_type", nullable = false, length = 30)
    private String platformType;

    /** 企业ID（企微 CorpID） */
    @Column(name = "corp_id", length = 200)
    private String corpId;

    /** 应用ID（企微 AgentID） */
    @Column(name = "agent_id")
    private Integer agentId;

    /** 应用Secret（企微应用密钥，用于获取 access_token） */
    @Column(name = "secret", length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String secret;

    /** 消息加解密密钥（EncodingAESKey，43字符 Base64） */
    @Column(name = "aes_key", length = 100)
    @Convert(converter = EncryptedStringConverter.class)
    private String aesKey;

    /** 回调 Token（用于校验消息签名） */
    @Column(name = "token", length = 200)
    @Convert(converter = EncryptedStringConverter.class)
    private String token;

    /** 平台 API 基础地址 */
    @Column(name = "base_url", length = 500)
    private String baseUrl;

    /** 回调 URL（企微回调推送地址） */
    @Column(name = "callback_url", length = 500)
    private String callbackUrl;

    /** 是否启用模拟模式（开发/测试使用） */
    @Column(name = "mock_mode", nullable = false)
    private boolean mockMode = true;

    /** 是否启用真实 API 调用（关闭时回退为模拟路径） */
    @Column(name = "real_api_enabled", nullable = false)
    private boolean realApiEnabled = false;

    /** 请求超时时间（毫秒） */
    @Column(name = "timeout", nullable = false)
    private int timeout = 30000;

    /** 重试次数 */
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 3;

    /** 连接状态：CONNECTED / DISCONNECTED / UNKNOWN */
    @Column(name = "connection_status", length = 20)
    private String connectionStatus;

    /** 最后连接测试时间 */
    @Column(name = "last_tested_at")
    private LocalDateTime lastTestedAt;
}
