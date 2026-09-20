/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContentDto.java
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

import java.time.LocalDateTime;

/**
 * SCRM 内容营销 DTO。
 * <p>
 * 对应 {@code ScrmContentEntity} 的业务字段, 创建/更新接口入参。
 * 各类 *Count / reviewedAt / publishedAt 字段为查询返回, 创建/更新时由服务端管理。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmContentDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 内容标题 */
    @NotBlank(message = "内容标题不能为空")
    @Size(max = 200, message = "内容标题长度不能超过 200")
    private String title;

    /** 内容类型: ARTICLE / VIDEO / IMAGE / POSTER / LIVE_SHORT / INFOGRAPHIC / PDF */
    @NotBlank(message = "内容类型不能为空")
    @Pattern(regexp = "ARTICLE|VIDEO|IMAGE|POSTER|LIVE_SHORT|INFOGRAPHIC|PDF",
            message = "内容类型仅支持 ARTICLE/VIDEO/IMAGE/POSTER/LIVE_SHORT/INFOGRAPHIC/PDF")
    private String contentType;

    /** 内容分类 (可空) */
    @Size(max = 100, message = "内容分类长度不能超过 100")
    private String category;

    /** 摘要 (可空) */
    @Size(max = 500, message = "摘要长度不能超过 500")
    private String summary;

    /** 正文内容 / 富文本 (可空) */
    private String bodyContent;

    /** 封面图 URL (可空) */
    @Size(max = 500, message = "封面图 URL 长度不能超过 500")
    private String coverImage;

    /** 媒体文件 URL (可空) */
    @Size(max = 500, message = "媒体文件 URL 长度不能超过 500")
    private String mediaUrl;

    /** 媒体时长秒 (可空) */
    private Integer mediaDuration;

    /** 标签 (逗号分隔, 可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 目标受众 (逗号分隔, 可空) */
    @Size(max = 500, message = "目标受众长度不能超过 500")
    private String targetAudience;

    /** 作者 ID (可空) */
    @Size(max = 100, message = "作者 ID 长度不能超过 100")
    private String authorId;

    /** 作者名称 (可空) */
    @Size(max = 100, message = "作者名称长度不能超过 100")
    private String authorName;

    /** 状态: DRAFT / PENDING_REVIEW / APPROVED / SCHEDULED / PUBLISHED / ARCHIVED / REJECTED (查询返回) */
    @Pattern(regexp = "DRAFT|PENDING_REVIEW|APPROVED|SCHEDULED|PUBLISHED|ARCHIVED|REJECTED",
            message = "状态仅支持 DRAFT/PENDING_REVIEW/APPROVED/SCHEDULED/PUBLISHED/ARCHIVED/REJECTED")
    private String status;

    /** 审核状态 (查询返回) */
    private String reviewStatus;

    /** 审核人 ID (可空) */
    @Size(max = 100, message = "审核人 ID 长度不能超过 100")
    private String reviewerId;

    /** 审核人名称 (可空) */
    @Size(max = 100, message = "审核人名称长度不能超过 100")
    private String reviewerName;

    /** 审核时间 (查询返回) */
    private LocalDateTime reviewedAt;

    /** 审核意见 (可空) */
    @Size(max = 500, message = "审核意见长度不能超过 500")
    private String reviewComment;

    /** 发布时间 (查询返回) */
    private LocalDateTime publishedAt;

    /** 计划发布时间 (可空) */
    private LocalDateTime scheduledAt;

    /** 浏览数 (查询返回) */
    private Integer viewCount;

    /** 点赞数 (查询返回) */
    private Integer likeCount;

    /** 分享数 (查询返回) */
    private Integer shareCount;

    /** 评论数 (查询返回) */
    private Integer commentCount;

    /** 收藏数 (查询返回) */
    private Integer collectCount;

    /** 转化数 (查询返回) */
    private Integer conversionCount;

    /** SEO 标题 (可空) */
    @Size(max = 200, message = "SEO 标题长度不能超过 200")
    private String seoTitle;

    /** SEO 描述 (可空) */
    @Size(max = 500, message = "SEO 描述长度不能超过 500")
    private String seoDescription;

    /** SEO 关键字 (逗号分隔, 可空) */
    @Size(max = 500, message = "SEO 关键字长度不能超过 500")
    private String seoKeywords;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
