/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContentAssetDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 内容素材 DTO。
 * <p>
 * 对应 {@code ScrmContentAssetEntity} 的业务字段, 素材上传/更新入参。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmContentAssetDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 素材名称 */
    @NotBlank(message = "素材名称不能为空")
    @Size(max = 200, message = "素材名称长度不能超过 200")
    private String assetName;

    /** 素材类型: IMAGE / VIDEO / AUDIO / DOCUMENT / TEMPLATE */
    @NotBlank(message = "素材类型不能为空")
    @Pattern(regexp = "IMAGE|VIDEO|AUDIO|DOCUMENT|TEMPLATE",
            message = "素材类型仅支持 IMAGE/VIDEO/AUDIO/DOCUMENT/TEMPLATE")
    private String assetType;

    /** 文件 URL */
    @NotBlank(message = "文件 URL 不能为空")
    @Size(max = 500, message = "文件 URL 长度不能超过 500")
    private String fileUrl;

    /** 文件路径 (可空) */
    @Size(max = 500, message = "文件路径长度不能超过 500")
    private String filePath;

    /** 文件大小 KB (可空) */
    private Integer fileSize;

    /** 文件类型: JPG / PNG / MP4 / MP3 / PDF / DOCX / PPTX (可空) */
    @Pattern(regexp = "JPG|PNG|MP4|MP3|PDF|DOCX|PPTX",
            message = "文件类型仅支持 JPG/PNG/MP4/MP3/PDF/DOCX/PPTX")
    private String fileType;

    /** 宽度 (像素, 可空) */
    private Integer width;

    /** 高度 (像素, 可空) */
    private Integer height;

    /** 媒体时长秒 (可空) */
    private Integer duration;

    /** 缩略图 URL (可空) */
    @Size(max = 500, message = "缩略图 URL 长度不能超过 500")
    private String thumbnailUrl;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 标签 (逗号分隔, 可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 素材分类 (可空) */
    @Size(max = 100, message = "素材分类长度不能超过 100")
    private String category;

    /** 来源: UPLOAD / GENERATE / IMPORT (默认 UPLOAD) */
    @Pattern(regexp = "UPLOAD|GENERATE|IMPORT",
            message = "来源仅支持 UPLOAD/GENERATE/IMPORT")
    private String sourceType;

    /** 来源 URL (可空) */
    @Size(max = 500, message = "来源 URL 长度不能超过 500")
    private String sourceUrl;

    /** 使用次数 (查询返回) */
    private Integer usageCount;

    /** 是否公开 (默认 FALSE) */
    private Boolean isPublic;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
