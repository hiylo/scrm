/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : KnowledgeTemplateParser.java
 * Date : 2026/10/23 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.parser;

import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.service.finance.model.KnowledgeTemplateEntry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T06 财税业务规则模板解析器 (Markdown 文本)。
 * <p>
 * 按 {@code docs/tasks/T06/knowledge-template.md} 规范解析单条规则: 文件名为
 * {@code <规则编号>-<标题关键词>.md}; 正文首行 {@code # <标题>}; 通用字段为行首
 * {@code - 字段名：值}; 业务字段从 {@code ## 分类} 段落到 {@code ## 附注} 前的
 * 行首 {@code - 字段名：值} / 编号列表行提取。字段名归一化后与原样值一并保留,
 * 供渲染正文与分类归属使用。
 * </p>
 * <p>
 * 解析策略 (对 AI 起草示例兼容):
 * <ul>
 *   <li>必填缺失不抛异常, 由导入服务按字段判「待人工审核」状态; 仅规则编号/标题缺失
 *       时抛 {@link ScrmException#badRequest} 打回该条。</li>
 *   <li>「最后人工复核日期：—（待审核）」等无实际日期值解析为 null。</li>
 *   <li>行内多个字段（如「状态：草稿　审查状态：待人工审核」）按全角空格/制表符切分
 *       后逐字段匹配。</li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public final class KnowledgeTemplateParser {

    /** 可被行内多字段分隔的分隔符 (全角空格 / 制表符; 半角空格保留在值内, 如「AI 起草」) */
    private static final String FIELD_SEPARATOR = "[　\\t]+";

    private KnowledgeTemplateParser() {
        // 工具类, 禁止实例化
    }

    /**
     * 解析单个 Markdown 模板文件内容。
     *
     * @param fileName 文件名 (用于提取规则编号, 可空)
     * @param markdown Markdown 文本
     * @return 解析后的条目
     * @throws ScrmException 标题/规则编号缺失或日期非法
     */
    public static KnowledgeTemplateEntry parse(String fileName, String markdown) throws ScrmException {
        if (markdown == null || markdown.isBlank()) {
            throw ScrmException.badRequest("模板内容为空: " + fileName);
        }
        List<String> lines = new ArrayList<>(List.of(markdown.split("\\r?\\n")));
        KnowledgeTemplateEntry entry = new KnowledgeTemplateEntry();
        entry.setBusinessFields(new LinkedHashMap<>());

        // 1. 标题: 首个 # 行
        String title = extractTitle(lines);
        if (title == null || title.isBlank()) {
            throw ScrmException.badRequest("模板缺少标题 (# 首行): " + fileName);
        }
        entry.setTitle(title.trim());

        // 2. 通用字段: 行首 "- 字段：值"（含行内多字段）
        List<String> businessSectionMarkers = List.of(
                "办理条件", "所需材料清单", "办理流程", "办理时限",
                "常见驳回", "特殊行业提示", "政策依据", "注意事项",
                "注销类型", "适用条件", "税务清算提示", "资质名称",
                "主管部门", "申办条件", "年审", "纳税人类型",
                "规则要点", "规则要点/适用场景", "申报/办理期限", "所需资料",
                "办理条件/口径", "常见错误", "附注");
        boolean inBusinessSection = false;
        String currentSection = null;
        String lastNamedField = null;
        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.isBlank() || line.startsWith(">")) {
                continue;
            }
            String heading = extractHeading(line);
            if (heading != null) {
                inBusinessSection = businessSectionMarkers.contains(heading);
                currentSection = heading;
                lastNamedField = null;
                if ("附注".equals(heading)) {
                    inBusinessSection = false;
                    currentSection = null;
                }
                continue;
            }
            if (inBusinessSection && currentSection != null) {
                if (line.startsWith("- ")) {
                    String fieldLine = line.substring(2).trim();
                    if (fieldLine.isBlank()) {
                        continue;
                    }
                    int idx = indexOfFieldSeparator(fieldLine);
                    if (idx > 0 && !fieldLine.substring(idx + 1).trim().isEmpty()) {
                        String name = normalizeFieldName(currentSection, fieldLine.substring(0, idx).trim());
                        String value = fieldLine.substring(idx + 1).trim();
                        lastNamedField = name;
                        entry.getBusinessFields().merge(name, value, (a, b) -> a + "\n" + b);
                    } else {
                        // 业务段无冒号列表行: 追加到当前命名字段或按段名归集
                        String target = lastNamedField != null
                                ? lastNamedField : normalizeFieldName(currentSection, currentSection);
                        entry.getBusinessFields().merge(target, "· " + fieldLine, (a, b) -> a + "\n" + b);
                    }
                } else if (line.matches("^\\d+\\.\\s.*")) {
                    String item = line.substring(line.indexOf('.') + 1).trim();
                    if (!item.isEmpty()) {
                        String target = lastNamedField != null
                                ? lastNamedField : normalizeFieldName(currentSection, currentSection);
                        entry.getBusinessFields().merge(target, "· " + item, (a, b) -> a + "\n" + b);
                    }
                } else {
                    // 业务段普通段落: 追加到当前命名字段或按段名归集
                    String target = lastNamedField != null
                            ? lastNamedField : normalizeFieldName(currentSection, currentSection);
                    entry.getBusinessFields().merge(target, line, (a, b) -> a + "\n" + b);
                }
                continue;
            }
            if (!line.startsWith("- ")) {
                continue;
            }
            String fieldLine = line.substring(2).trim();
            if (fieldLine.isBlank()) {
                continue;
            }
            // 问答对行由 extractQaPairs 处理, 不作为通用字段
            if (fieldLine.startsWith("问：") || fieldLine.startsWith("问:")
                    || fieldLine.startsWith("答：") || fieldLine.startsWith("答:")
                    || fieldLine.startsWith("问答对")) {
                continue;
            }
            // 通用字段段: 行内可能多字段（全角空格分隔）
            for (String part : fieldLine.split(FIELD_SEPARATOR)) {
                int idx = indexOfFieldSeparator(part);
                if (idx <= 0) {
                    continue;
                }
                String name = part.substring(0, idx).trim();
                String value = part.substring(idx + 1).trim();
                if (value.isEmpty()) {
                    continue;
                }
                applyCommonField(entry, name, value, fileName);
            }
        }

        // 3. 问答对: 附注前的 "问：… / 答：…" 行（独立行）
        entry.setQaPairs(extractQaPairs(lines));

        // 4. 附注: ## 附注 段
        entry.setNotes(extractNotes(lines));

        // 5. 附注: ## 附注 段
        entry.setNotes(extractNotes(lines));

        // 6. 规则编号兜底: 文件名 <R-XXX-0000>-
        if (entry.getArticleCode() == null || entry.getArticleCode().isBlank()) {
            entry.setArticleCode(extractCodeFromFileName(fileName));
        }
        if (entry.getArticleCode() == null || entry.getArticleCode().isBlank()) {
            throw ScrmException.badRequest("模板缺少规则编号: " + fileName);
        }
        return entry;
    }

    /**
     * 解析单条 Markdown 文件流。
     *
     * @param fileName    文件名
     * @param inputStream 文件流
     * @return 解析后的条目
     * @throws ScrmException 读取失败 / 解析失败
     */
    public static KnowledgeTemplateEntry parse(String fileName, InputStream inputStream) throws ScrmException {
        try {
            String markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return parse(fileName, markdown);
        } catch (IOException e) {
            throw ScrmException.badRequest("读取模板文件失败: " + fileName + ", err=" + e.getMessage());
        }
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 提取标题: 首个 {@code # } 行。
     *
     * @param lines 行列表
     * @return 标题内容, 无则 null
     */
    private static String extractTitle(List<String> lines) {
        for (String raw : lines) {
            String line = raw.strip();
            if (line.startsWith("# ") && !line.startsWith("## ")) {
                return line.substring(2).trim();
            }
        }
        return null;
    }

    /**
     * 提取标题行 (h2/h3)。
     *
     * @param line 行内容
     * @return 规范化后的标题（去掉前缀 #）, 非标题行返回 null
     */
    private static String extractHeading(String line) {
        if (line.startsWith("## ")) {
            return line.substring(3).trim();
        }
        if (line.startsWith("### ")) {
            return line.substring(4).trim();
        }
        return null;
    }

    /**
     * 定位字段分隔符 (全角冒号优先, 其次半角冒号)。
     *
     * @param text 文本
     * @return 分隔符下标, 无则 -1
     */
    private static int indexOfFieldSeparator(String text) {
        int fullWidth = text.indexOf('：');
        if (fullWidth >= 0) {
            return fullWidth;
        }
        int halfWidth = text.indexOf(':');
        if (halfWidth > 0) {
            return halfWidth;
        }
        return -1;
    }

    /**
     * 通用字段赋值。
     *
     * @param entry    条目
     * @param name     字段名 (原文)
     * @param value    字段值
     * @param fileName 文件名 (错误提示用)
     * @throws ScrmException 日期非法
     */
    private static void applyCommonField(KnowledgeTemplateEntry entry, String name, String value, String fileName)
            throws ScrmException {
        switch (name) {
            case "分类" -> entry.setCategory(stripCode(value));
            case "标题" -> {
                if (entry.getTitle() == null || entry.getTitle().isBlank()) {
                    entry.setTitle(value);
                }
            }
            case "规则编号" -> entry.setArticleCode(stripCode(value));
            case "适用地区" -> entry.setApplicableRegion(value);
            case "适用对象" -> entry.setApplicableObject(value);
            case "来源" -> entry.setSourceType(stripCode(value));
            case "状态" -> entry.setStatus(value);
            case "审查状态" -> entry.setReviewStatus(stripCode(value));
            case "审核人" -> entry.setReviewer(value);
            case "最后人工复核日期" -> entry.setLastReviewedDate(parseDateOrNull(value, name, fileName));
            case "版本号" -> {
                Integer version = parseIntegerOrNull(value);
                if (version != null) {
                    entry.setVersionNumber(version);
                }
            }
            case "变更说明" -> entry.setChangeLog(value);
            case "录入人" -> entry.setCreatedBy(value);
            case "录入日期" -> entry.setCreatedDate(parseDateOrNull(value, name, fileName));
            case "关联条目" -> entry.setRelatedArticles(stripCodeList(value));
            default -> {
                // 业务字段兜底（未进入业务段时）, 归入 businessFields
                entry.getBusinessFields().merge(normalizeFieldName(name, name), value, (a, b) -> a + "\n" + b);
            }
        }
    }

    /**
     * 提取问答对 (问/答 相邻行)。
     *
     * @param lines 行列表
     * @return 问答对列表
     */
    private static List<String> extractQaPairs(List<String> lines) {
        List<String> qaPairs = new ArrayList<>();
        String pendingQuestion = null;
        for (String raw : lines) {
            String line = raw.strip();
            if (line.startsWith("- 问：") || line.startsWith("- 问:")) {
                pendingQuestion = line.substring(line.indexOf('：') >= 0
                        ? line.indexOf('：') + 1 : line.indexOf(':') + 1).trim();
            } else if (line.startsWith("- 答：") || line.startsWith("- 答:")) {
                int idx = line.indexOf('：') >= 0 ? line.indexOf('：') : line.indexOf(':');
                String answer = idx >= 0 ? line.substring(idx + 1).trim() : "";
                if (pendingQuestion != null && !pendingQuestion.isEmpty()) {
                    qaPairs.add(pendingQuestion + " → " + answer);
                    pendingQuestion = null;
                }
            } else if (line.startsWith("问：") || line.startsWith("问:")) {
                pendingQuestion = line.substring(line.indexOf('：') >= 0
                        ? line.indexOf('：') + 1 : line.indexOf(':') + 1).trim();
            } else if (line.startsWith("答：") || line.startsWith("答:")) {
                int idx = line.indexOf('：') >= 0 ? line.indexOf('：') : line.indexOf(':');
                String answer = idx >= 0 ? line.substring(idx + 1).trim() : "";
                if (pendingQuestion != null && !pendingQuestion.isEmpty()) {
                    qaPairs.add(pendingQuestion + " → " + answer);
                    pendingQuestion = null;
                }
            }
        }
        return qaPairs;
    }

    /**
     * 提取附注段内容（真实性与待确认事项行）。
     *
     * @param lines 行列表
     * @return 附注内容, 无则 null
     */
    private static String extractNotes(List<String> lines) {
        boolean inNotes = false;
        StringBuilder sb = new StringBuilder();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.startsWith("## 附注")) {
                inNotes = true;
                continue;
            }
            if (inNotes && line.startsWith("## ")) {
                break;
            }
            if (inNotes && !line.isBlank()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 业务字段名归一化: 去掉「（步骤）」等括号后缀。
     *
     * @param section 当前段标题
     * @param name    字段原文
     * @return 归一化字段名
     */
    private static String normalizeFieldName(String section, String name) {
        String normalized = name;
        int parenIdx = normalized.indexOf('（');
        if (parenIdx > 0) {
            normalized = normalized.substring(0, parenIdx).trim();
        }
        if (normalized.isBlank() && section != null) {
            normalized = section;
        }
        return normalized;
    }

    /**
     * 从文件名提取规则编号 ({@code R-XXX-0000-…})。
     *
     * @param fileName 文件名
     * @return 规则编号, 无则 null
     */
    private static String extractCodeFromFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("R-[A-Z]+-\\d{4,}")
                .matcher(fileName);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * 去除值中的反引号与首尾空白, 并截断括号说明（如「MANUAL（AI 起草）」→ MANUAL）。
     *
     * @param value 字段值
     * @return 清理后的值
     */
    private static String stripCode(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace("`", "").trim();
        int parenIdx = cleaned.indexOf('（');
        if (parenIdx > 0) {
            cleaned = cleaned.substring(0, parenIdx).trim();
        }
        return cleaned;
    }

    /**
     * 去除关联条目中的反引号。
     *
     * @param value 字段值
     * @return 清理后的值
     */
    private static String stripCodeList(String value) {
        return value == null ? null : value.replace("`", "");
    }

    /**
     * 解析日期 (YYYY-MM-DD), 「—」或非法值返回 null。
     *
     * @param value    字段值
     * @param field    字段名 (错误提示用)
     * @param fileName 文件名 (错误提示用)
     * @return 日期, 无法解析返回 null
     * @throws ScrmException 值非空且非「—」且日期格式非法
     */
    private static LocalDate parseDateOrNull(String value, String field, String fileName) throws ScrmException {
        String cleaned = value == null ? null : value.trim();
        if (cleaned == null || cleaned.isEmpty() || cleaned.equals("—") || cleaned.startsWith("—")) {
            return null;
        }
        try {
            return LocalDate.parse(cleaned);
        } catch (DateTimeParseException e) {
            throw ScrmException.badRequest("模板字段日期格式非法: " + fileName + " / " + field + "=" + value);
        }
    }

    /**
     * 解析整数, 非法返回 null。
     *
     * @param value 字段值
     * @return 整数, 无法解析返回 null
     */
    private static Integer parseIntegerOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim().replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
