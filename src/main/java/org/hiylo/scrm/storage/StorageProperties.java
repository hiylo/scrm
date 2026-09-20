/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : StorageProperties.java
 * Date : 2026/09/18 11:24:39
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储配置
 * <p>
 * 通过 {@code scrm.storage.*} 绑定, 凭证一律由环境变量注入, 仓库内不落任何真实值。
 * </p>
 * <ul>
 *   <li>provider - 存储实现: {@code minio} (默认) 或 {@code oss} (阿里云 OSS)</li>
 *   <li>endpoint - 服务端点。MinIO 形如 {@code http://host:9000}; OSS 形如 {@code oss-cn-hangzhou.aliyuncs.com}。
 *       未带 scheme 时按 {@code secure} 决定补 {@code https://} 或 {@code http://} (仅 MinIO 生效)</li>
 *   <li>accessKey / secretKey - 访问凭证</li>
 *   <li>bucket - 默认 bucket 名称</li>
 *   <li>secure - MinIO 是否走 HTTPS, 默认 false</li>
 *   <li>presignExpirySeconds - 预签名 URL 默认有效期 (秒), 默认 3600</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@ConfigurationProperties(prefix = "scrm.storage")
public class StorageProperties {

    /** 存储实现标识: minio (默认) 或 oss */
    private String provider = "minio";

    /** 服务端点 */
    private String endpoint;

    /** 访问密钥 ID */
    private String accessKey;

    /** 访问密钥密码 */
    private String secretKey;

    /** 默认 bucket 名称 */
    private String bucket = "scrm-assets";

    /** MinIO 是否使用 HTTPS */
    private boolean secure = false;

    /** 预签名 URL 默认有效期 (秒) */
    private int presignExpirySeconds = 3600;

    /**
     * 判断是否配置了服务端点
     *
     * @return 已配置返回 true
     */
    public boolean isEndpointConfigured() {
        return endpoint != null && !endpoint.isBlank();
    }
}
