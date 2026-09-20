/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContentAssetEntity.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 内容素材实体。
 * <p>
 * 内容营销素材库, 支持 IMAGE/VIDEO/AUDIO/DOCUMENT/TEMPLATE 五种素材类型。
 * {@link #sourceType} 区分来源: UPLOAD (用户上传) / GENERATE (AI 生成) / IMPORT (外部导入)。
 * {@link #usageCount} 记录被内容引用次数, {@link #isPublic} 标识是否全局共享。
 * {@link #fileSize} 以 KB 为单位, {@link #duration} 仅对音视频有效。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_content_asset", schema = "scrm", indexes = {
        @Index(name = "idx_content_asset_type", columnList = "asset_type"),
        @Index(name = "idx_content_asset_category", columnList = "category"),
        @Index(name = "idx_content_asset_source", columnList = "source_type")
})
@Data
public class ScrmContentAssetEntity {

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

    // ==================== 业务字段 ====================

    /** 素材名称 */
    @Column(name = "asset_name", nullable = false, length = 200)
    private String assetName;

    /** 素材类型: IMAGE / VIDEO / AUDIO / DOCUMENT / TEMPLATE */
    @Column(name = "asset_type", nullable = false, length = 30)
    private String assetType;

    /** 文件 URL */
    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    /** 文件路径 (可空) */
    @Column(name = "file_path", length = 500)
    private String filePath;

    /** 文件大小 KB (可空) */
    @Column(name = "file_size")
    private Integer fileSize;

    /** 文件类型: JPG / PNG / MP4 / MP3 / PDF / DOCX / PPTX (可空) */
    @Column(name = "file_type", length = 20)
    private String fileType;

    /** 宽度 (像素, 可空) */
    @Column(name = "width")
    private Integer width;

    /** 高度 (像素, 可空) */
    @Column(name = "height")
    private Integer height;

    /** 媒体时长秒 (可空) */
    @Column(name = "duration")
    private Integer duration;

    /** 缩略图 URL (可空) */
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 标签 (逗号分隔, 可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 素材分类 (可空) */
    @Column(name = "category", length = 100)
    private String category;

    /** 来源: UPLOAD / GENERATE / IMPORT (默认 UPLOAD) */
    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    /** 来源 URL (可空) */
    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    /** 使用次数 (默认 0) */
    @Column(name = "usage_count")
    private Integer usageCount;

    /** 是否公开 (默认 FALSE) */
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
