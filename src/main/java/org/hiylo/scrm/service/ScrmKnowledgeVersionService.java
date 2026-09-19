/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeVersionService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmKnowledgeArticleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmKnowledgeArticleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * SCRM 知识文章版本管理服务。
 * <p>
 * 承载知识库版本子域: 创建版本快照 / 版本分页查询 / 版本详情 / 激活指定版本 / 回滚到指定版本
 * / 版本对比。版本基于文章实体本身: 每个版本快照为独立的文章行, 编码为 {baseCode}_v{n}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmKnowledgeVersionService {

    /** 版本快照编码后缀模板 */
    private static final String VERSION_CODE_SUFFIX = "_v";

    /** 知识文章数据访问层 */
    private final ScrmKnowledgeArticleRepository articleRepository;

    /** 知识文章管理服务 (文章查找 / 按编码查找主文章 / 当前操作人) */
    private final ScrmKnowledgeArticleService articleService;

    // ============================================================
    // 版本管理
    // ============================================================

    /**
     * 创建文章版本快照 (复制当前文章内容为独立行, 编码 {baseCode}_v{n})。
     * <p>
     * 变更日志存储在版本快照的 reviewComment 字段中 (版本快照不参与审核)。
     * </p>
     *
     * @param articleId 文章 ID
     * @param changeLog 变更日志 (可空)
     * @return 创建的版本快照
     * @throws ScrmException 文章不存在
     */
    @Transactional
    public ScrmKnowledgeArticleEntity createVersion(Long articleId, String changeLog) throws ScrmException {
        ScrmKnowledgeArticleEntity source = articleService.findArticleOrThrow(articleId);
        String baseCode = extractBaseCode(source.getArticleCode());
        int newVersion = ScrmKnowledgeCategoryService.safeInt(source.getVersionNumber()) + 1;
        String versionCode = baseCode + VERSION_CODE_SUFFIX + newVersion;
        // 确保版本编码不重复
        if (articleRepository.existsByArticleCode(versionCode)) {
            int suffix = 1;
            while (articleRepository.existsByArticleCode(versionCode + "_" + suffix)) {
                suffix++;
            }
            versionCode = versionCode + "_" + suffix;
        }
        ScrmKnowledgeArticleEntity snapshot = new ScrmKnowledgeArticleEntity();
        snapshot.setTitle(source.getTitle());
        snapshot.setArticleCode(versionCode);
        snapshot.setCategoryId(source.getCategoryId());
        snapshot.setCategoryName(source.getCategoryName());
        snapshot.setSummary(source.getSummary());
        snapshot.setContent(source.getContent());
        snapshot.setContentType(source.getContentType());
        snapshot.setArticleType(source.getArticleType());
        snapshot.setTags(source.getTags());
        snapshot.setKeywords(source.getKeywords());
        snapshot.setCoverImage(source.getCoverImage());
        snapshot.setAttachments(source.getAttachments());
        snapshot.setRelatedArticles(source.getRelatedArticles());
        snapshot.setRelatedProducts(source.getRelatedProducts());
        snapshot.setApplicableScenarios(source.getApplicableScenarios());
        snapshot.setDifficultyLevel(source.getDifficultyLevel());
        snapshot.setReadingTimeMinutes(source.getReadingTimeMinutes());
        snapshot.setStatus(source.getStatus());
        snapshot.setVersionNumber(newVersion);
        snapshot.setReviewStatus(source.getReviewStatus());
        snapshot.setReviewComment(changeLog);
        snapshot.setPublishedAt(source.getPublishedAt());
        snapshot.setLastModifiedAt(LocalDateTime.now());
        snapshot.setAuthorId(source.getAuthorId());
        snapshot.setAuthorName(source.getAuthorName());
        snapshot.setIsFeatured(source.getIsFeatured());
        snapshot.setIsPinned(source.getIsPinned());
        snapshot.setSortOrder(source.getSortOrder());
        snapshot.setCreatedBy(ScrmKnowledgeCategoryService.currentOperator());
        snapshot = articleRepository.save(snapshot);
        // 主文章的 currentVersionId 指向最新快照
        source.setCurrentVersionId(snapshot.getId());
        articleRepository.save(source);
        log.info("创建文章版本: articleId={}, versionId={}, versionNumber={}", articleId, snapshot.getId(), newVersion);
        return snapshot;
    }

    /**
     * 查询文章的全部版本 (按 versionNumber DESC)。
     *
     * @param articleId 文章 ID
     * @param pageable  分页参数
     * @return 版本分页结果 (包含主文章与全部版本快照)
     * @throws ScrmException 文章不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmKnowledgeArticleEntity> getVersions(Long articleId, Pageable pageable) throws ScrmException {
        ScrmKnowledgeArticleEntity article = articleService.findArticleOrThrow(articleId);
        String baseCode = extractBaseCode(article.getArticleCode());
        String versionPrefix = baseCode + VERSION_CODE_SUFFIX;
        Specification<ScrmKnowledgeArticleEntity> spec = (root, query, cb) -> cb.and(
                cb.or(
                        cb.equal(root.get("articleCode"), baseCode),
                        cb.like(root.get("articleCode"), versionPrefix + "%")
                ));
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "versionNumber"));
        return articleRepository.findAll(spec, sortedPageable);
    }

    /**
     * 查询版本详情。
     *
     * @param id 版本 ID
     * @return 版本实体
     * @throws ScrmException 版本不存在
     */
    @Transactional(readOnly = true)
    public ScrmKnowledgeArticleEntity getVersion(Long id) throws ScrmException {
        return articleService.findArticleOrThrow(id);
    }

    /**
     * 激活指定版本 (设置主文章的 currentVersionId 指向该版本)。
     *
     * @param versionId 版本 ID
     * @return 主文章
     * @throws ScrmException 版本不存在
     */
    @Transactional
    public ScrmKnowledgeArticleEntity activateVersion(Long versionId) throws ScrmException {
        ScrmKnowledgeArticleEntity version = articleService.findArticleOrThrow(versionId);
        ScrmKnowledgeArticleEntity main = findMainArticle(version.getArticleCode());
        main.setCurrentVersionId(version.getId());
        return articleRepository.save(main);
    }

    /**
     * 回滚到指定版本 (将版本内容复制回主文章, 版本号 +1)。
     *
     * @param articleId 主文章 ID
     * @param versionId 版本 ID
     * @return 回滚后的主文章
     * @throws ScrmException 文章 / 版本不存在
     */
    @Transactional
    public ScrmKnowledgeArticleEntity rollbackToVersion(Long articleId, Long versionId) throws ScrmException {
        ScrmKnowledgeArticleEntity main = articleService.findArticleOrThrow(articleId);
        ScrmKnowledgeArticleEntity version = articleService.findArticleOrThrow(versionId);
        if (!extractBaseCode(version.getArticleCode()).equals(extractBaseCode(main.getArticleCode()))) {
            throw ScrmException.badRequest("版本与文章不匹配: articleId=" + articleId + ", versionId=" + versionId);
        }
        main.setTitle(version.getTitle());
        main.setSummary(version.getSummary());
        main.setContent(version.getContent());
        main.setContentType(version.getContentType());
        main.setArticleType(version.getArticleType());
        main.setTags(version.getTags());
        main.setKeywords(version.getKeywords());
        main.setCoverImage(version.getCoverImage());
        main.setAttachments(version.getAttachments());
        main.setRelatedArticles(version.getRelatedArticles());
        main.setRelatedProducts(version.getRelatedProducts());
        main.setApplicableScenarios(version.getApplicableScenarios());
        main.setDifficultyLevel(version.getDifficultyLevel());
        main.setReadingTimeMinutes(version.getReadingTimeMinutes());
        main.setVersionNumber(ScrmKnowledgeCategoryService.safeInt(main.getVersionNumber()) + 1);
        main.setCurrentVersionId(version.getId());
        main.setLastModifiedAt(LocalDateTime.now());
        return articleRepository.save(main);
    }

    /**
     * 对比两个版本 (返回字段差异 Map)。
     *
     * @param v1Id 版本 1 ID
     * @param v2Id 版本 2 ID
     * @return 差异 Map
     * @throws ScrmException 版本不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> compareVersions(Long v1Id, Long v2Id) throws ScrmException {
        ScrmKnowledgeArticleEntity v1 = articleService.findArticleOrThrow(v1Id);
        ScrmKnowledgeArticleEntity v2 = articleService.findArticleOrThrow(v2Id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version1", buildVersionBrief(v1));
        result.put("version2", buildVersionBrief(v2));
        Map<String, Object[]> diffs = new LinkedHashMap<>();
        putDiff(diffs, "title", v1.getTitle(), v2.getTitle());
        putDiff(diffs, "summary", v1.getSummary(), v2.getSummary());
        putDiff(diffs, "content", v1.getContent(), v2.getContent());
        putDiff(diffs, "contentType", v1.getContentType(), v2.getContentType());
        putDiff(diffs, "articleType", v1.getArticleType(), v2.getArticleType());
        putDiff(diffs, "tags", v1.getTags(), v2.getTags());
        putDiff(diffs, "keywords", v1.getKeywords(), v2.getKeywords());
        putDiff(diffs, "difficultyLevel", v1.getDifficultyLevel(), v2.getDifficultyLevel());
        putDiff(diffs, "status", v1.getStatus(), v2.getStatus());
        result.put("diffs", diffs);
        result.put("identical", diffs.isEmpty());
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 从文章编码提取基础编码 (去掉版本后缀 _v{n})。
     *
     * @param articleCode 文章编码
     * @return 基础编码
     */
    private String extractBaseCode(String articleCode) {
        if (articleCode == null) {
            return articleCode;
        }
        int idx = articleCode.indexOf(VERSION_CODE_SUFFIX);
        if (idx > 0) {
            return articleCode.substring(0, idx);
        }
        return articleCode;
    }

    /**
     * 按基础编码查找主文章 (编码等于 baseCode 的文章)。
     *
     * @param articleCode 文章编码 (可能是版本编码)
     * @return 主文章
     * @throws ScrmException 主文章不存在
     */
    private ScrmKnowledgeArticleEntity findMainArticle(String articleCode) throws ScrmException {
        String baseCode = extractBaseCode(articleCode);
        return articleService.getArticleByCode(baseCode);
    }

    /**
     * 构建版本简要信息。
     *
     * @param article 文章/版本实体
     * @return 简要 Map
     */
    private Map<String, Object> buildVersionBrief(ScrmKnowledgeArticleEntity article) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", article.getId());
        m.put("articleCode", article.getArticleCode());
        m.put("title", article.getTitle());
        m.put("versionNumber", ScrmKnowledgeCategoryService.safeInt(article.getVersionNumber()));
        m.put("status", article.getStatus());
        m.put("reviewComment", article.getReviewComment());
        m.put("lastModifiedAt", article.getLastModifiedAt());
        m.put("createTime", article.getCreateTime());
        return m;
    }

    /**
     * 记录字段差异 (仅在值不同时记录)。
     *
     * @param diffs  差异 Map
     * @param field  字段名
     * @param val1   版本 1 的值
     * @param val2   版本 2 的值
     */
    private void putDiff(Map<String, Object[]> diffs, String field, Object val1, Object val2) {
        if (!Objects.equals(val1, val2)) {
            diffs.put(field, new Object[]{val1, val2});
        }
    }
}