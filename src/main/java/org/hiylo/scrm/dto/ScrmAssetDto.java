/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 营销素材 DTO。
 * <p>
 * 用于素材更新与查询返回。素材编码 (assetCode) 创建后不允许变更, 由上传接口生成;
 * 状态 / 审核状态 / 使用统计字段由服务端维护, 更新时入参可选。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAssetDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 素材名称 */
    @NotBlank(message = "素材名称不能为空")
    @Size(max = 500, message = "素材名称长度不能超过 500")
    private String assetName;

    /** 素材编码 (唯一, 创建后不可变更) */
    @Size(max = 50, message = "素材编码长度不能超过 50")
    private String assetCode;

    /** 分类 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    /** 分类名称 (由服务端维护) */
    private String categoryName;

    /** 素材类型 */
    @Size(max = 30, message = "素材类型长度不能超过 30")
    private String assetType;

    /** MIME 类型 (可空) */
    @Size(max = 100, message = "MIME 类型长度不能超过 100")
    private String mimeType;

    /** 文件扩展名 (可空) */
    @Size(max = 20, message = "文件扩展名长度不能超过 20")
    private String fileExtension;

    /** 文件大小字节 */
    private Long fileSizeBytes;

    /** 文件 URL (可空) */
    @Size(max = 1000, message = "文件 URL 长度不能超过 1000")
    private String fileUrl;

    /** 缩略图 URL (可空) */
    @Size(max = 1000, message = "缩略图 URL 长度不能超过 1000")
    private String thumbnailUrl;

    /** 预览 URL (可空) */
    @Size(max = 1000, message = "预览 URL 长度不能超过 1000")
    private String previewUrl;

    /** 下载 URL (可空) */
    @Size(max = 1000, message = "下载 URL 长度不能超过 1000")
    private String downloadUrl;

    /** 存储类型: LOCAL/OSS/COS/CDN/S3 */
    @Size(max = 20, message = "存储类型长度不能超过 20")
    private String storageType;

    /** 存储路径 (可空) */
    @Size(max = 1000, message = "存储路径长度不能超过 1000")
    private String storagePath;

    /** 存储 bucket (可空) */
    @Size(max = 200, message = "存储 bucket 长度不能超过 200")
    private String storageBucket;

    /** 校验和 (可空) */
    @Size(max = 200, message = "校验和长度不能超过 200")
    private String checksum;

    /** 描述 (可空) */
    @Size(max = 1000, message = "描述长度不能超过 1000")
    private String description;

    /** 标签 (逗号分隔, 可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 关键词 (逗号分隔, 可空) */
    @Size(max = 500, message = "关键词长度不能超过 500")
    private String keywords;

    /** 图片/视频宽 (可空) */
    private Integer width;

    /** 图片/视频高 (可空) */
    private Integer height;

    /** 视频/音频时长秒 (可空) */
    private Integer durationSeconds;

    /** 文档页数 (可空) */
    private Integer pageCount;

    /** 分辨率 (可空) */
    @Size(max = 50, message = "分辨率长度不能超过 50")
    private String resolution;

    /** 比特率 (可空) */
    private Integer bitrate;

    /** 格式 (可空) */
    @Size(max = 50, message = "格式长度不能超过 50")
    private String format;

    /** JSON 元数据 (TEXT, 可空) */
    private String metadata;

    /** 适用场景 (逗号分隔, 可空) */
    @Size(max = 500, message = "适用场景长度不能超过 500")
    private String applicableScenarios;

    /** 适用产品 (逗号分隔, 可空) */
    @Size(max = 500, message = "适用产品长度不能超过 500")
    private String applicableProducts;

    /** 适用渠道 (逗号分隔, 可空) */
    @Size(max = 500, message = "适用渠道长度不能超过 500")
    private String applicableChannels;

    /** 状态 (由服务端维护) */
    private String status;

    /** 审核状态 (由服务端维护) */
    private String reviewStatus;

    /** 审核人 (由服务端维护) */
    private String reviewedBy;

    /** 审核时间 (由服务端维护) */
    private LocalDateTime reviewedAt;

    /** 审核意见 (由服务端维护) */
    private String reviewComment;

    /** 业务版本号 (默认 1) */
    private Integer versionNo;

    /** 是否公开 */
    private Boolean isPublic;

    /** 是否模板 */
    private Boolean isTemplate;

    /** 下载次数 (由服务端维护) */
    private Integer downloadCount;

    /** 浏览次数 (由服务端维护) */
    private Integer viewCount;

    /** 使用次数 (由服务端维护) */
    private Integer useCount;

    /** 点赞数 (由服务端维护) */
    private Integer likeCount;

    /** 分享数 (由服务端维护) */
    private Integer shareCount;

    /** 收藏数 (由服务端维护) */
    private Integer favoriteCount;

    /** 最近使用时间 (由服务端维护) */
    private LocalDateTime lastUsedAt;

    /** 上传人 (可空) */
    @Size(max = 100, message = "上传人长度不能超过 100")
    private String uploadedBy;

    /** 上传时间 (由服务端维护) */
    private LocalDateTime uploadedAt;

    /** 过期日期 (可空) */
    private LocalDate expiryDate;

    /** 是否过期 (由服务端维护) */
    private Boolean isExpired;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
