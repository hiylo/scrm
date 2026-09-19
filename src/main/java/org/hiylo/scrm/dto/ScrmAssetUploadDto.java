/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetUploadDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 营销素材上传 DTO。
 * <p>
 * 用于素材上传接口入参, 包含素材名称 / 分类 / 类型 / 文件 URL / 文件大小 / MIME 类型 /
 * 标签 / 描述。素材编码由服务端自动生成, 状态 / 审核状态 / 统计字段由服务端初始化。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAssetUploadDto {

    /** 素材名称 */
    @NotBlank(message = "素材名称不能为空")
    @Size(max = 500, message = "素材名称长度不能超过 500")
    private String assetName;

    /** 分类 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    /* 素材类型: IMAGE/VIDEO/AUDIO/DOCUMENT/TEMPLATE/INFOGRAPHIC/LOGO/ICON/GIF/PDF/PRESENTATION/SPREADSHEET/ARCHIVE/OTHER */
    /* */ @NotBlank(message = "素材类型不能为空")    @Size(max = 30, message = "素材类型长度不能超过 30")
    private String assetType;

    /** 文件 URL */
    @Size(max = 1000, message = "文件 URL 长度不能超过 1000")
    private String fileUrl;

    /** 文件大小字节 */
    private Long fileSizeBytes;

    /** MIME 类型 (可空) */
    @Size(max = 100, message = "MIME 类型长度不能超过 100")
    private String mimeType;

    /** 标签 (逗号分隔, 可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 描述 (可空) */
    @Size(max = 1000, message = "描述长度不能超过 1000")
    private String description;

    /** 上传人 (可空, 缺省从 UserContext 获取) */
    @Size(max = 100, message = "上传人长度不能超过 100")
    private String uploadedBy;
}
