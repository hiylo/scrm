/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQuickReplyDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 快捷回复条目 DTO。
 * <p>
 * 对应 {@code ScrmQuickReplyEntity} 的业务字段, 不含公共字段
 * (id/createTime/updateTime/version)。创建/更新接口入参, 校验注解
 * 保证必填字段与长度约束。{@code isPersonal=TRUE} 时 {@code ownerUserId} 必填,
 * {@code useCount} 由 {@code POST /{id}/use} 端点维护, 创建/更新时入参可选。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmQuickReplyDto {

    /** 分类 ID（可空, 空表示未分类） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    /** 快捷回复标题/摘要 */
    @NotBlank(message = "快捷回复标题不能为空")
    @Size(max = 200, message = "快捷回复标题长度不能超过 200")
    private String title;

    /** 回复内容 */
    @NotBlank(message = "回复内容不能为空")
    private String content;

    /** 回复类型: TEXT / IMAGE / LINK / MIXED（创建时可选, 默认 TEXT） */
    @Size(max = 20, message = "回复类型长度不能超过 20")
    @Pattern(regexp = "TEXT|IMAGE|LINK|MIXED|",
            message = "回复类型仅支持 TEXT/IMAGE/LINK/MIXED")
    private String replyType;

    /** 媒体 URL JSON 数组字符串, 如 ["url1", "url2"] */
    private String mediaUrls;

    /** 快捷键, 如 "/你好"（可空） */
    @Size(max = 50, message = "快捷键长度不能超过 50")
    private String shortcut;

    /** 适用平台类型（可空, 空表示全部） */
    @Size(max = 30, message = "平台类型长度不能超过 30")
    private String platformType;

    /** 使用场景 */
    @Size(max = 50, message = "使用场景长度不能超过 50")
    private String scenario;

    /** 标签（逗号分隔） */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 排序值（数字越小越靠前, 默认 0） */
    private Integer sortOrder;

    /** 使用次数（创建时可选, 默认 0） */
    private Integer useCount;

    /** 是否个人专属（创建时可选, 默认 FALSE） */
    private Boolean isPersonal;

    /** 个人专属时归属人用户 ID（isPersonal=TRUE 时必填） */
    @Size(max = 100, message = "归属人用户 ID 长度不能超过 100")
    private String ownerUserId;

    /** 状态: ACTIVE / INACTIVE（创建时可选, 默认 ACTIVE） */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "ACTIVE|INACTIVE|", message = "状态仅支持 ACTIVE/INACTIVE")
    private String status;

    /** 创建人（可空） */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
