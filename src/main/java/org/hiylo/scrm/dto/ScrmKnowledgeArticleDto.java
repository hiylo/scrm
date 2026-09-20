/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeArticleDto.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 知识文章 DTO。
 * <p>
 * 对应 {@code ScrmKnowledgeArticleEntity} 的业务字段, 创建/更新接口入参。
 * 各类 *Count / avgRating / helpfulRate / reviewedAt / publishedAt 字段为查询返回,
 * 由服务端管理。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmKnowledgeArticleDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 文章标题 */
    @NotBlank(message = "文章标题不能为空")
    @Size(max = 500, message = "文章标题长度不能超过 500")
    private String title;

    /** 文章编码 (唯一) */
    @NotBlank(message = "文章编码不能为空")
    @Size(max = 50, message = "文章编码长度不能超过 50")
    @Pattern(regexp = "^[A-Za-z0-9_\\-]+$", message = "文章编码仅支持字母/数字/下划线/连字符")
    private String articleCode;

    /** 分类 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    /** 分类名称 (查询返回, 由服务端根据 categoryId 填充) */
    private String categoryName;

    /** 摘要 (可空) */
    @Size(max = 1000, message = "摘要长度不能超过 1000")
    private String summary;

    /** 文章内容 (Markdown / HTML / PLAIN / JSON) */
    @NotBlank(message = "文章内容不能为空")
    private String content;

    /** 内容类型: MARKDOWN / HTML / PLAIN / JSON (默认 MARKDOWN) */
    @Pattern(regexp = "MARKDOWN|HTML|PLAIN|JSON", message = "内容类型仅支持 MARKDOWN/HTML/PLAIN/JSON")
    private String contentType;

    /** 文章类型: ARTICLE/FAQ/TUTORIAL/GUIDE/POLICY/PRODUCT_DOC/TROUBLESHOOTING/BEST_PRACTICE (默认 ARTICLE) */
    @Pattern(regexp = "ARTICLE|FAQ|TUTORIAL|GUIDE|POLICY|PRODUCT_DOC|TROUBLESHOOTING|BEST_PRACTICE",
            message = "文章类型非法")
    private String articleType;

    /** 标签 (可空, 逗号分隔) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 关键词 (可空, 逗号分隔) */
    @Size(max = 500, message = "关键词长度不能超过 500")
    private String keywords;

    /** 封面图 URL (可空) */
    @Size(max = 500, message = "封面图 URL 长度不能超过 500")
    private String coverImage;

    /** 附件列表 (可空, JSON 字符串) */
    @Size(max = 1000, message = "附件列表长度不能超过 1000")
    private String attachments;

    /** 关联文章 ID (可空, 逗号分隔) */
    @Size(max = 500, message = "关联文章长度不能超过 500")
    private String relatedArticles;

    /** 关联产品 (可空, 逗号分隔) */
    @Size(max = 500, message = "关联产品长度不能超过 500")
    private String relatedProducts;

    /** 适用场景 (可空, 逗号分隔) */
    @Size(max = 500, message = "适用场景长度不能超过 500")
    private String applicableScenarios;

    /** 难度: BEGINNER / INTERMEDIATE / ADVANCED / EXPERT (默认 BEGINNER) */
    @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED|EXPERT", message = "难度仅支持 BEGINNER/INTERMEDIATE/ADVANCED/EXPERT")
    private String difficultyLevel;

    /** 预计阅读时长分钟 (可空, 默认 5) */
    private Integer readingTimeMinutes;

    /** 状态: DRAFT/PENDING_REVIEW/PUBLISHED/ARCHIVED/REJECTED (查询返回) */
    @Pattern(regexp = "DRAFT|PENDING_REVIEW|PUBLISHED|ARCHIVED|REJECTED",
            message = "状态仅支持 DRAFT/PENDING_REVIEW/PUBLISHED/ARCHIVED/REJECTED")
    private String status;

    /** 文章版本号 (查询返回) */
    private Integer versionNumber;

    /** 当前激活版本 ID (查询返回) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentVersionId;

    /** 审核状态 (查询返回) */
    private String reviewStatus;

    /** 审核人 (查询返回) */
    private String reviewedBy;

    /** 审核时间 (查询返回) */
    private LocalDateTime reviewedAt;

    /** 审核意见 (查询返回) */
    private String reviewComment;

    /** 发布时间 (查询返回) */
    private LocalDateTime publishedAt;

    /** 最后修改时间 (查询返回) */
    private LocalDateTime lastModifiedAt;

    /** 作者 ID (可空) */
    @Size(max = 100, message = "作者 ID 长度不能超过 100")
    private String authorId;

    /** 作者名称 (可空) */
    @Size(max = 100, message = "作者名称长度不能超过 100")
    private String authorName;

    /** 浏览量 (查询返回) */
    private Integer viewCount;

    /** 独立浏览量 (查询返回) */
    private Integer uniqueViewCount;

    /** 点赞数 (查询返回) */
    private Integer likeCount;

    /** 踩数 (查询返回) */
    private Integer dislikeCount;

    /** 收藏数 (查询返回) */
    private Integer favoriteCount;

    /** 分享数 (查询返回) */
    private Integer shareCount;

    /** 评论数 (查询返回) */
    private Integer commentCount;

    /** 有用数 (查询返回) */
    private Integer helpfulCount;

    /** 无用数 (查询返回) */
    private Integer notHelpfulCount;

    /** 有用率 (查询返回) */
    private Double helpfulRate;

    /** 平均评分 (查询返回) */
    private Double avgRating;

    /** 评分人数 (查询返回) */
    private Integer ratingCount;

    /** 是否精选 (查询返回) */
    private Boolean isFeatured;

    /** 是否置顶 (查询返回) */
    private Boolean isPinned;

    /** 排序序号 (可空) */
    private Integer sortOrder;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;
}
