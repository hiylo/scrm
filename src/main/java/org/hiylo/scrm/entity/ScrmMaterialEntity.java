/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMaterialEntity.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.hibernate.annotations.GenericGenerator;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 素材库实体。
 * <p>
 * 团队共享素材库, 支持 IMAGE / VIDEO / FILE / AUDIO / LINK 五种类型, {@code fileSize}
 * 记录字节大小, {@code fileSizeText} 记录人类可读文本 (如 "1.5MB"), {@code downloadCount}
 * 用于使用统计, {@code status=INACTIVE} 的素材不出现在选用列表中。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_material", schema = "scrm", indexes = {
        @Index(name = "idx_material_type", columnList = "material_type"),
        @Index(name = "idx_material_category", columnList = "category_id"),
        @Index(name = "idx_material_status", columnList = "status")
})
@Data
public class ScrmMaterialEntity {

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
    @Column(name = "material_name", length = 200, nullable = false)
    private String materialName;

    /** 素材类型: IMAGE / VIDEO / FILE / AUDIO / LINK */
    @Column(name = "material_type", length = 20, nullable = false)
    private String materialType;

    /** 文件 URL */
    @Column(name = "file_url", length = 500, nullable = false)
    private String fileUrl;

    /** 文件大小（字节） */
    @Column(name = "file_size")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileSize;

    /** 文件大小文本, 如 "1.5MB" */
    @Column(name = "file_size_text", length = 50)
    private String fileSizeText;

    /** 缩略图 URL（可空） */
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    /** 素材描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 标签（逗号分隔） */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 分类 ID（可空, 空表示未分类） */
    @Column(name = "category_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    /** 下载次数 */
    @Column(name = "download_count")
    private Integer downloadCount;

    /** 状态: ACTIVE / INACTIVE（默认 ACTIVE） */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    /** 上传人（可空） */
    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;
}
