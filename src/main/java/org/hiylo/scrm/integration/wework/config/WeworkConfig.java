/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkConfig.java
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.integration.wework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 企业微信平台配置类
 * <p>
 * 用于配置企业微信开放平台的连接参数，包括：
 * </p>
 * <ul>
 *   <li>CorpId - 企业ID,企业微信的唯一标识</li>
 *   <li>AgentId - 应用ID,企业微信应用的数字标识</li>
 *   <li>Secret - 应用Secret,用于获取 access_token,需保密</li>
 *   <li>AesKey - 消息加解密密钥(EncodingAESKey,43字符 Base64)</li>
 *   <li>Token - 回调Token,用于校验消息签名</li>
 *   <li>MockMode - 是否启用模拟模式(开发/测试使用)</li>
 *   <li>RealApiEnabled - 是否启用真实API调用</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "wework")
public class WeworkConfig {

    /** 企业ID,企业微信的唯一标识 */
    private String corpId;

    /** 应用ID(agentId),企业微信应用的数字标识 */
    private Integer agentId;

    /** 应用Secret,用于获取 access_token,需保密 */
    private String secret;

    /** 消息加解密密钥(EncodingAESKey,43字符 Base64) */
    private String aesKey;

    /** 回调Token,用于校验消息签名 */
    private String token;

    /** 企微 API 基础地址 */
    private String baseUrl = "https://qyapi.weixin.qq.com/cgi-bin";

    /** 是否启用真实 API 调用(关闭时回退为模拟路径) */
    private boolean realApiEnabled = false;

    /** 是否启用模拟模式(开发/测试使用) */
    private boolean mockMode = true;

    /** 请求超时时间(毫秒) */
    private int timeout = 30000;

    /** 重试次数 */
    private int retryCount = 3;

    /**
     * 无参构造器
     */
    public WeworkConfig() {
    }

    /**
     * 拷贝构造器
     *
     * @param other 源配置对象
     */
    public WeworkConfig(WeworkConfig other) {
        this.corpId = other.corpId;
        this.agentId = other.agentId;
        this.secret = other.secret;
        this.aesKey = other.aesKey;
        this.token = other.token;
        this.baseUrl = other.baseUrl;
        this.realApiEnabled = other.realApiEnabled;
        this.mockMode = other.mockMode;
        this.timeout = other.timeout;
        this.retryCount = other.retryCount;
    }

    /**
     * 验证配置是否完整(corpId/agentId/secret 必填)
     *
     * @return 配置是否完整
     */
    public boolean isValid() {
        return corpId != null && !corpId.isEmpty() && agentId != null && secret != null && !secret.isEmpty();
    }

    /**
     * 获取企微 API 完整地址
     *
     * @param endpoint 接口端点(以 / 开头)
     * @return 完整 API 地址
     */
    public String getApiUrl(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("API 端点不能为空");
        }
        String cleanEndpoint = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        return baseUrl + cleanEndpoint;
    }

    /**
     * 检查是否为模拟模式
     *
     * @return 是否为模拟模式
     */
    public boolean isMockMode() {
        return mockMode;
    }

    /**
     * 检查是否启用真实 API 调用
     *
     * @return 是否启用真实 API 调用
     */
    public boolean isRealApiEnabled() {
        return realApiEnabled;
    }
}
