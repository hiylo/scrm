/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechDto.java
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
 * SCRM 话术条目 DTO。
 * <p>
 * 对应 {@code ScrmSpeechEntity} 的业务字段, 不含公共字段
 * (id/createTime/updateTime/version)。创建/更新接口入参, 校验注解
 * 保证必填字段与长度约束。{@code useCount} / {@code likeCount} 用于统计, 由
 * {@code POST /{id}/use} 与 {@code POST /{id}/like} 端点维护, 创建/更新时入参可选。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmSpeechDto {

    /** 分类 ID（可空, 空表示未分类） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    /** 话术标题 */
    @NotBlank(message = "话术标题不能为空")
    @Size(max = 200, message = "话术标题长度不能超过 200")
    private String title;

    /** 话术内容 */
    @NotBlank(message = "话术内容不能为空")
    private String content;

    /** 话术类型: TEXT / IMAGE / VIDEO / FILE / LINK / MIXED（创建时可选, 默认 TEXT） */
    @Size(max = 20, message = "话术类型长度不能超过 20")
    @Pattern(regexp = "TEXT|IMAGE|VIDEO|FILE|LINK|MIXED|",
            message = "话术类型仅支持 TEXT/IMAGE/VIDEO/FILE/LINK/MIXED")
    private String speechType;

    /** 媒体 URL JSON 数组字符串, 如 ["url1", "url2"] */
    private String mediaUrls;

    /** 适用平台类型（可空, 空表示全部, 如 wechat / douyin / xhs） */
    @Size(max = 30, message = "平台类型长度不能超过 30")
    private String platformType;

    /** 使用场景: GREETING / FOLLOW_UP / REJECTION / HOLIDAY / AFTER_SALE / ETC */
    @Size(max = 50, message = "使用场景长度不能超过 50")
    private String scenario;

    /** 标签（逗号分隔） */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 排序值（数字越小越靠前, 默认 0） */
    private Integer sortOrder;

    /** 使用次数（创建时可选, 默认 0） */
    private Integer useCount;

    /** 点赞数（创建时可选, 默认 0） */
    private Integer likeCount;

    /** 状态: ACTIVE / INACTIVE / DRAFT（创建时可选, 默认 ACTIVE） */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "ACTIVE|INACTIVE|DRAFT|",
            message = "状态仅支持 ACTIVE/INACTIVE/DRAFT")
    private String status;

    /** 创建人（可空） */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
