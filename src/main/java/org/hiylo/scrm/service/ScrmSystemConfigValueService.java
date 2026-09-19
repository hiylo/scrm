/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemConfigValueService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.exception.ScrmException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 系统配置值处理服务。
 * <p>
 * 承载配置值子域: 值合法性验证 (类型检查 + 正则 + 范围)、显示值格式化、值解析转 Java 类型,
 * 以及简易 JSON 解析/序列化工具与字符串截断, 供配置项 / 组 / 历史兄弟类复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
public class ScrmSystemConfigValueService {

    /** 配置类型: 字符串 */
    static final String TYPE_STRING = "STRING";
    /** 配置类型: 整数 */
    static final String TYPE_INTEGER = "INTEGER";
    /** 配置类型: 浮点 */
    static final String TYPE_DOUBLE = "DOUBLE";
    /** 配置类型: 布尔 */
    static final String TYPE_BOOLEAN = "BOOLEAN";
    /** 配置类型: JSON */
    static final String TYPE_JSON = "JSON";
    /** 配置类型: XML */
    static final String TYPE_XML = "XML";
    /** 配置类型: 日期 */
    static final String TYPE_DATE = "DATE";
    /** 配置类型: 时间 */
    static final String TYPE_TIME = "TIME";
    /** 配置类型: 日期时间 */
    static final String TYPE_DATETIME = "DATETIME";
    /** 配置类型: 枚举 */
    static final String TYPE_ENUM = "ENUM";
    /** 配置类型: 密码 */
    static final String TYPE_PASSWORD = "PASSWORD";
    /** 配置类型: 加密 */
    static final String TYPE_ENCRYPTED = "ENCRYPTED";
    /** 配置类型: 文件 */
    static final String TYPE_FILE = "FILE";
    /** 配置类型: URL */
    static final String TYPE_URL = "URL";
    /** 配置类型: 邮箱 */
    static final String TYPE_EMAIL = "EMAIL";
    /** 配置类型: 手机号 */
    static final String TYPE_PHONE = "PHONE";
    /** 配置类型: 颜色 */
    static final String TYPE_COLOR = "COLOR";
    /** 配置类型: 富文本 */
    static final String TYPE_RICH_TEXT = "RICH_TEXT";

    /** 日期格式 */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 时间格式 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    /** 日期时间格式 */
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 邮箱正则 */
    private static final String REGEX_EMAIL = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    /** 手机号正则 (中国大陆) */
    private static final String REGEX_PHONE = "^1[3-9]\\d{9}$";
    /** URL 正则 */
    private static final String REGEX_URL = "^https?://[A-Za-z0-9.-]+(:\\d+)?(/.*)?$";
    /** 颜色正则 (#RRGGBB) */
    private static final String REGEX_COLOR = "^#[0-9A-Fa-f]{6}$";

    // ============================================================
    // 值验证 / 格式化 / 解析
    // ============================================================

    /**
     * 验证配置值合法性 (类型检查 + 正则验证 + 范围校验, 完整实现)。
     * <p>校验顺序: 类型检查 → 正则验证 → 范围校验 → 长度校验。
     * 任一校验失败立即抛出 ScrmException。</p>
     *
     * @param value     待验证值
     * @param type      配置类型
     * @param regex     验证正则（可空）
     * @param min       最小值（可空, 仅数值类型生效）
     * @param max       最大值（可空, 仅数值类型生效）
     * @param maxLength 最大长度（可空, 仅字符串类型生效）
     * @throws ScrmException 校验失败
     */
    public void validateValue(String value, String type, String regex, Double min, Double max, Integer maxLength)
            throws ScrmException {
        if (value == null || value.isEmpty()) {
            return;
        }
        // 类型检查
        validateType(value, type);
        // 长度校验 (字符串类型)
        if (maxLength != null && maxLength > 0) {
            if (value.length() > maxLength) {
                throw ScrmException.badRequest("配置值长度超过最大长度 " + maxLength
                        + ": actual=" + value.length());
            }
        }
        // 正则验证 (优先使用配置的 regex, 否则按类型应用内置正则)
        String effectiveRegex = regex;
        if (effectiveRegex == null || effectiveRegex.isBlank()) {
            effectiveRegex = builtInRegex(type);
        }
        if (effectiveRegex != null && !effectiveRegex.isBlank()) {
            try {
                if (!value.matches(effectiveRegex)) {
                    throw ScrmException.badRequest("配置值不匹配验证正则: " + effectiveRegex);
                }
            } catch (java.util.regex.PatternSyntaxException e) {
                throw ScrmException.badRequest("验证正则非法: " + effectiveRegex);
            }
        }
        // 范围校验 (数值类型)
        if (TYPE_INTEGER.equals(type) || TYPE_DOUBLE.equals(type)) {
            double numValue;
            try {
                numValue = Double.parseDouble(value);
            } catch (NumberFormatException e) {
                throw ScrmException.badRequest("配置值非数值: " + value);
            }
            if (min != null && numValue < min) {
                throw ScrmException.badRequest("配置值小于最小值 " + min + ": actual=" + numValue);
            }
            if (max != null && numValue > max) {
                throw ScrmException.badRequest("配置值大于最大值 " + max + ": actual=" + numValue);
            }
        }
    }

    /**
     * 类型检查: 校验值是否符合指定类型的格式。
     *
     * @param value 值
     * @param type  类型
     * @throws ScrmException 类型不匹配
     */
    private void validateType(String value, String type) throws ScrmException {
        if (type == null || type.isBlank()) {
            return;
        }
        switch (type) {
            case TYPE_INTEGER:
                try {
                    Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw ScrmException.badRequest("配置值非整数: " + value);
                }
                break;
            case TYPE_DOUBLE:
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    throw ScrmException.badRequest("配置值非浮点数: " + value);
                }
                break;
            case TYPE_BOOLEAN:
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    throw ScrmException.badRequest("配置值非布尔 (true/false): " + value);
                }
                break;
            case TYPE_JSON:
                try {
                    SimpleJsonParser.parse(value);
                } catch (Exception e) {
                    throw ScrmException.badRequest("配置值非合法 JSON: " + e.getMessage());
                }
                break;
            case TYPE_DATE:
                try {
                    LocalDate.parse(value, DATE_FMT);
                } catch (DateTimeParseException e) {
                    throw ScrmException.badRequest("配置值非日期 (yyyy-MM-dd): " + value);
                }
                break;
            case TYPE_DATETIME:
                try {
                    LocalDateTime.parse(value, DATETIME_FMT);
                } catch (DateTimeParseException e) {
                    throw ScrmException.badRequest("配置值非日期时间 (yyyy-MM-dd HH:mm:ss): " + value);
                }
                break;
            case TYPE_TIME:
                try {
                    java.time.LocalTime.parse(value, TIME_FMT);
                } catch (DateTimeParseException e) {
                    throw ScrmException.badRequest("配置值非时间 (HH:mm:ss): " + value);
                }
                break;
            case TYPE_ENUM:
            case TYPE_STRING:
            case TYPE_XML:
            case TYPE_PASSWORD:
            case TYPE_ENCRYPTED:
            case TYPE_FILE:
            case TYPE_RICH_TEXT:
                // 字符串族: 不做类型检查, 仅正则校验
                break;
            case TYPE_URL:
            case TYPE_EMAIL:
            case TYPE_PHONE:
            case TYPE_COLOR:
                // 由内置正则校验
                break;
            default:
                // 未知类型: 不做类型检查
                break;
        }
    }

    /**
     * 内置正则 (按类型返回内置校验正则, 不存在则返回 null)。
     *
     * @param type 类型
     * @return 正则字符串 (无则 null)
     */
    private String builtInRegex(String type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case TYPE_EMAIL:
                return REGEX_EMAIL;
            case TYPE_PHONE:
                return REGEX_PHONE;
            case TYPE_URL:
                return REGEX_URL;
            case TYPE_COLOR:
                return REGEX_COLOR;
            default:
                return null;
        }
    }

    /**
     * 格式化显示值 (按类型转换为人类可读形式, 完整实现)。
     * <p>BOOLEAN 转为 是/否, JSON 美化缩进, DATE/TIME/DATETIME 保持原格式,
     * PASSWORD/ENCRYPTED 显示为 ********, 其它原样返回。</p>
     *
     * @param value 值
     * @param type  类型
     * @return 显示值
     */
    public String formatDisplayValue(String value, String type) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (type == null || type.isBlank()) {
            return value;
        }
        switch (type) {
            case TYPE_BOOLEAN:
                return "true".equalsIgnoreCase(value) ? "是" : "否";
            case TYPE_INTEGER:
                try {
                    return String.valueOf(Long.parseLong(value));
                } catch (NumberFormatException e) {
                    return value;
                }
            case TYPE_DOUBLE:
                try {
                    return String.valueOf(Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    return value;
                }
            case TYPE_JSON:
                try {
                    Object parsed = SimpleJsonParser.parse(value);
                    return SimpleJsonParser.toPrettyJson(parsed);
                } catch (Exception e) {
                    return value;
                }
            case TYPE_PASSWORD:
            case TYPE_ENCRYPTED:
                return "********";
            case TYPE_DATE:
            case TYPE_TIME:
            case TYPE_DATETIME:
                return value;
            case TYPE_COLOR:
                return value;
            default:
                return value;
        }
    }

    /**
     * 解析值 (按类型转换为对应的 Java 类型, 完整实现)。
     * <p>STRING/JSON/XML/FILE/URL/EMAIL/PHONE/COLOR/PASSWORD/ENCRYPTED/RICH_TEXT/ENUM
     * 返回 String; INTEGER 返回 Long; DOUBLE 返回 Double; BOOLEAN 返回 Boolean;
     * DATE 返回 LocalDate; TIME 返回 LocalTime; DATETIME 返回 LocalDateTime;
     * 解析失败返回原字符串。</p>
     *
     * @param value 值
     * @param type  类型
     * @return 解析后的 Java 对象
     */
    public Object parseValue(String value, String type) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (type == null || type.isBlank()) {
            return value;
        }
        switch (type) {
            case TYPE_INTEGER:
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    return value;
                }
            case TYPE_DOUBLE:
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    return value;
                }
            case TYPE_BOOLEAN:
                if ("true".equalsIgnoreCase(value)) {
                    return Boolean.TRUE;
                }
                if ("false".equalsIgnoreCase(value)) {
                    return Boolean.FALSE;
                }
                return value;
            case TYPE_DATE:
                try {
                    return LocalDate.parse(value, DATE_FMT);
                } catch (DateTimeParseException e) {
                    return value;
                }
            case TYPE_TIME:
                try {
                    return java.time.LocalTime.parse(value, TIME_FMT);
                } catch (DateTimeParseException e) {
                    return value;
                }
            case TYPE_DATETIME:
                try {
                    return LocalDateTime.parse(value, DATETIME_FMT);
                } catch (DateTimeParseException e) {
                    return value;
                }
            case TYPE_JSON:
                try {
                    return SimpleJsonParser.parse(value);
                } catch (Exception e) {
                    return value;
                }
            default:
                return value;
        }
    }

    /**
     * 截断字符串到指定长度。
     *
     * @param s      字符串
     * @param maxLen 最大长度
     * @return 截断后的字符串
     */
    String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen);
    }

    /**
     * 简易 JSON 解析与序列化工具 (避免引入额外 JSON 依赖, 支持基础 Map/List/基础类型)。
     * <p>仅用于配置历史数据与 metadata 等简单结构, 复杂结构请使用 Jackson。</p>
     *
     * @author Hsi Chu
     * @since 2026-09-19
     */
    private static final class SimpleJsonParser {

        private SimpleJsonParser() {
        }

        /**
         * 解析 JSON 字符串为对象 (Map / List / String / Number / Boolean / null)。
         *
         * @param json JSON 字符串
         * @return 解析后的对象
         * @throws RuntimeException 解析失败
         */
        static Object parse(String json) {
            return new Parser(json).parseValue();
        }

        /**
         * 将对象序列化为 JSON 字符串 (紧凑形式)。
         *
         * @param obj 对象
         * @return JSON 字符串
         */
        static String toJson(Object obj) {
            StringBuilder sb = new StringBuilder();
            write(sb, obj);
            return sb.toString();
        }

        /**
         * 将对象序列化为美化 JSON 字符串 (2 空格缩进)。
         *
         * @param obj 对象
         * @return JSON 字符串
         */
        static String toPrettyJson(Object obj) {
            StringBuilder sb = new StringBuilder();
            writePretty(sb, obj, 0);
            return sb.toString();
        }

        @SuppressWarnings("unchecked")
        private static void write(StringBuilder sb, Object obj) {
            if (obj == null) {
                sb.append("null");
            } else if (obj instanceof String s) {
                writeString(sb, s);
            } else if (obj instanceof Map) {
                writeMap(sb, (Map<String, Object>) obj);
            } else if (obj instanceof Iterable it) {
                writeList(sb, it);
            } else if (obj instanceof LocalDateTime ldt) {
                writeString(sb, ldt.toString());
            } else {
                writeString(sb, obj.toString());
            }
        }

        private static void writeString(StringBuilder sb, String s) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> sb.append(c);
                }
            }
            sb.append('"');
        }

        private static void writeMap(StringBuilder sb, Map<String, Object> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, entry.getKey());
                sb.append(':');
                write(sb, entry.getValue());
            }
            sb.append('}');
        }

        private static void writeList(StringBuilder sb, Iterable<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, item);
            }
            sb.append(']');
        }

        @SuppressWarnings("unchecked")
        private static void writePretty(StringBuilder sb, Object obj, int indent) {
            if (obj == null) {
                sb.append("null");
            } else if (obj instanceof String s) {
                writeString(sb, s);
            } else if (obj instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) obj;
                if (map.isEmpty()) {
                    sb.append("{}");
                    return;
                }
                sb.append("{\n");
                boolean first = true;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (!first) {
                        sb.append(",\n");
                    }
                    first = false;
                    writeIndent(sb, indent + 1);
                    writeString(sb, entry.getKey());
                    sb.append(":");
                    writePretty(sb, entry.getValue(), indent + 1);
                }
                sb.append('\n');
                writeIndent(sb, indent);
                sb.append('}');
            } else if (obj instanceof Iterable it) {
                List<Object> list = new ArrayList<>();
                it.forEach(list::add);
                if (list.isEmpty()) {
                    sb.append("[]");
                    return;
                }
                sb.append("[\n");
                boolean first = true;
                for (Object item : list) {
                    if (!first) {
                        sb.append(",\n");
                    }
                    first = false;
                    writeIndent(sb, indent + 1);
                    writePretty(sb, item, indent + 1);
                }
                sb.append('\n');
                writeIndent(sb, indent);
                sb.append(']');
            } else if (obj instanceof LocalDateTime ldt) {
                writeString(sb, ldt.toString());
            } else {
                writeString(sb, obj.toString());
            }
        }

        /**
         * 写入缩进。
         *
         * @param sb     缓冲区
         * @param indent 缩进级别
         */
        private static void writeIndent(StringBuilder sb, int indent) {
            for (int i = 0; i < indent; i++) {
                sb.append(" ");
            }
        }

        /**
         * JSON 解析器
         *
         * @author Hsi Chu
         * @since 2026-09-19
         */
        private static final class Parser {
            /** 待解析的 JSON 原文 */
            private final String json;
            /** 当前解析位置 (字符下标, 取值范围 0..json.length()) */
            private int pos;

            Parser(String json) {
                this.json = json;
            }

            Object parseValue() {
                skipWhitespace();
                Object value = parseValueInternal();
                skipWhitespace();
                return value;
            }

            private Object parseValueInternal() {
                skipWhitespace();
                if (pos >= json.length()) {
                    throw new RuntimeException("JSON 解析意外结束");
                }
                char c = json.charAt(pos);
                if (c == '{') {
                    return parseObject();
                } else if (c == '[') {
                    return parseArray();
                } else if (c == '"') {
                    return parseString();
                } else if (c == 't' || c == 'f') {
                    return parseBoolean();
                } else if (c == 'n') {
                    return parseNull();
                } else {
                    return parseNumber();
                }
            }

            private Map<String, Object> parseObject() {
                Map<String, Object> map = new LinkedHashMap<>();
                expect('{');
                skipWhitespace();
                if (peek() == '}') {
                    pos++;
                    return map;
                }
                while (true) {
                    skipWhitespace();
                    String key = parseString();
                    skipWhitespace();
                    expect(':');
                    Object value = parseValue();
                    map.put(key, value);
                    skipWhitespace();
                    char c = next();
                    if (c == '}') {
                        break;
                    }
                    if (c != ',') {
                        throw new RuntimeException("JSON 对象缺少逗号或结束括号");
                    }
                }
                return map;
            }

            private List<Object> parseArray() {
                List<Object> list = new ArrayList<>();
                expect('[');
                skipWhitespace();
                if (peek() == ']') {
                    pos++;
                    return list;
                }
                while (true) {
                    Object value = parseValue();
                    list.add(value);
                    skipWhitespace();
                    char c = next();
                    if (c == ']') {
                        break;
                    }
                    if (c != ',') {
                        throw new RuntimeException("JSON 数组缺少逗号或结束括号");
                    }
                }
                return list;
            }

            private String parseString() {
                expect('"');
                StringBuilder sb = new StringBuilder();
                while (pos < json.length()) {
                    char c = json.charAt(pos++);
                    if (c == '"') {
                        return sb.toString();
                    }
                    if (c == '\\') {
                        if (pos >= json.length()) {
                            break;
                        }
                        char esc = json.charAt(pos++);
                        switch (esc) {
                            case '"' -> sb.append('"');
                            case '\\' -> sb.append('\\');
                            case '/' -> sb.append('/');
                            case 'n' -> sb.append('\n');
                            case 'r' -> sb.append('\r');
                            case 't' -> sb.append('\t');
                            case 'b' -> sb.append('\b');
                            case 'f' -> sb.append('\f');
                            case 'u' -> {
                                if (pos + 4 > json.length()) {
                                    throw new RuntimeException("JSON Unicode 转义不完整");
                                }
                                String hex = json.substring(pos, pos + 4);
                                sb.append((char) Integer.parseInt(hex, 16));
                                pos += 4;
                            }
                            default -> sb.append(esc);
                        }
                    } else {
                        sb.append(c);
                    }
                }
                throw new RuntimeException("JSON 字符串未闭合");
            }

            private Boolean parseBoolean() {
                if (json.startsWith("true", pos)) {
                    pos += 4;
                    return Boolean.TRUE;
                }
                if (json.startsWith("false", pos)) {
                    pos += 5;
                    return Boolean.FALSE;
                }
                throw new RuntimeException("JSON 布尔值非法");
            }

            private Object parseNull() {
                if (json.startsWith("null", pos)) {
                    pos += 4;
                    return null;
                }
                throw new RuntimeException("JSON null 非法");
            }

            private Number parseNumber() {
                int start = pos;
                if (peek() == '-') {
                    pos++;
                }
                while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                    pos++;
                }
                boolean isDouble = false;
                if (pos < json.length() && json.charAt(pos) == '.') {
                    isDouble = true;
                    pos++;
                    while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                        pos++;
                    }
                }
                if (pos < json.length() && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
                    isDouble = true;
                    pos++;
                    if (pos < json.length() && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) {
                        pos++;
                    }
                    while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                        pos++;
                    }
                }
                String num = json.substring(start, pos);
                if (isDouble) {
                    return Double.parseDouble(num);
                }
                try {
                    return Long.parseLong(num);
                } catch (NumberFormatException e) {
                    return Double.parseDouble(num);
                }
            }

            private void skipWhitespace() {
                while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
                    pos++;
                }
            }

            private void expect(char c) {
                if (pos >= json.length() || json.charAt(pos) != c) {
                    throw new RuntimeException("JSON 期望字符 '" + c + "' 但得到:"
                            + (pos < json.length() ? json.charAt(pos) : "EOF"));
                }
                pos++;
            }

            private char peek() {
                if (pos >= json.length()) {
                    throw new RuntimeException("JSON 意外结束");
                }
                return json.charAt(pos);
            }

            private char next() {
                if (pos >= json.length()) {
                    throw new RuntimeException("JSON 意外结束");
                }
                return json.charAt(pos++);
            }
        }
    }
}