/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ObjectStorageAutoConfiguration.java
 * Date : 2026/09/18 11:24:39
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对象存储装配
 * <p>
 * 按 {@code scrm.storage.provider} 装配对应实现: {@code minio} (默认) 或 {@code oss}。
 * 两者互斥, 同一时刻只有一个 {@link ObjectStorage} bean; 外部可自定义实现覆盖。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class ObjectStorageAutoConfiguration {

    /**
     * 装配 MinIO 存储实现
     *
     * @param properties 存储配置
     * @return MinIO 存储实现
     */
    @Bean
    @ConditionalOnMissingBean(ObjectStorage.class)
    @ConditionalOnProperty(prefix = "scrm.storage", name = "provider",
            havingValue = "minio", matchIfMissing = true)
    public ObjectStorage minioObjectStorage(StorageProperties properties) {
        return new MinioObjectStorage(properties);
    }

    /**
     * 装配阿里云 OSS 存储实现
     *
     * @param properties 存储配置
     * @return OSS 存储实现
     */
    @Bean
    @ConditionalOnMissingBean(ObjectStorage.class)
    @ConditionalOnProperty(prefix = "scrm.storage", name = "provider", havingValue = "oss")
    public ObjectStorage ossObjectStorage(StorageProperties properties) {
        return new OssObjectStorage(properties);
    }
}
