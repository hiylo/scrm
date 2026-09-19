/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmImportTemplateDto.java
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
 * SCRM 数据导入模板 DTO。
 * <p>
 * 对应 {@code ScrmImportTemplateEntity} 的业务字段, 不含公共字段与统计字段 (usageCount)。
 * 创建/更新接口入参, 校验注解保证必填字段与长度约束。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmImportTemplateDto {

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名称长度不能超过 200")
    private String templateName;

    /** 数据类型: CUSTOMER/CONTACT/FOLLOW_RECORD/TAG/PRODUCT/ORDER/OTHER */
    @NotBlank(message = "数据类型不能为空")
    @Size(max = 30, message = "数据类型长度不能超过 30")
    @Pattern(regexp = "CUSTOMER|CONTACT|FOLLOW_RECORD|TAG|PRODUCT|ORDER|OTHER",
            message = "数据类型仅支持 CUSTOMER/CONTACT/FOLLOW_RECORD/TAG/PRODUCT/ORDER/OTHER")
    private String dataType;

    /** 模板描述（可空） */
    @Size(max = 500, message = "模板描述长度不能超过 500")
    private String description;

    /** 列定义 JSON: [{name,field,type,required,enum}] */
    @NotBlank(message = "列定义不能为空")
    private String columns;

    /** 示例文件 URL（可空） */
    @Size(max = 500, message = "示例文件 URL 长度不能超过 500")
    private String sampleFileUrl;

    /** 校验规则 JSON（可空） */
    private String validationRules;

    /** 去重字段（可空） */
    @Size(max = 200, message = "去重字段长度不能超过 200")
    private String deduplicationKey;

    /** 存在则更新（创建时可选, 默认 false） */
    private Boolean updateIfExists;

    /** 是否启用（创建时可选, 默认 true） */
    private Boolean enabled;

    /** 创建人（可空） */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
