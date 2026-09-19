/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ObjectStorage.java
 * Date : 2026/09/18 11:24:39
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.storage;

import java.io.InputStream;

/**
 * 对象存储统一抽象
 * <p>
 * 屏蔽底层存储实现差异, 当前提供 MinIO 与阿里云 OSS 两种实现,
 * 由 {@code scrm.storage.provider} 决定装配哪一个。业务代码只依赖本接口,
 * 不感知 SDK 类型, 便于按部署环境在两种存储间切换。
 * </p>
 * <p>
 * 所有方法失败时抛出 {@link StorageException}, 由调用方转换为业务异常。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ObjectStorage {

    /**
     * 获取存储实现标识
     *
     * @return 实现标识 (如 minio / oss)
     */
    String providerName();

    /**
     * 确保 bucket 存在, 不存在则创建
     * <p>
     * 实现方应保证本方法幂等; 存储不可达时抛出 {@link StorageException}。
     * </p>
     *
     * @param bucket bucket 名称
     */
    void ensureBucket(String bucket);

    /**
     * 上传对象
     *
     * @param bucket      bucket 名称
     * @param objectKey   对象 key
     * @param content     内容输入流 (实现方不负责关闭, 由调用方管理)
     * @param size        内容长度 (字节), 小于 0 表示未知
     * @param contentType MIME 类型, 允许为空
     * @throws StorageException 上传失败
     */
    void upload(String bucket, String objectKey, InputStream content, long size, String contentType);

    /**
     * 下载对象
     *
     * @param bucket    bucket 名称
     * @param objectKey 对象 key
     * @return 内容输入流 (调用方负责关闭)
     * @throws StorageException 下载失败
     */
    InputStream download(String bucket, String objectKey);

    /**
     * 生成 GET 预签名 URL
     *
     * @param bucket       bucket 名称
     * @param objectKey    对象 key
     * @param expirySeconds 过期时间 (秒)
     * @return 预签名 URL
     * @throws StorageException 生成失败
     */
    String presignedGetUrl(String bucket, String objectKey, int expirySeconds);

    /**
     * 删除对象
     *
     * @param bucket    bucket 名称
     * @param objectKey 对象 key
     * @throws StorageException 删除失败
     */
    void delete(String bucket, String objectKey);

    /**
     * 判断对象是否存在
     * <p>
     * 存储端返回不存在或查询失败时返回 false; 客户端未配置时抛出 {@link StorageException},
     * 避免把"存储不可用"误报为"对象不存在"。
     * </p>
     *
     * @param bucket    bucket 名称
     * @param objectKey 对象 key
     * @return 存在返回 true
     * @throws StorageException 客户端未配置
     */
    boolean exists(String bucket, String objectKey);
}
