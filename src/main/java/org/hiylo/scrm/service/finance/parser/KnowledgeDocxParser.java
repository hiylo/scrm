/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : KnowledgeDocxParser.java
 * Date : 2026/10/23 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.service.finance.model.KnowledgeTemplateEntry;

import java.io.IOException;
import java.io.InputStream;

/**
 * T06 财税业务规则模板解析器 (Word .docx)。
 * <p>
 * 使用项目既有 Apache POI XWPF（pom 已声明 poi/poi-ooxml 5.3.0, 不改 pom）将
 * .docx 按段落抽取为 Markdown 文本后复用 {@link KnowledgeTemplateParser} 解析。
 * 段落级抽取策略:
 * <ul>
 *   <li>段落样式名以 {@code Heading} 开头 → {@code ## } 标题行;</li>
 *   <li>列表段（样式名含 {@code List} / 以 {@code 1.} {@code -} 开头）保留原前缀;
 *       普通段保留原文。</li>
 * </ul>
 * 抽取后交给 Markdown 解析器, 保持 md/docx 两条路径同一套字段映射与校验。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public final class KnowledgeDocxParser {

    private KnowledgeDocxParser() {
        // 工具类, 禁止实例化
    }

    /**
     * 解析 .docx 模板文件流。
     *
     * @param fileName    文件名 (规则编号提取兜底)
     * @param inputStream docx 文件流
     * @return 解析后的条目
     * @throws ScrmException 读取失败 / 非 docx / 解析失败
     */
    public static KnowledgeTemplateEntry parse(String fileName, InputStream inputStream) throws ScrmException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder markdown = new StringBuilder();
            boolean firstHeading = true;
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text == null || text.isBlank()) {
                    continue;
                }
                String style = paragraph.getStyle() == null ? "" : paragraph.getStyle();
                String trimmed = text.trim();
                if (style.startsWith("Heading") || trimmed.startsWith("#")) {
                    // 首个 Heading 作为一级标题 (#), 供 Markdown 解析器提取 title
                    if (firstHeading) {
                        markdown.append("# ").append(trimmed.replaceFirst("^#+\\s*", "")).append('\n');
                        firstHeading = false;
                    } else {
                        markdown.append("## ").append(trimmed.replaceFirst("^#+\\s*", "")).append('\n');
                    }
                } else if (style.contains("List") || trimmed.startsWith("1.") || trimmed.startsWith("- ")
                        || trimmed.matches("^\\d+\\.\\s.*")) {
                    markdown.append(trimmed).append('\n');
                } else {
                    markdown.append(trimmed).append('\n');
                }
            }
            return KnowledgeTemplateParser.parse(fileName, markdown.toString());
        } catch (IOException e) {
            throw ScrmException.badRequest("读取 docx 模板失败: " + fileName + ", err=" + e.getMessage());
        } catch (RuntimeException e) {
            throw ScrmException.badRequest("解析 docx 模板失败: " + fileName + ", err=" + e.getMessage());
        }
    }
}
