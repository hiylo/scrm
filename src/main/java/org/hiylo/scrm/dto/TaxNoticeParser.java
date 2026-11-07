/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : TaxNoticeParser.java
 * Date : 2026/10/23 09:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 税务公开公告 HTML/JSON 解析器（省级适配器公共工具）。
 * <p>
 * 按 T03 实证结论实现两类解析:
 * <ul>
 *   <li><b>静态公告列表</b>: 普通 HTML 列表页（广东/浙江主站通知公告、浙江公布栏）,
 *       直接按通用选择器取 标题/日期/详情链接;</li>
 *   <li><b>查询应用 AJAX</b>: 表单返回的 JSON/页面片段, 按字段别名解析
 *       企业名称/识别号/法人/欠税金额/所属期/公告日期等。</li>
 * </ul>
 * 解析器只做「结构到模型」的转换, 网络请求与频率控制在抓取执行方; 产出统一
 * {@link TaxNoticeRecord} 并计算明细唯一键（识别号 + 公告类型 + 所属期起 [+ 税种]）。
 * 各省级站点字段命名不一, 解析时按别名兜底, 解析失败只记警告不中断本轮。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Component
@RequiredArgsConstructor
public class TaxNoticeParser {

    /**
     * 日期格式: yyyy-MM-dd
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 可接受的公告年份下限（过滤掉误匹配的数字串） */
    private static final int MIN_YEAR = 2000;

    /** 可接受的公告年份上限 */
    private static final int MAX_YEAR = 2099;

    /** 欠税金额数字提取（去掉 ￥/,/空白后保留数字与小数点） */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("[0-9]+(\\.[0-9]+)?");

    /** 常见导航/栏目文案（真实站点里同样是 <li>+<a>, 需排除） */
    private static final java.util.Set<String> NAVIGATION_TEXTS = java.util.Set.of(
            "首页", "简体", "繁体", "无障碍浏览", "信息公开", "新闻动态", "政策文件", "纳税服务",
            "互动交流", "税收宣传", "机构职能", "领导简介", "通知公告", "人事管理", "政府采购",
            "政府信息公开", "政策法规", "办税指南", "更多", "上一页", "下一页", "尾页",
            "页面放大", "页面缩小", "页面配色", "辅助线", "显示屏放大", "语音模式");

    /** JSON 解析器 */
    private final ObjectMapper objectMapper;

    /**
     * 解析静态 HTML 公告列表片段。
     * <p>适用于广东/浙江主站「通知公告」列表: 每行含 标题 + 日期 + 详情链接。
     * 页面结构各站略有差异, 这里按常见行/链接/日期节点组合提取, 提取不到的行跳过。</p>
     *
     * @param html            列表页 HTML
     * @param noticeType      公告类型（如 通知公告）
     * @param province        省份代码（GD/ZJ）
     * @param detailTitleAttr 详情链接标题属性名（如 title/text, 可空）
     * @return 公告记录列表
     */
    public List<TaxNoticeRecord> parseStaticList(String html, String noticeType, String province,
                                                 String detailTitleAttr) {
        List<TaxNoticeRecord> records = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return records;
        }
        // 真实页面里导航项同样是 <li>+<a>, 全页扫描会把「首页/信息公开」当成公告。
        // 因此先按站点特征收缩到真正的列表容器, 再用「日期有效」做最终判别。
        for (String block : extractListBlocks(html)) {
            String date = extractDate(block);
            String title = cleanTitle(extractTitle(block, detailTitleAttr));
            if (date == null || title == null || isNavigationText(title)) {
                continue;
            }
            TaxNoticeRecord record = new TaxNoticeRecord();
            record.setProvince(province);
            record.setNoticeType(noticeType);
            record.setNoticeDate(date);
            record.setSourceUrl(extractHref(block));
            record.setEnterpriseName(title.trim());
            records.add(record);
        }
        return records;
    }

    /**
     * 按站点结构提取候选列表项片段。
     * <p>
     * 优先识别已知容器：广东 {@code id="ajaxlist"}、浙江 datastore 的
     * {@code <record><![CDATA[..]]></record>}；都识别不到时退回全页 {@code <li>} 扫描，
     * 由调用方用「日期有效」过滤掉导航项。
     * </p>
     *
     * @param html 页面 HTML
     * @return 候选列表项 HTML 片段
     */
    private List<String> extractListBlocks(String html) {
        List<String> blocks = new ArrayList<>();
        // 1) 广东: <div id="ajaxlist"> 容器内的 <li>
        int ajaxIdx = html.indexOf("id=\"ajaxlist\"");
        if (ajaxIdx >= 0) {
            int end = html.indexOf("</div>", ajaxIdx);
            String scope = end > ajaxIdx ? html.substring(ajaxIdx, end) : html.substring(ajaxIdx);
            Matcher matcher = Pattern.compile("(?is)<li\\b[^>]*>(.*?)</li>").matcher(scope);
            while (matcher.find()) {
                blocks.add(matcher.group(1));
            }
            if (!blocks.isEmpty()) {
                return blocks;
            }
        }
        // 2) 浙江等 datastore 站点: <record><![CDATA[ <li>..</li> ]]></record>
        Matcher recordMatcher = Pattern.compile(
                "(?is)<record>\\s*<!\\[CDATA\\[(.*?)\\]\\]>\\s*</record>").matcher(html);
        while (recordMatcher.find()) {
            blocks.add(recordMatcher.group(1));
        }
        if (!blocks.isEmpty()) {
            return blocks;
        }
        // 3) 兜底: 全页 <li> 扫描（由调用方的日期判定过滤导航）
        Matcher matcher = Pattern.compile("(?is)<li\\b[^>]*>(.*?)</li>").matcher(html);
        while (matcher.find()) {
            blocks.add(matcher.group(1));
        }
        return blocks;
    }

    /**
     * 清洗标题：去掉 HTML 注释与首尾空白，并过滤「只剩标点」的占位符标题。
     * <p>
     * 部分站点的栏目模板会把标题渲染成 {@code <!--articlequotetitle--><!--articletitle-->——}
     * 这类占位符（浙江 A5 重大税收违法栏目实测如此）。这类标题没有信息量，
     * 若不过滤会把无意义记录写进巡检快照并触发误告警。
     * </p>
     *
     * @param raw 原始标题
     * @return 清洗后的标题（不可用时返回 null）
     */
    private String cleanTitle(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.replaceAll("(?s)<!--.*?-->", "").trim();
        if (t.isEmpty()) {
            return null;
        }
        // 去掉标点/空白后若无实质字符, 视为占位符
        String meaningful = t.replaceAll("[\\p{Punct}\\s—－·、，。；：！？（）()\\[\\]【】《》]", "");
        if (meaningful.length() < 2) {
            return null;
        }
        return t;
    }

    /**
     * 判断文本是否为导航/栏目名（避免把「首页」「信息公开」当成公告标题）。
     *
     * @param text 标题文本
     * @return true 表示导航项
     */
    private boolean isNavigationText(String text) {
        String t = text.trim();
        if (t.length() > 40) {
            return false;
        }
        return NAVIGATION_TEXTS.contains(t);
    }

    /**
     * 解析查询应用返回的 JSON（广东/浙江 B 类查询应用的 AJAX 分页结果）。
     *
     * @param json       查询返回 JSON 字符串
     * @param noticeType 公告类型（欠税 / 非正常户 / 重大税收违法）
     * @param province   省份代码
     * @param sourceUrl  来源 URL（可空）
     * @return 公告记录列表
     */
    public List<TaxNoticeRecord> parseQueryJson(String json, String noticeType, String province,
                                                String sourceUrl) {
        List<TaxNoticeRecord> records = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return records;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode rows = firstNode(root, "data", "rows", "list", "result", "items");
            if (rows == null || !rows.isArray()) {
                return records;
            }
            for (JsonNode row : rows) {
                records.add(parseQueryRow(row, noticeType, province, sourceUrl));
            }
        } catch (Exception e) {
            // 解析失败返回空记录, 由上层记录错误; 不抛断整轮巡检
            return records;
        }
        return records;
    }

    /**
     * 解析查询应用返回的页面片段（HTML 表格行）。
     *
     * @param html       页面片段 HTML
     * @param noticeType 公告类型
     * @param province   省份代码
     * @param sourceUrl  来源 URL（可空）
     * @return 公告记录列表
     */
    public List<TaxNoticeRecord> parseQueryHtml(String html, String noticeType, String province,
                                                String sourceUrl) {
        List<TaxNoticeRecord> records = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return records;
        }
        for (String rowHtml : html.split("(?i)<tr\\b")) {
            TaxNoticeRecord record = parseQueryRowHtml(rowHtml, noticeType, province, sourceUrl);
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    // ============================================================
    // 内部解析辅助
    // ============================================================

    /**
     * 从 JSON 行节点解析一条公告记录。
     *
     * @param row        行节点
     * @param noticeType 公告类型
     * @param province   省份代码
     * @param sourceUrl  来源 URL
     * @return 公告记录
     */
    private TaxNoticeRecord parseQueryRow(JsonNode row, String noticeType, String province, String sourceUrl) {
        TaxNoticeRecord record = new TaxNoticeRecord();
        record.setProvince(province);
        record.setNoticeType(noticeType);
        record.setCreditCode(firstText(row, "nsrsbh", "creditCode", "taxpayerCode", "unifiedCode", "nsrsbm"));
        record.setEnterpriseName(firstText(row, "nsrmc", "enterpriseName", "taxpayerName", "name", "companyName"));
        record.setLegalPerson(firstText(row, "fddbrxm", "legalPerson", "fddbr", "legalRepName"));
        record.setTaxPeriodStart(firstText(row, "qssqq", "taxPeriodStart", "periodStart", "ssqq"));
        record.setTaxPeriodEnd(firstText(row, "qssqz", "taxPeriodEnd", "periodEnd", "ssqz"));
        record.setTaxCategory(firstText(row, "nsse", "taxCategory", "taxType", "szmc", "ssmc"));
        record.setTaxAmount(firstText(row, "qsje", "taxAmount", "amount", "qsse"));
        record.setNoticeDate(firstText(row, "ggrq", "noticeDate", "publishDate", "ggrq1"));
        record.setNoticeAuthority(firstText(row, "ggjg", "noticeAuthority", "authority", "swjgmc"));
        record.setSourceUrl(sourceUrl);
        record.setUniqueKey(buildKey(record));
        return record;
    }

    /**
     * 从 HTML 表格行片段解析一条公告记录（按 <td> 文本 + <a>/<span> 标题）。
     *
     * @param rowHtml    行 HTML 片段
     * @param noticeType 公告类型
     * @param province   省份代码
     * @param sourceUrl  来源 URL
     * @return 公告记录（关键字段缺失时返回 null）
     */
    private TaxNoticeRecord parseQueryRowHtml(String rowHtml, String noticeType, String province,
                                              String sourceUrl) {
        TaxNoticeRecord record = new TaxNoticeRecord();
        record.setProvince(province);
        record.setNoticeType(noticeType);
        record.setSourceUrl(sourceUrl);
        // 从单元格文本中按已知列序启发式提取（各站列序不同, 具体字段名在联调时按实测调整）
        List<String> cells = extractCellTexts(rowHtml);
        if (cells.size() >= 2) {
            record.setEnterpriseName(cells.get(0));
            record.setCreditCode(findCreditCode(cells));
        }
        if (cells.size() >= 5) {
            record.setLegalPerson(findLegalPerson(cells));
            record.setTaxAmount(findAmount(cells));
            record.setNoticeDate(extractDate(rowHtml));
        }
        if (record.getEnterpriseName() == null || record.getEnterpriseName().isBlank()) {
            return null;
        }
        record.setUniqueKey(buildKey(record));
        return record;
    }

    /**
     * 提取 <td> 文本列表（按 {@code <td...>内容</td>} 逐格匹配, 避免 <tr> 前缀残留）。
     *
     * @param rowHtml 行片段
     * @return 单元格文本列表
     */
    private List<String> extractCellTexts(String rowHtml) {
        List<String> cells = new ArrayList<>();
        Pattern tdPattern = Pattern.compile("(?is)<td\\b[^>]*>(.*?)</td>");
        Matcher matcher = tdPattern.matcher(rowHtml);
        while (matcher.find()) {
            String text = stripTags(matcher.group(1));
            if (text != null && !text.isBlank()) {
                cells.add(text.trim());
            }
        }
        return cells;
    }

    /**
     * 从单元格列表中找统一社会信用代码（18 位字母数字）。
     *
     * @param cells 单元格文本列表
     * @return 信用代码（未命中返回 null）
     */
    private String findCreditCode(List<String> cells) {
        Pattern codePattern = Pattern.compile("(?i)([0-9A-Z]{18})");
        for (String cell : cells) {
            Matcher matcher = codePattern.matcher(cell.replaceAll("\\s", ""));
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * 从单元格列表找法定代表人（常见中文姓名, 2-4 字）。
     *
     * @param cells 单元格文本列表
     * @return 法人姓名（未命中返回 null）
     */
    private String findLegalPerson(List<String> cells) {
        Pattern namePattern = Pattern.compile("^[\\u4e00-\\u9fa5]{2,4}$");
        for (String cell : cells) {
            if (namePattern.matcher(cell).matches()) {
                return cell;
            }
        }
        return null;
    }

    /**
     * 从单元格列表找金额（含数字与小数点的单元格, 排除日期/所属期列）。
     *
     * @param cells 单元格文本列表
     * @return 金额（未命中返回 null）
     */
    private String findAmount(List<String> cells) {
        for (String cell : cells) {
            if (cell.contains("日期") || cell.contains("所属") || cell.contains("年")) {
                continue;
            }
            String cleaned = cell.replaceAll("[￥¥,，\\s]", "");
            if (cleaned.isEmpty()) {
                continue;
            }
            Matcher matcher = AMOUNT_PATTERN.matcher(cleaned);
            if (matcher.matches()) {
                return cleaned;
            }
        }
        return null;
    }

    /**
     * 构建明细唯一键: 识别号 + 公告类型 + 所属期起（欠税叠加税种）。
     *
     * @param record 公告记录
     * @return 唯一键
     */
    private String buildKey(TaxNoticeRecord record) {
        StringBuilder key = new StringBuilder();
        key.append(record.getCreditCode() == null ? "" : record.getCreditCode()).append('|')
                .append(record.getNoticeType()).append('|')
                .append(record.getTaxPeriodStart() == null ? "" : record.getTaxPeriodStart());
        if ("欠税".equals(record.getNoticeType()) && record.getTaxCategory() != null) {
            key.append('|').append(record.getTaxCategory());
        }
        return key.toString();
    }

    /**
     * 从行片段提取 href（详情链接）。
     *
     * @param line 行片段
     * @return href 值（可空）
     */
    private String extractHref(String line) {
        Pattern pattern = Pattern.compile("(?i)href\\s*=\\s*['\"]([^'\"]+)['\"]");
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 从行片段提取标题文本。
     *
     * @param line           行片段
     * @param detailTitleAttr 标题属性名（title/text, 可空, 默认 text）
     * @return 标题（可空）
     */
    private String extractTitle(String line, String detailTitleAttr) {
        // 真实站点普遍把完整标题放在 <a title="..."> 上（正文可能被截断或含省略号）
        Pattern attrPattern = Pattern.compile("(?i)<a[^>]*title\\s*=\\s*[\"']([^\"']+)[\"']");
        Matcher attrMatcher = attrPattern.matcher(line);
        if (attrMatcher.find()) {
            String v = attrMatcher.group(1).trim();
            if (!v.isEmpty()) {
                return v;
            }
        }
        Pattern textPattern = Pattern.compile("(?is)<a[^>]*>(.*?)</a>");
        Matcher textMatcher = textPattern.matcher(line);
        if (textMatcher.find()) {
            String v = stripTags(textMatcher.group(1));
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    /**
     * 从行片段提取日期（yyyy-MM-dd 或 yyyyMMdd）。
     *
     * @param line 行片段
     * @return 日期文本（yyyy-MM-dd 归一化, 未命中返回 null）
     */
    private String extractDate(String line) {
        if (line == null) {
            return null;
        }
        Pattern pattern = Pattern.compile("(\\d{4})[-年/](\\d{1,2})[-月/](\\d{1,2})日?");
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            try {
                int year = Integer.parseInt(matcher.group(1));
                if (year < MIN_YEAR || year > MAX_YEAR) {
                    continue;
                }
                LocalDate date = LocalDate.of(year,
                        Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
                return date.format(DATE_TIME_FORMATTER);
            } catch (Exception ignored) {
                // 非法日期（如把「11330000」误认为日期）继续找下一个
            }
        }
        return null;
    }

    /**
     * 去除 HTML 标签。
     *
     * @param html HTML 片段
     * @return 纯文本（可空）
     */
    private String stripTags(String html) {
        if (html == null) {
            return null;
        }
        return html.replaceAll("(?s)<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    /**
     * 按候选别名取第一个非空文本字段。
     *
     * @param node    节点
     * @param aliases 候选字段名
     * @return 字段文本（可能为 null）
     */
    private String firstText(JsonNode node, String... aliases) {
        if (node == null) {
            return null;
        }
        for (String alias : aliases) {
            JsonNode child = node.get(alias);
            if (child != null && !child.isNull() && !child.isMissingNode()) {
                String text = child.asText();
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    /**
     * 按候选别名取第一个存在的节点。
     *
     * @param node    节点
     * @param aliases 候选字段名
     * @return 命中的节点（可能为 null）
     */
    private JsonNode firstNode(JsonNode node, String... aliases) {
        if (node == null) {
            return null;
        }
        for (String alias : aliases) {
            JsonNode child = node.get(alias);
            if (child != null && !child.isNull() && !child.isMissingNode()) {
                return child;
            }
        }
        return null;
    }
}