/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : OssObjectStorage.java
 * Date : 2026/09/18 11:24:39
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.storage;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

/**
 * 阿里云 OSS 对象存储实现
 * <p>
 * 基于 aliyun-sdk-oss, 实现 {@link ObjectStorage}。客户端构造不发起网络请求,
 * 因此在未配置 endpoint 时仍安全构造, 仅在实际操作时快速失败。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
public class OssObjectStorage implements ObjectStorage {

    /** OSS 客户端 (未配置 endpoint 时为 null) */
    private final OSS client;

    /** 存储配置 */
    private final StorageProperties properties;

    /**
     * 构造 OSS 存储实现
     *
     * @param properties 存储配置
     */
    public OssObjectStorage(StorageProperties properties) {
        this.properties = properties;
        this.client = buildClient(properties);
    }

    /**
     * 启动后尽力确保 bucket 存在, 失败不阻断启动
     */
    @PostConstruct
    public void init() {
        if (client == null) {
            log.warn("对象存储未配置 endpoint, OSS 存储不可用 (scrm.storage.endpoint 为空)");
            return;
        }
        try {
            ensureBucket(properties.getBucket());
            log.info("OSS 存储就绪: endpoint={}, bucket={}", properties.getEndpoint(), properties.getBucket());
        } catch (StorageException e) {
            log.warn("检查 / 创建 OSS bucket 失败, 首次上传时将重试: {}", e.getMessage());
        }
    }

    /**
     * 按配置构造 OSS 客户端, endpoint 为空时返回 null
     *
     * @param properties 存储配置
     * @return OSS 客户端实例, 未配置时为 null
     */
    private static OSS buildClient(StorageProperties properties) {
        if (!properties.isEndpointConfigured()) {
            return null;
        }
        return new OSSClientBuilder().build(
                properties.getEndpoint().trim(),
                properties.getAccessKey(),
                properties.getSecretKey());
    }

    /**
     * 获取客户端, 未配置时抛异常
     *
     * @return OSS 客户端实例
     * @throws StorageException 未配置 endpoint
     */
    private OSS requireClient() {
        if (client == null) {
            throw new StorageException("对象存储未配置: scrm.storage.endpoint 为空");
        }
        return client;
    }

    @Override
    public String providerName() {
        return "oss";
    }

    @Override
    public void ensureBucket(String bucket) {
        OSS c = requireClient();
        try {
            if (!c.doesBucketExist(bucket)) {
                c.createBucket(bucket);
                log.info("已创建 OSS bucket: {}", bucket);
            }
        } catch (OSSException | ClientException e) {
            throw new StorageException("确保 bucket 存在失败: " + bucket + ", " + e.getMessage(), e);
        }
    }

    @Override
    public void upload(String bucket, String objectKey, InputStream content, long size, String contentType) {
        OSS c = requireClient();
        ObjectMetadata metadata = new ObjectMetadata();
        if (size >= 0) {
            metadata.setContentLength(size);
        }
        if (contentType != null && !contentType.isBlank()) {
            metadata.setContentType(contentType);
        }
        try {
            c.putObject(bucket, objectKey, content, metadata);
        } catch (OSSException | ClientException e) {
            throw new StorageException("上传对象失败: " + objectKey + ", " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String bucket, String objectKey) {
        OSS c = requireClient();
        OSSObject object;
        try {
            object = c.getObject(bucket, objectKey);
        } catch (OSSException | ClientException e) {
            throw new StorageException("下载对象失败: " + objectKey + ", " + e.getMessage(), e);
        }
        // 包装流: 调用方关闭时同步释放 OSSObject 持有的连接资源
        InputStream content = object.getObjectContent();
        return new FilterInputStream(content) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    try {
                        object.close();
                    } catch (Exception ignored) {
                        // 释放资源失败不影响业务, 连接由 SDK 连接池回收
                    }
                }
            }
        };
    }

    @Override
    public String presignedGetUrl(String bucket, String objectKey, int expirySeconds) {
        OSS c = requireClient();
        Date expiration = new Date(System.currentTimeMillis() + expirySeconds * 1000L);
        try {
            return c.generatePresignedUrl(bucket, objectKey, expiration).toString();
        } catch (OSSException | ClientException e) {
            throw new StorageException("生成预签名 URL 失败: " + objectKey + ", " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String bucket, String objectKey) {
        OSS c = requireClient();
        try {
            c.deleteObject(bucket, objectKey);
        } catch (OSSException | ClientException e) {
            throw new StorageException("删除对象失败: " + objectKey + ", " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String bucket, String objectKey) {
        OSS c = requireClient();
        try {
            return c.doesObjectExist(bucket, objectKey);
        } catch (OSSException | ClientException e) {
            return false;
        }
    }
}
