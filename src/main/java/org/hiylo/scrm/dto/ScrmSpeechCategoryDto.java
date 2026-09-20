/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechCategoryDto.java
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
 * SCRM 话术分类 DTO。
 * <p>
 * 对应 {@code ScrmSpeechCategoryEntity} 的业务字段, 不含公共字段
 * (id/createTime/updateTime/version)。创建/更新接口入参, 校验注解
 * 保证必填字段与长度约束。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmSpeechCategoryDto {

    /** 分类名称 */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 100, message = "分类名称长度不能超过 100")
    private String categoryName;

    /** 父分类 ID（可空, 空表示顶级分类） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    /** 排序值（数字越小越靠前, 默认 0） */
    private Integer sortOrder;

    /** 分类描述 */
    @Size(max = 500, message = "分类描述长度不能超过 500")
    private String description;

    /** 状态: ACTIVE / INACTIVE（创建时可选, 默认 ACTIVE） */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "ACTIVE|INACTIVE|", message = "状态仅支持 ACTIVE/INACTIVE")
    private String status;
}
