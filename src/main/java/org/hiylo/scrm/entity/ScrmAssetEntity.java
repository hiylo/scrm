/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetEntity.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.persistence.Column;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 营销素材实体。
 * <p>
 * 描述一条营销素材: {@code assetCode} 在唯一, {@code assetType} 区分素材类型
 * (IMAGE/VIDEO/AUDIO/DOCUMENT/TEMPLATE/INFOGRAPHIC/LOGO/ICON/GIF/PDF/PRESENTATION/
 * SPREADSHEET/ARCHIVE/OTHER), {@code storageType} 描述存储位置 (LOCAL/OSS/COS/CDN/S3),
 * {@code status} / {@code reviewStatus} 维护素材状态与审核状态, {@code metadata} (TEXT)
 * 存储 JSON 元数据 (相机/位置/GPS/拍摄时间等), {@code downloadCount} / {@code viewCount} /
 * {@code useCount} / {@code likeCount} / {@code shareCount} / {@code favoriteCount}
 * 为素材级使用统计。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_asset", schema = "scrm",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_asset_code",
                columnNames = {"asset_code"}),
        indexes = {
                @Index(name = "idx_asset_category", columnList = "category_id"),
                @Index(name = "idx_asset_type", columnList = "asset_type"),
                @Index(name = "idx_asset_status", columnList = "status"),
                @Index(name = "idx_asset_review_status", columnList = "review_status"),
                @Index(name = "idx_asset_storage_type", columnList = "storage_type"),
                @Index(name = "idx_asset_uploaded_at", columnList = "uploaded_at"),
                @Index(name = "idx_asset_expiry", columnList = "expiry_date")
        })
/**
 * SCRM 营销素材实体。
 * <p>承载的营销素材文件资源: 素材名称与编码、所属分类 (categoryId/categoryName)、
 * 素材类型与 MIME/扩展名/字节数、文件与缩略图地址、封面与时长、分辨率与宽高比、
 * 来源与渠道、上传人与上传时间、状态与审核状态及审核人/时间/意见、存储类型与存储键、
 * 下载次数/引用次数/曝光次数/使用次数统计、失效时间与标签。素材编码 (assetCode) 唯一。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAssetEntity {

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

    /** 乐观锁版本号（并发更新保护, 后写入者触发 OptimisticLockException） */
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

    // ==================== 基础信息 ====================

    /** 素材名称 */
    @Column(name = "asset_name", nullable = false, length = 500)
    private String assetName;

    /** 素材编码 (唯一) */
    @Column(name = "asset_code", nullable = false, length = 50)
    private String assetCode;

    /** 分类 ID (可空) */
    @Column(name = "category_id")
    private Long categoryId;

    /** 分类名称 (冗余, 便于列表展示, 可空) */
    @Column(name = "category_name", length = 200)
    private String categoryName;

    /* 素材类型: IMAGE/VIDEO/AUDIO/DOCUMENT/TEMPLATE/INFOGRAPHIC/LOGO/ICON/GIF/PDF/PRESENTATION/SPREADSHEET/ARCHIVE/OTHER */
    /* */ @Column(name = "asset_type", nullable = false, length = 30)    private String assetType;

    // ==================== 文件信息 ====================

    /** MIME 类型 (可空, 如 image/png) */
    @Column(name = "mime_type", length = 100)
    private String mimeType;

    /** 文件扩展名 (可空, 如 png) */
    @Column(name = "file_extension", length = 20)
    private String fileExtension;

    /** 文件大小字节 (默认 0) */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /** 文件 URL (可空) */
    @Column(name = "file_url", length = 1000)
    private String fileUrl;

    /** 缩略图 URL (可空) */
    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    /** 预览 URL (可空) */
    @Column(name = "preview_url", length = 1000)
    private String previewUrl;

    /** 下载 URL (可空) */
    @Column(name = "download_url", length = 1000)
    private String downloadUrl;

    /** 存储类型: LOCAL/OSS/COS/CDN/S3 (默认 LOCAL) */
    @Column(name = "storage_type", nullable = false, length = 20)
    private String storageType;

    /** 存储路径 (可空) */
    @Column(name = "storage_path", length = 1000)
    private String storagePath;

    /** 存储 bucket (可空) */
    @Column(name = "storage_bucket", length = 200)
    private String storageBucket;

    /** 校验和 MD5/SHA (可空) */
    @Column(name = "checksum", length = 200)
    private String checksum;

    /** 描述 (可空) */
    @Column(name = "description", length = 1000)
    private String description;

    /** 标签 (逗号分隔, 可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 关键词 (逗号分隔, 可空) */
    @Column(name = "keywords", length = 500)
    private String keywords;

    // ==================== 媒体元信息 ====================

    /** 图片/视频宽 (可空) */
    @Column(name = "width")
    private Integer width;

    /** 图片/视频高 (可空) */
    @Column(name = "height")
    private Integer height;

    /** 视频/音频时长秒 (可空) */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** 文档页数 (可空) */
    @Column(name = "page_count")
    private Integer pageCount;

    /** 分辨率 (可空, 如 1920x1080) */
    @Column(name = "resolution", length = 50)
    private String resolution;

    /** 比特率 (可空) */
    @Column(name = "bitrate")
    private Integer bitrate;

    /** 格式 (可空, 如 MP4/PNG) */
    @Column(name = "format", length = 50)
    private String format;

    /** JSON 元数据 (TEXT, 如 {camera,location,gps,takenAt,...}, 可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    // ==================== 适用范围 ====================

    /** 适用场景 (逗号分隔, 可空) */
    @Column(name = "applicable_scenarios", length = 500)
    private String applicableScenarios;

    /** 适用产品 (逗号分隔, 可空) */
    @Column(name = "applicable_products", length = 500)
    private String applicableProducts;

    /** 适用渠道 (逗号分隔, 可空) */
    @Column(name = "applicable_channels", length = 500)
    private String applicableChannels;

    // ==================== 状态与审核 ====================

    /** 状态: PENDING/UNDER_REVIEW/APPROVED/REJECTED/PUBLISHED/ARCHIVED (默认 PENDING) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 审核状态: PENDING/APPROVED/REJECTED (默认 PENDING) */
    @Column(name = "review_status", nullable = false, length = 20)
    private String reviewStatus;

    /** 审核人 (可空) */
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    /** 审核时间 (可空) */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 审核意见 (可空) */
    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    /** 业务版本号 (默认 1, 区别于乐观锁 version) */
    @Column(name = "version_no")
    private Integer versionNo;

    /** 是否公开 (默认 FALSE) */
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic;

    /** 是否模板 (默认 FALSE) */
    @Column(name = "is_template", nullable = false)
    private Boolean isTemplate;

    // ==================== 使用统计 ====================

    /** 下载次数 (默认 0) */
    @Column(name = "download_count")
    private Integer downloadCount;

    /** 浏览次数 (默认 0) */
    @Column(name = "view_count")
    private Integer viewCount;

    /** 使用次数 (默认 0) */
    @Column(name = "use_count")
    private Integer useCount;

    /** 点赞数 (默认 0) */
    @Column(name = "like_count")
    private Integer likeCount;

    /** 分享数 (默认 0) */
    @Column(name = "share_count")
    private Integer shareCount;

    /** 收藏数 (默认 0) */
    @Column(name = "favorite_count")
    private Integer favoriteCount;

    /** 最近使用时间 (可空) */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    // ==================== 上传与过期 ====================

    /** 上传人 (可空) */
    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    /** 上传时间 (可空) */
    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    /** 过期日期 (可空) */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** 是否过期 (默认 FALSE) */
    @Column(name = "is_expired", nullable = false)
    private Boolean isExpired;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
