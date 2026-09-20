/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMaterialDto.java
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

/**
 * SCRM 素材库 DTO。
 * <p>
 * 对应 {@code ScrmMaterialEntity} 的业务字段, 不含公共字段
 * (id/createTime/updateTime/version)。创建/更新接口入参, 校验注解
 * 保证必填字段与长度约束。{@code downloadCount} 由 {@code POST /{id}/download}
 * 端点维护, 创建/更新时入参可选。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmMaterialDto {

    /** 素材名称 */
    @NotBlank(message = "素材名称不能为空")
    @Size(max = 200, message = "素材名称长度不能超过 200")
    private String materialName;

    /** 素材类型: IMAGE / VIDEO / FILE / AUDIO / LINK */
    @NotBlank(message = "素材类型不能为空")
    @Size(max = 20, message = "素材类型长度不能超过 20")
    @Pattern(regexp = "IMAGE|VIDEO|FILE|AUDIO|LINK",
            message = "素材类型仅支持 IMAGE/VIDEO/FILE/AUDIO/LINK")
    private String materialType;

    /** 文件 URL */
    @NotBlank(message = "文件 URL 不能为空")
    @Size(max = 500, message = "文件 URL 长度不能超过 500")
    private String fileUrl;

    /** 文件大小（字节） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileSize;

    /** 文件大小文本, 如 "1.5MB" */
    @Size(max = 50, message = "文件大小文本长度不能超过 50")
    private String fileSizeText;

    /** 缩略图 URL（可空） */
    @Size(max = 500, message = "缩略图 URL 长度不能超过 500")
    private String thumbnailUrl;

    /** 素材描述 */
    @Size(max = 500, message = "素材描述长度不能超过 500")
    private String description;

    /** 标签（逗号分隔） */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 分类 ID（可空, 空表示未分类） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    /** 下载次数（创建时可选, 默认 0） */
    private Integer downloadCount;

    /** 状态: ACTIVE / INACTIVE（创建时可选, 默认 ACTIVE） */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "ACTIVE|INACTIVE|", message = "状态仅支持 ACTIVE/INACTIVE")
    private String status;

    /** 上传人（可空） */
    @Size(max = 100, message = "上传人长度不能超过 100")
    private String uploadedBy;
}
