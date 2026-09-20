/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateGroupDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 消息模板分组 DTO。
 * <p>
 * 对应 {@code ScrmMessageTemplateGroupEntity} 的业务字段, 不含公共字段
 * (id/createTime/updateTime/version)。创建/更新分组接口入参。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmMessageTemplateGroupDto {

    /** 分组名称 */
    @NotBlank(message = "分组名称不能为空")
    @Size(max = 200, message = "分组名称长度不能超过 200")
    private String groupName;

    /** 分组编码（全局唯一） */
    @NotBlank(message = "分组编码不能为空")
    @Size(max = 50, message = "分组编码长度不能超过 50")
    private String groupCode;

    /** 分组描述（可空） */
    @Size(max = 500, message = "分组描述长度不能超过 500")
    private String description;

    /** 分组类型: MARKETING/SERVICE/NOTIFICATION/VERIFICATION/REMINDER/WELCOME/FOLLOWUP/CUSTOM */
    @NotBlank(message = "分组类型不能为空")
    @Size(max = 50, message = "分组类型长度不能超过 50")
    private String groupType;

    /** 父分组 ID（可空, 空表示顶级分组） */
    private Long parentGroupId;

    /** 排序值（数字越小越靠前, 默认 0） */
    private Integer sortOrder;

    /** 是否启用（默认 true） */
    private Boolean enabled;

    /** 展示颜色（可空） */
    @Size(max = 20, message = "颜色长度不能超过 20")
    private String color;

    /** 展示图标（可空） */
    @Size(max = 100, message = "图标长度不能超过 100")
    private String icon;

    /** 创建人（可空） */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
