/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ConversationMediaController.java
 * Date : 2026/09/18 11:24:39
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ConversationMediaService;
import org.hiylo.scrm.vo.ConversationMediaVo;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 会话媒体控制器
 * <p>
 * 提供会话消息媒体文件的上传、下载地址获取、内容下载与删除接口,
 * 统一前缀 {@code /scrm/media}。底层存储由 {@code scrm.storage.provider}
 * 决定使用 MinIO 或阿里云 OSS, 接口契约不随存储实现变化。
 * </p>
 * <p>
 * objectKey 含路径分隔符, 因此统一通过查询参数 {@code key} 传递, 不使用路径变量。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/media")
@RequiredArgsConstructor
public class ConversationMediaController {

    /** 预签名 URL 默认过期时间 (分钟) */
    private static final int DEFAULT_EXPIRY_MINUTES = 60;

    /** 会话媒体服务 */
    private final ConversationMediaService mediaService;

    /**
     * 上传会话媒体文件
     *
     * @param file 媒体文件 (multipart 字段名 file)
     * @return 上传结果 (objectKey + 预签名 URL)
     * @throws ScrmException 文件为空或上传失败
     */
    @RequirePermission(resource = "media", action = "write")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/upload")
    public OperationResponse<ConversationMediaVo> upload(@RequestParam("file") MultipartFile file)
            throws ScrmException {
        if (file == null || file.isEmpty()) {
            throw ScrmException.badRequest("上传文件不能为空");
        }
        String objectKey;
        try (InputStream inputStream = file.getInputStream()) {
            objectKey = mediaService.uploadMedia(
                    inputStream, file.getOriginalFilename(), file.getSize(),
                    file.getContentType());
        } catch (IOException e) {
            throw new ScrmException("SCRM_MEDIA_UPLOAD_FAILED", "读取上传文件失败: " + e.getMessage(), e);
        }
        int expirySeconds = DEFAULT_EXPIRY_MINUTES * 60;
        String url = mediaService.getMediaUrl(objectKey, DEFAULT_EXPIRY_MINUTES);
        return OperationResponse.build(ConversationMediaVo.builder()
                .objectKey(objectKey)
                .provider(mediaService.getProviderName())
                .bucket(mediaService.getBucket())
                .url(url)
                .expiresInSeconds(expirySeconds)
                .size(file.getSize())
                .contentType(file.getContentType())
                .build());
    }

    /**
     * 获取媒体文件预签名下载 URL
     *
     * @param key           对象 key
     * @param expiryMinutes 过期时间 (分钟), 默认 60
     * @return 预签名 URL
     * @throws ScrmException key 非法或生成失败
     */
    @RequirePermission(resource = "media", action = "read")
    @GetMapping("/url")
    public OperationResponse<ConversationMediaVo> getUrl(
            @RequestParam("key") String key,
            @RequestParam(value = "expiryMinutes", defaultValue = "60") int expiryMinutes)
            throws ScrmException {
        int expirySeconds = expiryMinutes * 60;
        return OperationResponse.build(ConversationMediaVo.builder()
                .objectKey(key)
                .provider(mediaService.getProviderName())
                .bucket(mediaService.getBucket())
                .url(mediaService.getMediaUrl(key, expiryMinutes))
                .expiresInSeconds(expirySeconds)
                .build());
    }

    /**
     * 下载媒体文件内容
     *
     * @param key      对象 key
     * @param response HTTP 响应
     * @throws ScrmException key 非法或下载失败
     */
    @RequirePermission(resource = "media", action = "read")
    @GetMapping("/download")
    public void download(@RequestParam("key") String key, HttpServletResponse response)
            throws ScrmException {
        try (InputStream inputStream = mediaService.downloadMedia(key)) {
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; " + buildContentDisposition(key));
            StreamUtils.copy(inputStream, response.getOutputStream());
        } catch (IOException e) {
            throw new ScrmException("SCRM_MEDIA_DOWNLOAD_FAILED", "写出媒体内容失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除媒体文件
     *
     * @param key 对象 key
     * @return 删除结果
     * @throws ScrmException 删除失败
     */
    @RequirePermission(resource = "media", action = "delete")
    @DeleteMapping
    public OperationResponse<Void> delete(@RequestParam("key") String key) throws ScrmException {
        mediaService.deleteMedia(key);
        return OperationResponse.build();
    }

    /**
     * 从 objectKey 构造 Content-Disposition 文件名头
     * <p>
     * 取 key 最后一段作为文件名, 按 RFC 5987 以 UTF-8 编码, 避免中文文件名乱码
     * 与响应头注入风险。
     * </p>
     *
     * @param objectKey 对象 key
     * @return Content-Disposition 头值
     */
    private String buildContentDisposition(String objectKey) {
        String name = objectKey.contains("/") ? objectKey.substring(objectKey.lastIndexOf('/') + 1) : objectKey;
        String safe = name.replace("\"", "").replace("\\", "").replace("\r", "").replace("\n", "");
        String encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
        return "filename*=UTF-8''" + encoded;
    }
}
