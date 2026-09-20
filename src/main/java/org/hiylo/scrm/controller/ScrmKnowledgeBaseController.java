/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeBaseController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmKnowledgeArticleDto;
import org.hiylo.scrm.dto.ScrmKnowledgeCategoryDto;
import org.hiylo.scrm.dto.ScrmKnowledgeFeedbackDto;
import org.hiylo.scrm.dto.ScrmKnowledgeReviewDto;
import org.hiylo.scrm.dto.ScrmKnowledgeSearchDto;
import org.hiylo.scrm.entity.ScrmKnowledgeArticleEntity;
import org.hiylo.scrm.entity.ScrmKnowledgeCategoryEntity;
import org.hiylo.scrm.entity.ScrmKnowledgeFeedbackEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmKnowledgeBaseService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 知识库控制器。
 * <p>
 * 提供知识库全流程接口: 分类管理 (CRUD / 树形 / 启停 / 移动 / 统计), 文章管理
 * (CRUD / 发布 / 归档 / 精选 / 置顶 / 复制 / 浏览), 版本管理 (创建 / 激活 / 回滚 / 对比),
 * 搜索 (关键词搜索 / 自动补全 / 搜索建议 / 热门词 / 相关文章), 反馈 (添加 / 解决 / 忽略 /
 * 评论 / 回复 / 评分), 审核 (提交 / 审核 / 批量 / 待审 / 历史), 统计 (概览 / 分类 / 文章 /
 * 搜索 / 反馈 / 贡献 / 趋势)。权限由 gateway-server 统一鉴权, {@code @RequirePermission}
 * 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/knowledge-base")
@RequiredArgsConstructor
public class ScrmKnowledgeBaseController {

    /** 知识库服务 */
    private final ScrmKnowledgeBaseService scrmKnowledgeBaseService;

    // ============================================================
    // 分类管理
    // ============================================================

    /**
     * 创建知识分类。
     *
     * @param dto 分类参数
     * @return 创建后的分类
     * @throws ScrmException 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_knowledge", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/categories")
    public OperationResponse<ScrmKnowledgeCategoryEntity> createCategory(
            @Valid @RequestBody ScrmKnowledgeCategoryDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.createCategory(dto));
    }

    /**
     * 更新知识分类。
     *
     * @param id  分类 ID
     * @param dto 分类参数
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_knowledge", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/categories/{id}")
    public OperationResponse<ScrmKnowledgeCategoryEntity> updateCategory(@PathVariable Long id,
                                                                          @RequestBody ScrmKnowledgeCategoryDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.updateCategory(id, dto));
    }

    /**
     * 删除知识分类 (存在子分类或关联文章时拒绝)。
     *
     * @param id 分类 ID
     * @return 空响应
     * @throws ScrmException 分类不存在 / 存在子分类 / 存在关联文章
     */
    @RequirePermission(resource = "scrm_knowledge", action = "delete")
    @DeleteMapping("/categories/{id}")
    public OperationResponse<Void> deleteCategory(@PathVariable Long id) throws ScrmException {
        scrmKnowledgeBaseService.deleteCategory(id);
        return OperationResponse.build();
    }

    /**
     * 查询知识分类详情。
     *
     * @param id 分类 ID
     * @return 分类详情
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/categories/{id}")
    public OperationResponse<ScrmKnowledgeCategoryEntity> getCategory(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.getCategory(id));
    }

    /**
     * 按编码查询知识分类。
     *
     * @param code 分类编码
     * @return 分类详情
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/categories/code/{code}")
    public OperationResponse<ScrmKnowledgeCategoryEntity> getCategoryByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.getCategoryByCode(code));
    }

    /**
     * 分页查询知识分类列表。
     *
     * @param parentId 父分类 ID 过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  分类名称关键字模糊匹配（可空）
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 分类分页结果
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/categories/list")
    public OperationResponse<Page<ScrmKnowledgeCategoryEntity>> listCategories(
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "sortOrder").and(Sort.by(Sort.Direction.ASC, "categoryLevel")));
        return OperationResponse.build(scrmKnowledgeBaseService.listCategories(parentId, enabled, keyword, pageable));
    }

    /**
     * 获取分类树。
     *
     * @return 分类树节点列表
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/categories/tree")
    public OperationResponse<List<Map<String, Object>>> getCategoryTree() {
        return OperationResponse.build(scrmKnowledgeBaseService.getCategoryTree());
    }

    /**
     * 启用知识分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "update")
    @PostMapping("/categories/{id}/enable")
    public OperationResponse<ScrmKnowledgeCategoryEntity> enableCategory(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.enableCategory(id));
    }

    /**
     * 禁用知识分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "update")
    @PostMapping("/categories/{id}/disable")
    public OperationResponse<ScrmKnowledgeCategoryEntity> disableCategory(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.disableCategory(id));
    }

    /**
     * 移动分类到新的父分类下。
     *
     * @param id          分类 ID
     * @param newParentId 新父分类 ID (可空, null 表示移到顶层)
     * @param newSortOrder 新排序序号 (可空, 默认 0)
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 父分类不能为自身
     */
    @RequirePermission(resource = "scrm_knowledge", action = "update")
    @PostMapping("/categories/{id}/move")
    public OperationResponse<ScrmKnowledgeCategoryEntity> moveCategory(
            @PathVariable Long id,
            @RequestParam(required = false) Long newParentId,
            @RequestParam(required = false) Integer newSortOrder) throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.moveCategory(id, newParentId, newSortOrder));
    }

    /**
     * 更新分类统计 (文章数 / 总浏览量 / 总点赞数)。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "update")
    @PostMapping("/categories/{id}/stats")
    public OperationResponse<ScrmKnowledgeCategoryEntity> updateCategoryStats(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.updateCategoryStats(id));
    }

    /**
     * 查询分类下的已发布文章。
     *
     * @param id   分类 ID
     * @param page 页码（从 0 开始, 默认 0）
     * @param size 每页大小（默认 20）
     * @return 文章分页结果
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/categories/{id}/articles")
    public OperationResponse<Page<ScrmKnowledgeArticleEntity>> getArticlesByCategory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmKnowledgeBaseService.getArticlesByCategory(id, pageable));
    }

    // ============================================================
    // 文章管理
    // ============================================================

    /**
     * 创建知识文章。
     *
     * @param dto 文章参数
     * @return 创建后的文章
     * @throws ScrmException 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_knowledge", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/articles")
    public OperationResponse<ScrmKnowledgeArticleEntity> createArticle(@Valid @RequestBody ScrmKnowledgeArticleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.createArticle(dto));
    }

    /**
     * 更新知识文章。
     *
     * @param id  文章 ID
     * @param dto 文章参数
     * @return 更新后的文章
     * @throws ScrmException 文章不存在 / 参数非法 / 状态非法
     */
    @RequirePermission(resource = "scrm_knowledge", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/articles/{id}")
    public OperationResponse<ScrmKnowledgeArticleEntity> updateArticle(@PathVariable Long id,
                                                                        @RequestBody ScrmKnowledgeArticleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.updateArticle(id, dto));
    }

    /**
     * 删除知识文章 (同时清理关联反馈)。
     *
     * @param id 文章 ID
     * @return 空响应
     * @throws ScrmException 文章不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "delete")
    @DeleteMapping("/articles/{id}")
    public OperationResponse<Void> deleteArticle(@PathVariable Long id) throws ScrmException {
        scrmKnowledgeBaseService.deleteArticle(id);
        return OperationResponse.build();
    }

    /**
     * 查询文章详情 (同时增加浏览量)。
     *
     * @param id 文章 ID
     * @return 文章详情
     * @throws ScrmException 文章不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/articles/{id}")
    public OperationResponse<ScrmKnowledgeArticleEntity> getArticle(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.getArticle(id));
    }

    /**
     * 按编码查询文章 (不增加浏览量)。
     *
     * @param code 文章编码
     * @return 文章详情
     * @throws ScrmException 文章不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/articles/code/{code}")
    public OperationResponse<ScrmKnowledgeArticleEntity> getArticleByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.getArticleByCode(code));
    }

    /**
     * 分页查询文章列表, 支持关键词 / 分类 / 类型 / 标签 / 难度过滤。
     *
     * @param keyword        关键词模糊匹配标题/摘要/内容/标签（可空）
     * @param categoryId     分类 ID 过滤（可空）
     * @param articleType    文章类型过滤（可空）
     * @param tags           标签过滤（可空）
     * @param difficultyLevel 难度过滤（可空）
     * @param status         状态过滤（可空, 默认 PUBLISHED）
     * @param authorId       作者 ID 过滤（可空）
     * @param sortBy         排序字段: RELEVANCE/VIEW_COUNT/LIKE_COUNT/RATING/CREATE_TIME（默认 RELEVANCE）
     * @param page           页码（从 0 开始, 默认 0）
     * @param size           每页大小（默认 20）
     * @return 文章分页结果
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/articles/list")
    public OperationResponse<Page<ScrmKnowledgeArticleEntity>> listArticles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String articleType,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String difficultyLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String authorId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ScrmKnowledgeSearchDto queryDto = new ScrmKnowledgeSearchDto();
        queryDto.setKeyword(keyword);
        queryDto.setCategoryId(categoryId);
        queryDto.setArticleType(articleType);
        queryDto.setTags(tags);
        queryDto.setDifficultyLevel(difficultyLevel);
        queryDto.setStatus(status);
        queryDto.setAuthorId(authorId);
        queryDto.setSortBy(sortBy);
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmKnowledgeBaseService.listArticles(queryDto, pageable));
    }

    /**
     * 发布文章。
     *
     * @param id 文章 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_knowledge", action = "update")
    @PostMapping("/articles/{id}/publish")
    public OperationResponse<ScrmKnowledgeArticleEntity> publishArticle(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.publishArticle(id));
    }

    /**
     * 归档文章。
     *
     * @param id 文章 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_knowledge", action = "update")
    @PostMapping("/articles/{id}/archive")
    public OperationResponse<ScrmKnowledgeArticleEntity> archiveArticle(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.archiveArticle(id));
    }

    /**
     * 设置文章为精选。
     *
     * @param id 文章 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "update")
    @PostMapping("/articles/{id}/feature")
    public OperationResponse<ScrmKnowledgeArticleEntity> featureArticle(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.featureArticle(id));
    }

    /**
     * 设置文章为置顶。
     *
     * @param id 文章 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "update")
    @PostMapping("/articles/{id}/pin")
    public OperationResponse<ScrmKnowledgeArticleEntity> pinArticle(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.pinArticle(id));
    }

    /**
     * 复制文章 (创建副本, 状态置 DRAFT)。
     *
     * @param id      源文章 ID
     * @param newCode 新文章编码
     * @return 复制后的文章
     * @throws ScrmException 源文章不存在 / 新编码已存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/articles/{id}/duplicate")
    public OperationResponse<ScrmKnowledgeArticleEntity> duplicateArticle(
            @PathVariable Long id, @RequestParam String newCode) throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.duplicateArticle(id, newCode));
    }

    /**
     * 获取精选文章。
     *
     * @param limit 返回条数 (默认 10)
     * @return 精选文章列表
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/articles/featured")
    public OperationResponse<List<ScrmKnowledgeArticleEntity>> getFeaturedArticles(
            @RequestParam(defaultValue = "10") Integer limit) {
        return OperationResponse.build(scrmKnowledgeBaseService.getFeaturedArticles(limit));
    }

    /**
     * 获取置顶文章。
     *
     * @return 置顶文章列表
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/articles/pinned")
    public OperationResponse<List<ScrmKnowledgeArticleEntity>> getPinnedArticles() {
        return OperationResponse.build(scrmKnowledgeBaseService.getPinnedArticles());
    }

    /**
     * 获取热门文章。
     *
     * @param limit 返回条数 (默认 10)
     * @return 热门文章列表
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/articles/popular")
    public OperationResponse<List<ScrmKnowledgeArticleEntity>> getPopularArticles(
            @RequestParam(defaultValue = "10") Integer limit) {
        return OperationResponse.build(scrmKnowledgeBaseService.getPopularArticles(limit));
    }

    /**
     * 获取最新文章。
     *
     * @param limit 返回条数 (默认 10)
     * @return 最新文章列表
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/articles/recent")
    public OperationResponse<List<ScrmKnowledgeArticleEntity>> getRecentArticles(
            @RequestParam(defaultValue = "10") Integer limit) {
        return OperationResponse.build(scrmKnowledgeBaseService.getRecentArticles(limit));
    }

    /**
     * 按标签查询已发布文章。
     *
     * @param tag  标签
     * @param page 页码（从 0 开始, 默认 0）
     * @param size 每页大小（默认 20）
     * @return 文章分页结果
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/articles/by-tag/{tag}")
    public OperationResponse<Page<ScrmKnowledgeArticleEntity>> getArticlesByTag(
            @PathVariable String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmKnowledgeBaseService.getArticlesByTag(tag, pageable));
    }

    /**
     * 增加文章浏览量 (去重)。
     *
     * @param id     文章 ID
     * @param userId 用户 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @RateLimit(capacity = 120, refillTokens = 120, refillPeriodSeconds = 60)
    @PostMapping("/articles/{id}/view")
    public OperationResponse<ScrmKnowledgeArticleEntity> incrementViewCount(
            @PathVariable Long id, @RequestParam String userId) throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.incrementViewCount(id, userId));
    }

    /**
     * 更新文章统计 (根据反馈类型)。
     *
     * @param id           文章 ID
     * @param feedbackType 反馈类型
     * @return 更新后的文章
     * @throws ScrmException 文章不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "update")
    @PostMapping("/articles/{id}/stats")
    public OperationResponse<ScrmKnowledgeArticleEntity> updateArticleStats(
            @PathVariable Long id, @RequestParam String feedbackType) throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.updateArticleStats(id, feedbackType));
    }

    // ============================================================
    // 版本管理
    // ============================================================

    /**
     * 创建文章版本快照。
     *
     * @param articleId 文章 ID
     * @param changeLog 变更日志（可空）
     * @return 创建的版本快照
     * @throws ScrmException 文章不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/versions")
    public OperationResponse<ScrmKnowledgeArticleEntity> createVersion(
            @RequestParam Long articleId,
            @RequestParam(required = false) String changeLog) throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.createVersion(articleId, changeLog));
    }

    /**
     * 查询文章的全部版本。
     *
     * @param articleId 文章 ID
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 版本分页结果
     * @throws ScrmException 文章不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/versions/list")
    public OperationResponse<Page<ScrmKnowledgeArticleEntity>> getVersions(
            @RequestParam Long articleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmKnowledgeBaseService.getVersions(articleId, pageable));
    }

    /**
     * 查询版本详情。
     *
     * @param id 版本 ID
     * @return 版本详情
     * @throws ScrmException 版本不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/versions/{id}")
    public OperationResponse<ScrmKnowledgeArticleEntity> getVersion(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.getVersion(id));
    }

    /**
     * 激活指定版本。
     *
     * @param id 版本 ID
     * @return 主文章
     * @throws ScrmException 版本不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "update")
    @PostMapping("/versions/{id}/activate")
    public OperationResponse<ScrmKnowledgeArticleEntity> activateVersion(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.activateVersion(id));
    }

    /**
     * 回滚到指定版本。
     *
     * @param articleId 主文章 ID
     * @param versionId 版本 ID
     * @return 回滚后的主文章
     * @throws ScrmException 文章 / 版本不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "update")
    @PostMapping("/versions/rollback")
    public OperationResponse<ScrmKnowledgeArticleEntity> rollbackToVersion(
            @RequestParam Long articleId, @RequestParam Long versionId) throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.rollbackToVersion(articleId, versionId));
    }

    /**
     * 对比两个版本。
     *
     * @param v1Id 版本 1 ID
     * @param v2Id 版本 2 ID
     * @return 差异 Map
     * @throws ScrmException 版本不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @PostMapping("/versions/compare")
    public OperationResponse<Map<String, Object>> compareVersions(
            @RequestParam Long v1Id, @RequestParam Long v2Id) throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.compareVersions(v1Id, v2Id));
    }

    // ============================================================
    // 搜索
    // ============================================================

    /**
     * 搜索知识文章 (关键词匹配标题/摘要/内容/标签, 排序按相关度+浏览量+点赞数)。
     *
     * @param queryDto 查询参数
     * @return 搜索结果分页
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/search")
    public OperationResponse<Page<ScrmKnowledgeArticleEntity>> search(@RequestBody ScrmKnowledgeSearchDto queryDto) {
        return OperationResponse.build(scrmKnowledgeBaseService.search(queryDto));
    }

    /**
     * 自动补全建议 (按标题前缀匹配)。
     *
     * @param prefix 标题前缀
     * @return 建议标题列表
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/auto-complete")
    public OperationResponse<List<String>> autoComplete(@RequestParam String prefix) {
        return OperationResponse.build(scrmKnowledgeBaseService.autoComplete(prefix));
    }

    /**
     * 搜索建议 (关键词匹配, 返回文章简要信息)。
     *
     * @param keyword 关键词
     * @return 建议列表
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/suggestions")
    public OperationResponse<List<Map<String, Object>>> getSearchSuggestions(@RequestParam String keyword) {
        return OperationResponse.build(scrmKnowledgeBaseService.getSearchSuggestions(keyword));
    }

    /**
     * 热门搜索词 (基于文章标签统计)。
     *
     * @param limit 返回条数 (默认 10)
     * @return 热门标签列表
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/popular-keywords")
    public OperationResponse<List<String>> getPopularKeywords(@RequestParam(defaultValue = "10") Integer limit) {
        return OperationResponse.build(scrmKnowledgeBaseService.getPopularKeywords(limit));
    }

    /**
     * 相关文章推荐 (基于同分类 / 共同标签)。
     *
     * @param articleId 文章 ID
     * @param limit     返回条数 (默认 5)
     * @return 相关文章列表
     * @throws ScrmException 文章不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/{articleId}/related")
    public OperationResponse<List<ScrmKnowledgeArticleEntity>> getRelatedArticles(
            @PathVariable Long articleId,
            @RequestParam(defaultValue = "5") Integer limit) throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.getRelatedArticles(articleId, limit));
    }

    // ============================================================
    // 反馈
    // ============================================================

    /**
     * 添加知识反馈 (同时更新文章统计)。
     *
     * @param dto 反馈参数
     * @return 创建后的反馈
     * @throws ScrmException 参数非法 / 文章不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "create")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/feedback")
    public OperationResponse<ScrmKnowledgeFeedbackEntity> addFeedback(@Valid @RequestBody ScrmKnowledgeFeedbackDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.addFeedback(dto));
    }

    /**
     * 查询反馈详情。
     *
     * @param id 反馈 ID
     * @return 反馈详情
     * @throws ScrmException 反馈不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/feedback/{id}")
    public OperationResponse<ScrmKnowledgeFeedbackEntity> getFeedback(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.getFeedback(id));
    }

    /**
     * 分页查询反馈列表。
     *
     * @param articleId    文章 ID 过滤（可空）
     * @param feedbackType 反馈类型过滤（可空）
     * @param status       状态过滤（可空）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 反馈分页结果
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/feedback/list")
    public OperationResponse<Page<ScrmKnowledgeFeedbackEntity>> listFeedback(
            @RequestParam(required = false) Long articleId,
            @RequestParam(required = false) String feedbackType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmKnowledgeBaseService.listFeedback(
                articleId, feedbackType, status, pageable));
    }

    /**
     * 解决反馈。
     *
     * @param id   反馈 ID
     * @param note 解决备注
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "update")
    @PostMapping("/feedback/{id}/resolve")
    public OperationResponse<ScrmKnowledgeFeedbackEntity> resolveFeedback(
            @PathVariable Long id, @RequestParam(required = false) String note) throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.resolveFeedback(id, note));
    }

    /**
     * 忽略反馈。
     *
     * @param id   反馈 ID
     * @param note 忽略备注
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "update")
    @PostMapping("/feedback/{id}/dismiss")
    public OperationResponse<ScrmKnowledgeFeedbackEntity> dismissFeedback(
            @PathVariable Long id, @RequestParam(required = false) String note) throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.dismissFeedback(id, note));
    }

    /**
     * 获取文章评论列表。
     *
     * @param articleId 文章 ID
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 评论分页结果
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/feedback/comments/{articleId}")
    public OperationResponse<Page<ScrmKnowledgeFeedbackEntity>> getComments(
            @PathVariable Long articleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmKnowledgeBaseService.getComments(articleId, pageable));
    }

    /**
     * 回复评论。
     *
     * @param feedbackId 父评论 ID
     * @param content    回复内容
     * @return 创建后的回复
     * @throws ScrmException 父评论不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/feedback/reply")
    public OperationResponse<ScrmKnowledgeFeedbackEntity> replyToComment(
            @RequestParam Long feedbackId, @RequestParam String content) throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.replyToComment(feedbackId, content));
    }

    /**
     * 评分。
     *
     * @param articleId 文章 ID
     * @param userId    用户 ID
     * @param rating    评分 1-5
     * @return 创建后的评分反馈
     * @throws ScrmException 文章不存在 / 评分非法
     */
    @RequirePermission(resource = "scrm_knowledge", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/feedback/rate")
    public OperationResponse<ScrmKnowledgeFeedbackEntity> rateArticle(
            @RequestParam Long articleId, @RequestParam String userId, @RequestParam Integer rating)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.rateArticle(articleId, userId, rating));
    }

    /**
     * 获取用户对文章的评分。
     *
     * @param articleId 文章 ID
     * @param userId    用户 ID
     * @return 评分反馈 (不存在返回 null)
     * @throws ScrmException 文章不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/feedback/rating")
    public OperationResponse<ScrmKnowledgeFeedbackEntity> getArticleRating(
            @RequestParam Long articleId, @RequestParam String userId) throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.getArticleRating(articleId, userId));
    }

    // ============================================================
    // 审核
    // ============================================================

    /**
     * 提交审核。
     *
     * @param articleId 文章 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_knowledge", action = "update")
    @PostMapping("/reviews/submit")
    public OperationResponse<ScrmKnowledgeArticleEntity> submitForReview(@RequestParam Long articleId)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.submitForReview(articleId));
    }

    /**
     * 审核文章 (通过 / 驳回)。
     *
     * @param reviewDto 审核参数 (articleId + action: APPROVE/REJECT + comment)
     * @return 更新后的文章
     * @throws ScrmException 文章不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_knowledge", action = "review")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/reviews/review")
    public OperationResponse<ScrmKnowledgeArticleEntity> review(@Valid @RequestBody ScrmKnowledgeReviewDto reviewDto)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.review(reviewDto));
    }

    /**
     * 批量审核文章。
     *
     * @param articleIds 文章 ID 列表
     * @param action     审核动作 APPROVE / REJECT
     * @param comment    审核意见（可空）
     * @return 各文章审核结果
     */
    @RequirePermission(resource = "scrm_knowledge", action = "review")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/reviews/batch")
    public OperationResponse<List<Map<String, Object>>> batchReview(
            @RequestParam List<Long> articleIds,
            @RequestParam String action,
            @RequestParam(required = false) String comment) {
        return OperationResponse.build(scrmKnowledgeBaseService.batchReview(articleIds, action, comment));
    }

    /**
     * 获取待审核文章。
     *
     * @param page 页码（从 0 开始, 默认 0）
     * @param size 每页大小（默认 20）
     * @return 待审核文章分页结果
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/reviews/pending")
    public OperationResponse<Page<ScrmKnowledgeArticleEntity>> getPendingReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmKnowledgeBaseService.getPendingReviews(pageable));
    }

    /**
     * 获取文章审核历史。
     *
     * @param articleId 文章 ID
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 审核历史分页结果
     * @throws ScrmException 文章不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/reviews/history/{articleId}")
    public OperationResponse<Page<ScrmKnowledgeArticleEntity>> getReviewHistory(
            @PathVariable Long articleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmKnowledgeBaseService.getReviewHistory(articleId, pageable));
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 知识库统计概览。
     *
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getKnowledgeStats() {
        return OperationResponse.build(scrmKnowledgeBaseService.getKnowledgeStats());
    }

    /**
     * 分类统计。
     *
     * @return 分类统计列表
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/stats/categories")
    public OperationResponse<List<Map<String, Object>>> getCategoryStats() {
        return OperationResponse.build(scrmKnowledgeBaseService.getCategoryStats());
    }

    /**
     * 单文章统计详情。
     *
     * @param articleId 文章 ID
     * @return 统计详情 Map
     * @throws ScrmException 文章不存在
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/stats/article/{articleId}")
    public OperationResponse<Map<String, Object>> getArticleStats(@PathVariable Long articleId)
            throws ScrmException {
        return OperationResponse.build(scrmKnowledgeBaseService.getArticleStats(articleId));
    }

    /**
     * 搜索统计。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/stats/search")
    public OperationResponse<Map<String, Object>> getSearchStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmKnowledgeBaseService.getSearchStats(startTime, endTime));
    }

    /**
     * 反馈统计。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/stats/feedback")
    public OperationResponse<Map<String, Object>> getFeedbackStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmKnowledgeBaseService.getFeedbackStats(startTime, endTime));
    }

    /**
     * 贡献统计 (按作者分组)。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 贡献统计列表
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/stats/contribution")
    public OperationResponse<List<Map<String, Object>>> getContributionStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmKnowledgeBaseService.getContributionStats(startTime, endTime));
    }

    /**
     * 趋势统计 (每日新增文章数)。
     *
     * @param days 统计天数 (默认 30)
     * @return 趋势列表
     */
    @RequirePermission(resource = "scrm_knowledge", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<List<Map<String, Object>>> getTrend(
            @RequestParam(defaultValue = "30") Integer days) {
        return OperationResponse.build(scrmKnowledgeBaseService.getTrend(days));
    }
}
