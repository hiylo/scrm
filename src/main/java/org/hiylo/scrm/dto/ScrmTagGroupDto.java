/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagGroupDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 客户标签分组 DTO。
 * <p>
 * 对应 {@code ScrmTagGroupEntity} 的业务字段, 不含公共字段 (id/createTime/
 * updateTime/version/tagCount/customerCount)。创建/更新接口入参, 校验注解保证必填字段
 * 与取值约束。groupCode 创建后不可修改 (Service 层强制忽略更新)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmTagGroupDto {

    /** 分组名称 */
    @NotBlank(message = "分组名称不能为空")
    @Size(max = 100, message = "分组名称长度不能超过 100")
    private String groupName;

    /** 分组编码 (创建时必填, 仅允许字母/数字/下划线) */
    @NotBlank(message = "分组编码不能为空")
    @Size(max = 50, message = "分组编码长度不能超过 50")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "分组编码仅允许字母、数字与下划线")
    private String groupCode;

    /** 分组描述 */
    @Size(max = 500, message = "分组描述长度不能超过 500")
    private String description;

    /** 分组颜色 (前端展示用, 如 #1890FF) */
    @Size(max = 20, message = "分组颜色长度不能超过 20")
    private String color;

    /** 分组图标 (前端展示用, 如 icon-tag-group) */
    @Size(max = 100, message = "分组图标长度不能超过 100")
    private String icon;

    /** 排序 (数字越小越靠前, 默认 0) */
    private Integer sortOrder;

    /** 是否系统内置 (创建时可选, 默认 false) */
    private Boolean isSystem;

    /** 是否启用 (创建时可选, 默认 true) */
    private Boolean enabled;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
