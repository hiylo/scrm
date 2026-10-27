/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmFinanceKnowledgeReviewService.java
 * Date : 2026/10/23 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmAiKnowledgeDocumentEntity;
import org.hiylo.scrm.entity.ScrmKnowledgeArticleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAiKnowledgeDocumentRepository;
import org.hiylo.scrm.repository.ScrmKnowledgeArticleRepository;
import org.hiylo.scrm.service.ScrmKnowledgeVersionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块③ 版本复核服务: 「最后人工复核日期」落库 + 待复核检索 + 版本归档。
 * <p>
 * 两个承载域统一支持:
 * </p>
 * <ul>
 *   <li><b>AiKnowledgeDocument</b>（批量导入条目）: 复核动作更新
 *       lastReviewedAt / nextReviewAt / reviewCycleDays, 待复核检索按
 *       nextReviewAt &lt; now() OR nextReviewAt IS NULL;</li>
 *   <li><b>KnowledgeArticle</b>（知识库文章域）: 复核动作复用
 *       {@link ScrmKnowledgeVersionService#createVersion} 创建版本快照
 *       （版本号 +1, 旧版归档）, 并回写 lastReviewedAt / nextReviewAt;</li>
 * </ul>
 * <p>
 * 复核语义对齐 docs/tasks/T06/knowledge-template.md 总则第 6 条: 政策类信息每
 * 180 天至少人工复核一次, 复核后更新「最后人工复核日期」并做版本号 +1。
 * 复核人是业务人员（会计/资质顾问）, 不是 AI。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmFinanceKnowledgeReviewService {

    /** 默认复核周期 (天): 政策类信息建议 ≤180 天 */
    private static final int DEFAULT_REVIEW_CYCLE_DAYS = 180;

    /** AI 知识库文档数据访问层 (复核 AiKnowledgeDocument) */
    private final ScrmAiKnowledgeDocumentRepository documentRepository;

    /** 知识文章数据访问层 (复核 KnowledgeArticle + 待复核检索) */
    private final ScrmKnowledgeArticleRepository articleRepository;

    /** 知识版本管理服务 (复核时创建版本快照, 版本号 +1 旧版归档) */
    private final ScrmKnowledgeVersionService versionService;
    // ============================================================
    // AiKnowledgeDocument 复核 (批量导入条目)
    // ============================================================

    /**
     * 复核 AI 知识库文档条目: 更新最后人工复核日期 / 下次复核日期 / 复核周期。
     * <p>若文档尚未审核通过（审查状态非已审核）, 复核动作不改变其对外状态;
     * 复核只记录日期, 不替代审核闸门。</p>
     *
     * @param documentId  文档 ID
     * @param reviewedAt  复核日期 (可空, 默认当前时间)
     * @param cycleDays   复核周期(天) (可空, 默认 180)
     * @param reviewer    复核人 (业务人员, 可空)
     * @return 更新后的文档
     * @throws ScrmException 文档不存在
     */
    @Transactional
    public ScrmAiKnowledgeDocumentEntity reviewDocument(Long documentId, LocalDateTime reviewedAt,
                                                        Integer cycleDays, String reviewer)
            throws ScrmException {
        ScrmAiKnowledgeDocumentEntity entity = documentRepository.findById(documentId)
                .orElseThrow(() -> ScrmException.notFound("AI 知识库文档不存在: id=" + documentId));
        LocalDateTime reviewed = reviewedAt != null ? reviewedAt : LocalDateTime.now();
        int effectiveCycle = cycleDays != null && cycleDays > 0
                ? cycleDays : DEFAULT_REVIEW_CYCLE_DAYS;
        entity.setLastReviewedAt(reviewed);
        entity.setReviewCycleDays(effectiveCycle);
        entity.setNextReviewAt(reviewed.plusDays(effectiveCycle));
        entity = documentRepository.save(entity);
        log.info("AI 知识库文档复核: id={}, reviewedAt={}, nextReviewAt={}, reviewer={}",
                documentId, reviewed, entity.getNextReviewAt(), reviewer);
        return entity;
    }

    /**
     * 分页检索待复核的 AI 知识库文档（未复核或已过下次复核日期）。
     *
     * @param knowledgeBaseId 知识库 ID 过滤 (可空)
     * @param pageable        分页参数
     * @return 待复核文档分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAiKnowledgeDocumentEntity> listDocumentsDueForReview(Long knowledgeBaseId, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        return documentRepository.findDueForReview(knowledgeBaseId, now, pageable);
    }

    // ============================================================
    // KnowledgeArticle 复核 (知识库文章域, 版本化)
    // ============================================================

    /**
     * 复核知识文章: 创建版本快照（版本号 +1, 旧版归档）+ 更新复核日期字段。
     * <p>
     * 语义对齐 T06 总则第 6/8 条: 复核后更新「最后人工复核日期」并做版本号 +1;
     * 变更说明记录复核人 / 复核时间 / 变更摘要。
     * </p>
     *
     * @param articleId 文章 ID
     * @param reviewedAt 复核日期 (可空, 默认当前时间)
     * @param cycleDays  复核周期(天) (可空, 默认 180)
     * @param changeLog  变更说明 (复核做了什么, 可空)
     * @param reviewer   复核人 (业务人员, 可空)
     * @return 更新后的主文章
     * @throws ScrmException 文章不存在
     */
    @Transactional
    public ScrmKnowledgeArticleEntity reviewArticle(Long articleId, LocalDateTime reviewedAt,
                                                    Integer cycleDays, String changeLog, String reviewer)
            throws ScrmException {
        ScrmKnowledgeArticleEntity main = articleRepository.findById(articleId)
                .orElseThrow(() -> ScrmException.notFound("知识文章不存在: id=" + articleId));
        LocalDateTime reviewed = reviewedAt != null ? reviewedAt : LocalDateTime.now();
        int effectiveCycle = cycleDays != null && cycleDays > 0
                ? cycleDays : DEFAULT_REVIEW_CYCLE_DAYS;

        // 1. 版本快照: 当前内容归档为 {baseCode}_v{n}, 版本号 +1
        String changeLogText = buildChangeLog(reviewer, reviewed, changeLog);
        versionService.createVersion(articleId, changeLogText);

        // 2. 回写复核日期 (主文章)
        main.setLastReviewedAt(reviewed);
        main.setReviewCycleDays(effectiveCycle);
        main.setNextReviewAt(reviewed.plusDays(effectiveCycle));
        main.setLastModifiedAt(reviewed);
        main = articleRepository.save(main);
        log.info("知识文章版本复核: id={}, version={}, reviewedAt={}, reviewer={}",
                articleId, main.getVersionNumber(), reviewed, reviewer);
        return main;
    }

    /**
     * 分页检索待复核的知识文章（未复核或已过下次复核日期, 仅已发布/草稿/待审核）。
     *
     * @param pageable 分页参数
     * @return 待复核文章分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmKnowledgeArticleEntity> listArticlesDueForReview(Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        List<String> statuses = List.of(
                "DRAFT",
                "PENDING_REVIEW",
                "PUBLISHED");
        return articleRepository.findDueForReview(now, statuses, pageable);
    }

    /**
     * 待复核概览 (两个域数量)。
     *
     * @return Map: {documentsDue, articlesDue}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getReviewOverview() {
        LocalDateTime now = LocalDateTime.now();
        long docsDue = documentRepository.countDueForReview(null, now);
        long articlesDue = articleRepository.countDueForReview(now,
                List.of("DRAFT", "PENDING_REVIEW", "PUBLISHED"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentsDue", docsDue);
        result.put("articlesDue", articlesDue);
        result.put("reviewCycleDays", DEFAULT_REVIEW_CYCLE_DAYS);
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 构建变更说明。
     *
     * @param reviewer   复核人
     * @param reviewedAt 复核时间
     * @param changeLog  变更摘要
     * @return 变更说明文本
     */
    private String buildChangeLog(String reviewer, LocalDateTime reviewedAt, String changeLog) {
        StringBuilder sb = new StringBuilder();
        if (changeLog != null && !changeLog.isBlank()) {
            sb.append(changeLog);
        } else {
            sb.append("人工复核");
        }
        sb.append(" | 复核人: ").append(reviewer == null || reviewer.isBlank() ? "未署名" : reviewer);
        sb.append(" | 复核时间: ").append(reviewedAt);
        return sb.toString();
    }
}
