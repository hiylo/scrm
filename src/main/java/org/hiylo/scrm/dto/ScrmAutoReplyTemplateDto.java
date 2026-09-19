/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyTemplateDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 消息自动回复模板 DTO。
 * <p>
 * 对应 {@code ScrmAutoReplyTemplateEntity} 的业务字段, 不含公共字段
 * (id/createTime/updateTime/version/usageCount)。创建/更新接口入参,
 * 校验注解保证必填字段与取值约束。content 支持变量: {customerName}/{nickname}/{time}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAutoReplyTemplateDto {

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名称长度不能超过 200")
    private String templateName;

    /** 模板类型: TEXT/RICH_TEXT/HTML（默认 TEXT） */
    @Size(max = 20, message = "模板类型长度不能超过 20")
    @Pattern(regexp = "TEXT|RICH_TEXT|HTML|",
            message = "模板类型仅支持 TEXT/RICH_TEXT/HTML")
    private String templateType;

    /** 模板分类 */
    @Size(max = 100, message = "模板分类长度不能超过 100")
    private String category;

    /** 模板内容 (支持变量: {customerName}/{nickname}/{time}) */
    @NotBlank(message = "模板内容不能为空")
    private String content;

    /** 可用变量 (逗号分隔) */
    @Size(max = 500, message = "可用变量长度不能超过 500")
    private String variables;

    /** 适用场景 (逗号分隔) */
    @Size(max = 500, message = "适用场景长度不能超过 500")
    private String applicableScenes;

    /** 缩略图 URL */
    @Size(max = 500, message = "缩略图 URL 长度不能超过 500")
    private String thumbnailUrl;

    /** 是否启用（默认 TRUE） */
    private Boolean enabled;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
