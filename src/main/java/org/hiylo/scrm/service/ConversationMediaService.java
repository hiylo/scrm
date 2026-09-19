/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ConversationMediaService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.storage.ObjectStorage;
import org.hiylo.scrm.storage.StorageException;
import org.hiylo.scrm.storage.StorageProperties;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 会话媒体存储服务
 * <p>
 * 负责会话消息中媒体文件（图片 / 语音 / 视频 / 文件）的上传、下载、预签名 URL 生成与删除。
 * 底层通过 {@link ObjectStorage} 抽象访问对象存储, 由 {@code scrm.storage.provider}
 * 决定使用 MinIO 或阿里云 OSS, 本服务不感知具体 SDK 类型。
 * </p>
 * <p>
 * objectKey 命名规范：{\code scrm/conversation/{yyyyMM}/{uuid}_{fileName}}，
 * 按年月分目录, 便于归档清理。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationMediaService {

    /** objectKey 中年月目录格式 */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /** objectKey 前缀 */
    private static final String OBJECT_KEY_PREFIX = "scrm/conversation/";

    /** 预签名 URL 默认过期时间（分钟） */
    private static final int DEFAULT_PRESIGNED_EXPIRY_MINUTES = 60;

    /** 预签名 URL 最大过期时间（分钟）, 防止生成永久有效的预签名地址 */
    private static final int MAX_PRESIGNED_EXPIRY_MINUTES = 24 * 60;

    /** 对象存储实现 */
    private final ObjectStorage objectStorage;

    /** 对象存储配置 */
    private final StorageProperties storageProperties;

    /**
     * 获取媒体存储 bucket 名
     *
     * @return bucket 名
     */
    public String getBucket() {
        return storageProperties.getBucket();
    }

    /**
     * 获取当前生效的存储实现标识
     *
     * @return 实现标识 (如 minio / oss)
     */
    public String getProviderName() {
        return objectStorage.providerName();
    }

    /**
     * 生成 objectKey
     * <p>
     * 格式：{\code scrm/conversation/{yyyyMM}/{uuid}_{fileName}}
     * </p>
     *
     * @param fileName 原始文件名
     * @return objectKey
     */
    private String buildObjectKey(String fileName) {
        String monthPart = LocalDateTime.now().format(MONTH_FORMATTER);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return OBJECT_KEY_PREFIX + monthPart + "/" + uuid + "_" + sanitizeFileName(fileName);
    }

    /**
     * 净化客户端上传文件名
     * <p>
     * 仅取 basename 并去除路径穿越与响应头注入字符, 否则客户端可构造含 {@code /} 或
     * {@code ..} 的文件名, 使对象写到会话媒体命名空间之外, 甚至造成 key 越界。
     * </p>
     *
     * @param fileName 原始文件名
     * @return 净化后的文件名, 非法或为空时返回 media
     */
    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "media";
        }
        String name = fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replace("..", "")
                .replace("\"", "")
                .replace("\\", "")
                .replace("\r", "")
                .replace("\n", "")
                .trim();
        return name.isEmpty() ? "media" : name;
    }

    /**
     * 校验 objectKey 属于会话媒体命名空间
     * <p>
     * 对象存储 bucket 可能被其他业务共用, 通过前缀校验避免越权读取或
     * 删除命名空间之外的对象。
     * </p>
     *
     * @param objectKey 待校验的 objectKey
     * @throws ScrmException 为空或前缀不匹配
     */
    private void assertMediaKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw ScrmException.badRequest("媒体对象 key 不能为空");
        }
        if (!objectKey.startsWith(OBJECT_KEY_PREFIX)) {
            throw ScrmException.badRequest("媒体对象 key 前缀非法: " + OBJECT_KEY_PREFIX);
        }
    }

    /**
     * 上传媒体文件
     *
     * @param inputStream 媒体文件输入流
     * @param fileName    原始文件名（用于 objectKey 命名）
     * @param size        文件大小（字节）
     * @param contentType MIME 类型（如 image/jpeg）
     * @return 上传后的 objectKey
     * @throws ScrmException 上传失败
     */
    public String uploadMedia(InputStream inputStream, String fileName, long size, String contentType)
            throws ScrmException {
        if (inputStream == null) {
            throw ScrmException.badRequest("媒体文件输入流不能为空");
        }
        String objectKey = buildObjectKey(fileName);
        String safeContentType = sanitizeContentType(contentType);
        try {
            objectStorage.upload(storageProperties.getBucket(), objectKey, inputStream, size, safeContentType);
            log.info("会话媒体上传成功: provider={}, bucket={}, objectKey={}, size={}, contentType={}",
                    objectStorage.providerName(), storageProperties.getBucket(), objectKey, size, safeContentType);
            return objectKey;
        } catch (StorageException e) {
            log.error("会话媒体上传失败: objectKey={}, err={}", objectKey, e.getMessage(), e);
            throw new ScrmException(ScrmExceptionConstants.SCRM_MEDIA_UPLOAD_FAILED,
                    "媒体文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 净化上传文件的 Content-Type。
     * <p>
     * 仅允许图片/音视频/PDF/压缩包等安全 MIME, 否则强制降级为
     * {@code application/octet-stream}。防止客户端上传 {@code text/html} 等类型后,
     * 通过预签名 URL 在浏览器中直接渲染执行 (XSS)。
     * </p>
     *
     * @param contentType 客户端声明的 MIME 类型 (可为空白)
     * @return 净化后的 MIME 类型, 非法或空白时返回 application/octet-stream
     */
    private String sanitizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        String ct = contentType.trim().toLowerCase();
        // 仅基本媒体类型 (不含参数, 如 text/plain; charset=utf-8 需剥离)
        String base = ct.split(";")[0].trim();
        if (base.startsWith("image/") || base.startsWith("audio/") || base.startsWith("video/")
                || "application/pdf".equals(base) || "application/zip".equals(base)
                || "application/gzip".equals(base) || "application/x-tar".equals(base)
                || "application/msword".equals(base)
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(base)
                || "application/vnd.ms-excel".equals(base)
                || "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(base)
                || "application/vnd.ms-powerpoint".equals(base)
                || "application/vnd.openxmlformats-officedocument.presentationml.presentation".equals(base)
                || "application/octet-stream".equals(base)) {
            return base;
        }
        return "application/octet-stream";
    }

    /**
     * 下载媒体文件
     *
     * @param objectKey 对象 key
     * @return 媒体文件输入流（调用方负责关闭）
     * @throws ScrmException 下载失败
     */
    public InputStream downloadMedia(String objectKey) throws ScrmException {
        assertMediaKey(objectKey);
        try {
            return objectStorage.download(storageProperties.getBucket(), objectKey);
        } catch (StorageException e) {
            log.error("会话媒体下载失败: objectKey={}, err={}", objectKey, e.getMessage(), e);
            throw new ScrmException(ScrmExceptionConstants.SCRM_MEDIA_DOWNLOAD_FAILED,
                    "媒体文件下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成媒体文件预签名 URL（默认过期时间 1 小时）
     *
     * @param objectKey 对象 key
     * @return 预签名 URL
     * @throws ScrmException 生成 URL 失败
     */
    public String getMediaUrl(String objectKey) throws ScrmException {
        return getMediaUrl(objectKey, DEFAULT_PRESIGNED_EXPIRY_MINUTES);
    }

    /**
     * 生成媒体文件预签名 URL
     *
     * @param objectKey     对象 key
     * @param expiryMinutes 过期时间（分钟）
     * @return 预签名 URL
     * @throws ScrmException 生成 URL 失败
     */
    public String getMediaUrl(String objectKey, int expiryMinutes) throws ScrmException {
        assertMediaKey(objectKey);
        if (expiryMinutes <= 0 || expiryMinutes > MAX_PRESIGNED_EXPIRY_MINUTES) {
            throw ScrmException.badRequest("预签名 URL 过期时间必须在 1-" + MAX_PRESIGNED_EXPIRY_MINUTES
                    + " 分钟之间");
        }
        try {
            return objectStorage.presignedGetUrl(
                    storageProperties.getBucket(), objectKey, expiryMinutes * 60);
        } catch (StorageException e) {
            log.error("生成媒体预签名 URL 失败: objectKey={}, err={}", objectKey, e.getMessage(), e);
            throw new ScrmException(ScrmExceptionConstants.SCRM_MEDIA_PRESIGN_FAILED,
                    "生成媒体预签名 URL 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除媒体文件
     *
     * @param objectKey 对象 key
     * @throws ScrmException 删除失败
     */
    public void deleteMedia(String objectKey) throws ScrmException {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        if (!objectKey.startsWith(OBJECT_KEY_PREFIX)) {
            log.warn("忽略非法前缀的媒体删除请求: {}", objectKey);
            return;
        }
        try {
            objectStorage.delete(storageProperties.getBucket(), objectKey);
            log.info("会话媒体删除成功: objectKey={}", objectKey);
        } catch (StorageException e) {
            log.error("会话媒体删除失败: objectKey={}, err={}", objectKey, e.getMessage(), e);
            throw new ScrmException(ScrmExceptionConstants.SCRM_MEDIA_DELETE_FAILED,
                    "媒体文件删除失败: " + e.getMessage(), e);
        }
    }
}
