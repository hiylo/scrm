/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateDto.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * SCRM 消息模板 DTO。
 * <p>
 * 对应 {@code ScrmMessageTemplateEntity} 的业务字段, 不含公共字段
 * (id/createTime/updateTime/version)。创建/更新接口入参, 校验注解保证必填字段
 * 与长度约束。{@link #renderVariables} 仅用于 {@code /render} 端点传参, 不持久化。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmMessageTemplateDto {

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名称长度不能超过 200")
    private String templateName;

    /** 分类: greeting（问候）/ promotion（促销）/ service（服务）/ follow_up（跟进）/ apology（致歉） */
    @NotBlank(message = "分类不能为空")
    @Size(max = 50, message = "分类长度不能超过 50")
    private String category;

    /** 模板内容, 支持变量插值 {{nickname}} / {{platformType}} / {{customerName}} / {{ownerName}} */
    @NotBlank(message = "模板内容不能为空")
    private String content;

    /** 适用平台类型（可空, 空表示通用, 如 wechat / douyin / xhs） */
    @Size(max = 30, message = "平台类型长度不能超过 30")
    private String platformType;

    /** 变量列表 JSON 数组字符串（由 content 自动提取, 入参可空） */
    private String variables;

    /** 是否启用（创建时可选, 默认 true） */
    private Boolean enabled;

    /** 排序值（数字越小越靠前, 默认 0） */
    private Integer sortOrder;

    /** 创建人（可空） */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /**
     * 渲染时使用的变量值映射, 仅用于 {@code POST /{id}/render} 端点传参, 不持久化。
     * <p>key 为变量名（如 nickname）, value 为替换值（缺失则替换为空字符串）。</p>
     */
    private Map<String, String> renderVariables;
}
