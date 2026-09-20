/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeSearchDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * SCRM 知识搜索 DTO。
 * <p>
 * 知识库搜索/列表查询入参, 支持关键词匹配标题/摘要/内容/标签, 按分类 / 文章类型 /
 * 标签 / 难度过滤, 并支持相关度 + 浏览量 + 点赞数排序。{@code pageable} 由 Controller
 * 构造后传入, DTO 仅承载过滤条件。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmKnowledgeSearchDto {

    /** 关键词 (可空, 匹配标题/摘要/内容/标签) */
    private String keyword;

    /** 分类 ID 过滤 (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    /** 文章类型过滤 (可空): ARTICLE/FAQ/TUTORIAL/GUIDE/POLICY/PRODUCT_DOC/TROUBLESHOOTING/BEST_PRACTICE */
    private String articleType;

    /** 标签过滤 (可空, 单标签) */
    private String tags;

    /** 难度过滤 (可空): BEGINNER/INTERMEDIATE/ADVANCED/EXPERT */
    private String difficultyLevel;

    /** 状态过滤 (可空, 默认仅查 PUBLISHED), 多状态逗号分隔 */
    private String status;

    /** 作者 ID 过滤 (可空) */
    private String authorId;

    /** 排序字段 (可空): RELEVANCE/VIEW_COUNT/LIKE_COUNT/RATING/CREATE_TIME (默认 RELEVANCE) */
    private String sortBy;

    /** 多标签过滤 (可空, 与 tags 为或关系) */
    private List<String> tagList;
}
