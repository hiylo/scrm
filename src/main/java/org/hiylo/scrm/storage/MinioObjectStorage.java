/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : MinioObjectStorage.java
 * Date : 2026/09/18 11:24:39
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储实现
 * <p>
 * 基于 MinIO Java SDK, 实现 {@link ObjectStorage}。客户端构造不发起网络请求,
 * 因此在未配置 endpoint 时仍安全构造, 仅在实际操作时快速失败。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
public class MinioObjectStorage implements ObjectStorage {

    /** MinIO 客户端 (未配置 endpoint 时为 null) */
    private final MinioClient client;

    /** 存储配置 */
    private final StorageProperties properties;

    /**
     * 构造 MinIO 存储实现
     *
     * @param properties 存储配置
     */
    public MinioObjectStorage(StorageProperties properties) {
        this.properties = properties;
        this.client = buildClient(properties);
    }

    /**
     * 启动后尽力确保 bucket 存在, 失败不阻断启动
     */
    @PostConstruct
    public void init() {
        if (client == null) {
            log.warn("对象存储未配置 endpoint, MinIO 存储不可用 (scrm.storage.endpoint 为空)");
            return;
        }
        try {
            ensureBucket(properties.getBucket());
            log.info("MinIO 存储就绪: endpoint={}, bucket={}", normalizeEndpoint(properties), properties.getBucket());
        } catch (StorageException e) {
            log.warn("检查 / 创建 MinIO bucket 失败, 首次上传时将重试: {}", e.getMessage());
        }
    }

    /**
     * 按配置构造 MinIO 客户端, endpoint 为空时返回 null
     *
     * @param properties 存储配置
     * @return MinioClient 实例, 未配置时为 null
     */
    private static MinioClient buildClient(StorageProperties properties) {
        if (!properties.isEndpointConfigured()) {
            return null;
        }
        return MinioClient.builder()
                .endpoint(normalizeEndpoint(properties))
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    /**
     * endpoint 未带 scheme 时按 secure 补全, 带 scheme 时原样返回
     *
     * @param properties 存储配置
     * @return 规范化后的 endpoint
     */
    private static String normalizeEndpoint(StorageProperties properties) {
        String endpoint = properties.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            return "<未配置>";
        }
        endpoint = endpoint.trim();
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint;
        }
        return (properties.isSecure() ? "https://" : "http://") + endpoint;
    }

    /**
     * 获取客户端, 未配置时抛异常
     *
     * @return MinioClient 实例
     * @throws StorageException 未配置 endpoint
     */
    private MinioClient requireClient() {
        if (client == null) {
            throw new StorageException("对象存储未配置: scrm.storage.endpoint 为空");
        }
        return client;
    }

    @Override
    public String providerName() {
        return "minio";
    }

    @Override
    public void ensureBucket(String bucket) {
        MinioClient c = requireClient();
        try {
            if (!c.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                c.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("已创建 MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            throw new StorageException("确保 bucket 存在失败: " + bucket + ", " + e.getMessage(), e);
        }
    }

    @Override
    public void upload(String bucket, String objectKey, InputStream content, long size, String contentType) {
        MinioClient c = requireClient();
        PutObjectArgs.Builder builder = PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .stream(content, size, -1);
        if (contentType != null && !contentType.isBlank()) {
            builder.contentType(contentType);
        }
        try {
            c.putObject(builder.build());
        } catch (Exception e) {
            throw new StorageException("上传对象失败: " + objectKey + ", " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String bucket, String objectKey) {
        MinioClient c = requireClient();
        try {
            return c.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new StorageException("下载对象失败: " + objectKey + ", " + e.getMessage(), e);
        }
    }

    @Override
    public String presignedGetUrl(String bucket, String objectKey, int expirySeconds) {
        MinioClient c = requireClient();
        try {
            return c.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(expirySeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new StorageException("生成预签名 URL 失败: " + objectKey + ", " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String bucket, String objectKey) {
        MinioClient c = requireClient();
        try {
            c.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new StorageException("删除对象失败: " + objectKey + ", " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String bucket, String objectKey) {
        MinioClient c = requireClient();
        try {
            // statObject 成功即存在; 不判断 size, 否则 0 字节对象会被误判为不存在
            c.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
