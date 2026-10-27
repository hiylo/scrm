/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmFinanceKnowledgeImportService.java
 * Date : 2026/10/23 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.ScrmFinanceKnowledgeProperties;
import org.hiylo.scrm.entity.ScrmAiKnowledgeBaseEntity;
import org.hiylo.scrm.entity.ScrmAiKnowledgeDocumentEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAiKnowledgeBaseRepository;
import org.hiylo.scrm.repository.ScrmAiKnowledgeDocumentRepository;
import org.hiylo.scrm.service.finance.model.KnowledgeTemplateEntry;
import org.hiylo.scrm.service.finance.parser.KnowledgeDocxParser;
import org.hiylo.scrm.service.finance.parser.KnowledgeTemplateParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块③ 财税知识库 T06 模板批量导入服务。
 * <p>
 * 按 {@code docs/tasks/T06/knowledge-template.md} 规范将 docx/md 模板文件解析为
 * {@link KnowledgeTemplateEntry}, 落库为 {@link ScrmAiKnowledgeDocumentEntity}:
 * </p>
 * <ul>
 *   <li>sourceType=IMPORT（AI 起草内容经业务人员审核后可按 T06 规范导入, 入库默认
 *       status=草稿 / 审查状态=待人工审核, 不进入对外问答, 对齐模板总则第 7 条）</li>
 *   <li>最后人工复核日期 / 建议下次复核日期 / 复核周期(天) 落库（V11 新增列）</li>
 *   <li>标题幂等去重（existsByTitle）: 同标题重复导入跳过, 不产生重复条目</li>
 *   <li>4 个财税分类自动归入同一知识库（kbName=财税知识库, category=POLICY）,
 *       分类信息写入 tags 便于检索; 库不存在则自动创建</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmFinanceKnowledgeImportService {

    /** 财税知识库默认名称 */
    public static final String FINANCE_KB_NAME = "财税知识库";

    /** 知识库分类: 政策 (财税规则统一归入 POLICY 库) */
    private static final String KB_CATEGORY_POLICY = "POLICY";

    /** 来源类型: 批量导入 */
    private static final String SOURCE_TYPE_IMPORT = "IMPORT";

    /** 内容类型: Markdown */
    private static final String CONTENT_TYPE_MARKDOWN = "MARKDOWN";

    /** 默认复核周期 (天): 政策类信息建议 ≤180 天 */
    private static final int DEFAULT_REVIEW_CYCLE_DAYS = 180;

    /** 审查状态: 待人工审核 (AI 起草/未核对, 未审核不入对外问答) */
    private static final String REVIEW_STATUS_PENDING = "待人工审核";

    /** 审查状态: 已审核 */
    private static final String REVIEW_STATUS_APPROVED = "已审核";

    /** 状态: 草稿 */
    private static final String STATUS_DRAFT = "草稿";

    /** AI 起草标记 (录入人) */
    private static final String CREATED_BY_AI = "AI 起草";

    /** AI 知识库数据访问层 (库自动创建/复用) */
    private final ScrmAiKnowledgeBaseRepository knowledgeBaseRepository;

    /** AI 知识库文档数据访问层 (导入落库/幂等去重) */
    private final ScrmAiKnowledgeDocumentRepository documentRepository;

    /** 模块③ 财税知识库配置 (库名 / 默认复核周期 / 单次导入上限) */
    private final ScrmFinanceKnowledgeProperties properties;

    /**
     * 批量导入 T06 模板文件（md/docx）。
     * <p>
     * 逐文件解析 + 校验, 单文件失败仅记录错误不阻断整批; 成功条目落库为
     * AiKnowledgeDocument（sourceType=IMPORT）。返回每条结果
     * [{fileName, success, message, articleCode, title, documentId}]。
     * </p>
     *
     * @param files 模板文件列表 (md / docx)
     * @return 导入结果列表
     */
    @Transactional
    public List<Map<String, Object>> importTemplateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw ScrmException.badRequest("导入文件列表不能为空");
        }
        int maxFiles = properties.getImportMaxFiles() > 0 ? properties.getImportMaxFiles() : 100;
        if (files.size() > maxFiles) {
            throw ScrmException.badRequest("单次导入文件数超过上限: " + maxFiles);
        }
        ScrmAiKnowledgeBaseEntity kb = ensureFinanceKnowledgeBase();
        List<Map<String, Object>> results = new ArrayList<>(files.size());
        int success = 0;
        for (MultipartFile file : files) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileName", file.getOriginalFilename());
            try {
                if (file == null || file.isEmpty()) {
                    throw ScrmException.badRequest("文件为空");
                }
                String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
                KnowledgeTemplateEntry entry = parseFile(fileName, file);
                ScrmAiKnowledgeDocumentEntity entity = saveEntry(kb, entry, fileName);
                result.put("success", true);
                result.put("message", "导入成功");
                result.put("articleCode", entry.getArticleCode());
                result.put("title", entry.getTitle());
                result.put("documentId", entity.getId());
                success++;
            } catch (Exception e) {
                log.warn("T06 模板导入单条失败: file={}, err={}",
                        file != null ? file.getOriginalFilename() : null, e.getMessage());
                result.put("success", false);
                result.put("message", e instanceof ScrmException se
                        ? se.getMessage() : "导入失败: " + e.getMessage());
            }
            results.add(result);
        }
        log.info("T06 模板批量导入完成: total={}, success={}, kbId={}",
                files.size(), success, kb.getId());
        return results;
    }

    /**
     * 按扩展名选择解析器并解析模板文件。
     *
     * @param fileName 文件名
     * @param file     文件内容
     * @return 解析后的条目
     * @throws ScrmException 不支持的文件类型 / 解析失败
     */
    private KnowledgeTemplateEntry parseFile(String fileName, MultipartFile file) throws ScrmException {
        String lower = fileName.toLowerCase();
        try {
            if (lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt")) {
                return KnowledgeTemplateParser.parse(fileName, file.getInputStream());
            }
            if (lower.endsWith(".docx")) {
                return KnowledgeDocxParser.parse(fileName, file.getInputStream());
            }
            throw ScrmException.badRequest("不支持的文件类型, 仅支持 .md / .docx: " + fileName);
        } catch (java.io.IOException e) {
            throw ScrmException.badRequest("读取模板文件失败: " + fileName + ", err=" + e.getMessage());
        }
    }

    /**
     * 校验条目并落库为 AiKnowledgeDocument。
     * <p>
     * 幂等: 同标题文档已存在则跳过（返回已存在标记）; 已审核 + 已发布（状态已发布）
     * 条目直接落库为已发布可对外, 其余落库为草稿 + 待人工审核。
     * </p>
     *
     * @param kb       目标知识库
     * @param entry    解析条目
     * @param fileName 文件名 (错误提示用)
     * @return 创建的文档
     * @throws ScrmException 标题/规则编号缺失
     */
    private ScrmAiKnowledgeDocumentEntity saveEntry(ScrmAiKnowledgeBaseEntity kb,
                                                    KnowledgeTemplateEntry entry, String fileName)
            throws ScrmException {
        if (entry == null || entry.getTitle() == null || entry.getTitle().isBlank()) {
            throw ScrmException.badRequest("模板缺少标题: " + fileName);
        }
        if (entry.getArticleCode() == null || entry.getArticleCode().isBlank()) {
            throw ScrmException.badRequest("模板缺少规则编号: " + fileName);
        }
        // 幂等去重: 同标题已存在则跳过
        if (documentRepository.existsByTitle(entry.getTitle().trim())) {
            log.info("T06 导入跳过重复标题: title={}, code={}", entry.getTitle(), entry.getArticleCode());
            ScrmAiKnowledgeDocumentEntity existing = new ScrmAiKnowledgeDocumentEntity();
            existing.setId(-1L);
            existing.setTitle(entry.getTitle());
            return existing;
        }

        ScrmAiKnowledgeDocumentEntity entity = new ScrmAiKnowledgeDocumentEntity();
        entity.setKnowledgeBaseId(kb.getId());
        entity.setTitle(entry.getTitle().trim());
        entity.setContent(buildContent(entry));
        entity.setContentType(CONTENT_TYPE_MARKDOWN);
        entity.setTags(buildTags(entry));
        entity.setSourceType(SOURCE_TYPE_IMPORT);
        entity.setSourceUrl(null);
        entity.setQaPairs(buildQaPairs(entry));
        entity.setEnabled(Boolean.TRUE);
        entity.setViewCount(0);
        entity.setCreatedBy(buildCreatedBy(entry));
        // 版本复核日期
        LocalDateTime lastReviewed = entry.getLastReviewedDate() != null
                ? entry.getLastReviewedDate().atStartOfDay() : null;
        entity.setLastReviewedAt(lastReviewed);
        int cycleDays = properties.getReviewCycleDays() > 0
                ? properties.getReviewCycleDays() : DEFAULT_REVIEW_CYCLE_DAYS;
        entity.setReviewCycleDays(cycleDays);
        entity.setNextReviewAt(computeNextReviewAt(lastReviewed, cycleDays));
        entity = documentRepository.save(entity);
        // 同步知识库文档数
        kb.setDocumentCount((kb.getDocumentCount() != null ? kb.getDocumentCount() : 0) + 1);
        knowledgeBaseRepository.save(kb);
        return entity;
    }

    /**
     * 确保财税知识库存在（按名称查找, 不存在自动创建）。
     *
     * @return 财税知识库
     */
    private ScrmAiKnowledgeBaseEntity ensureFinanceKnowledgeBase() {
        String kbName = properties.getKbName() != null && !properties.getKbName().isBlank()
                ? properties.getKbName() : FINANCE_KB_NAME;
        return knowledgeBaseRepository.findByKbName(kbName)
                .orElseGet(() -> {
                    ScrmAiKnowledgeBaseEntity kb = new ScrmAiKnowledgeBaseEntity();
                    kb.setKbName(kbName);
                    kb.setDescription("财税业务规则知识库 (T06 模板批量导入, sourceType=IMPORT)");
                    kb.setCategory(KB_CATEGORY_POLICY);
                    kb.setDocumentCount(0);
                    kb.setEnabled(Boolean.TRUE);
                    kb.setCreatedBy("finance-tax-v11");
                    return knowledgeBaseRepository.save(kb);
                });
    }

    /**
     * 渲染条目正文 (Markdown): 元信息 + 业务字段 + 附注。
     *
     * @param entry 解析条目
     * @return 正文 Markdown
     */
    private String buildContent(KnowledgeTemplateEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("> 财税业务规则 · ").append(entry.getArticleCode()).append('\n');
        sb.append('\n');
        sb.append("## 元信息\n");
        sb.append("- 分类：").append(entry.getCategory() == null ? "—" : entry.getCategory()).append('\n');
        sb.append("- 规则编号：").append(entry.getArticleCode()).append('\n');
        sb.append("- 适用地区：").append(entry.getApplicableRegion() == null ? "—" : entry.getApplicableRegion()).append('\n');
        sb.append("- 适用对象：").append(entry.getApplicableObject() == null ? "—" : entry.getApplicableObject()).append('\n');
        sb.append("- 来源：IMPORT").append('\n');
        sb.append("- 状态：").append(entry.getStatus() == null ? STATUS_DRAFT : entry.getStatus()).append('\n');
        sb.append("- 审查状态：").append(entry.getReviewStatus() == null ? REVIEW_STATUS_PENDING : entry.getReviewStatus()).append('\n');
        if (entry.getReviewer() != null && !entry.getReviewer().isBlank()) {
            sb.append("- 审核人：").append(entry.getReviewer()).append('\n');
        }
        if (entry.getLastReviewedDate() != null) {
            sb.append("- 最后人工复核日期：").append(entry.getLastReviewedDate()).append('\n');
        }
        sb.append("- 版本号：").append(entry.getVersionNumber() == null ? 1 : entry.getVersionNumber()).append('\n');
        if (entry.getChangeLog() != null && !entry.getChangeLog().isBlank()) {
            sb.append("- 变更说明：").append(entry.getChangeLog()).append('\n');
        }
        sb.append("- 录入人：").append(entry.getCreatedBy() == null ? CREATED_BY_AI : entry.getCreatedBy()).append('\n');
        sb.append("- 录入日期：").append(entry.getCreatedDate() == null ? LocalDate.now() : entry.getCreatedDate()).append('\n');
        if (entry.getRelatedArticles() != null && !entry.getRelatedArticles().isBlank()) {
            sb.append("- 关联条目：").append(entry.getRelatedArticles()).append('\n');
        }
        if (entry.getQaPairs() != null && !entry.getQaPairs().isEmpty()) {
            sb.append("- 问答对：\n");
            for (String qa : entry.getQaPairs()) {
                sb.append("  - ").append(qa).append('\n');
            }
        }
        sb.append('\n');
        sb.append("## 业务规则\n");
        Map<String, String> fields = entry.getBusinessFields();
        if (fields == null || fields.isEmpty()) {
            sb.append("（模板未填写业务字段, 待业务人员补充）\n");
        } else {
            for (Map.Entry<String, String> field : fields.entrySet()) {
                sb.append("### ").append(field.getKey()).append('\n');
                sb.append(field.getValue()).append('\n');
                sb.append('\n');
            }
        }
        if (entry.getNotes() != null && !entry.getNotes().isBlank()) {
            sb.append("## 附注\n");
            sb.append(entry.getNotes()).append('\n');
        }
        sb.append('\n');
        sb.append("> 仅供参考，以最新政策及主管机关确认为准。\n");
        return sb.toString();
    }

    /**
     * 构建标签: 分类 + 地区 + 适用对象。
     *
     * @param entry 解析条目
     * @return 逗号分隔标签
     */
    private String buildTags(KnowledgeTemplateEntry entry) {
        List<String> tags = new ArrayList<>();
        if (entry.getCategory() != null && !entry.getCategory().isBlank()) {
            tags.add(entry.getCategory());
        }
        if (entry.getApplicableRegion() != null && !entry.getApplicableRegion().isBlank()) {
            tags.add(entry.getApplicableRegion());
        }
        if (entry.getApplicableObject() != null && !entry.getApplicableObject().isBlank()) {
            tags.add(entry.getApplicableObject());
        }
        tags.add("T06");
        tags.add("IMPORT");
        return String.join(",", tags);
    }

    /**
     * 构建问答对 JSON（[{question, answer}]）。
     *
     * @param entry 解析条目
     * @return JSON 字符串, 无问答对返回 null
     */
    private String buildQaPairs(KnowledgeTemplateEntry entry) {
        if (entry.getQaPairs() == null || entry.getQaPairs().isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < entry.getQaPairs().size(); i++) {
            String qa = entry.getQaPairs().get(i);
            String[] parts = qa.split(" → ", 2);
            if (parts.length < 2) {
                continue;
            }
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append("{\"question\":").append(toJsonString(parts[0]))
                    .append(",\"answer\":").append(toJsonString(parts[1])).append('}');
        }
        sb.append(']');
        if (sb.length() <= 2) {
            return null;
        }
        return sb.toString();
    }

    /**
     * JSON 字符串转义 (简化, 不引第三方序列化依赖)。
     *
     * @param text 文本
     * @return JSON 字符串字面量
     */
    private String toJsonString(String text) {
        return "\"" + text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }

    /**
     * 计算建议下次复核日期。
     *
     * @param lastReviewedAt 最后复核日期
     * @param cycleDays      复核周期(天)
     * @return 下次复核日期, 无复核日期返回 null
     */
    static LocalDateTime computeNextReviewAt(LocalDateTime lastReviewedAt, int cycleDays) {
        if (lastReviewedAt == null) {
            return null;
        }
        int effective = cycleDays > 0 ? cycleDays : DEFAULT_REVIEW_CYCLE_DAYS;
        return lastReviewedAt.plusDays(effective);
    }

    /**
     * 构建录入人: 模板字段无则标 AI 起草。
     *
     * @param entry 解析条目
     * @return 录入人
     */
    private String buildCreatedBy(KnowledgeTemplateEntry entry) {
        if (entry.getCreatedBy() != null && !entry.getCreatedBy().isBlank()) {
            return entry.getCreatedBy();
        }
        return CREATED_BY_AI;
    }
}
