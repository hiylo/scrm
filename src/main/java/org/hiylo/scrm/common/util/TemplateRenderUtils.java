/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : TemplateRenderUtils.java
 * Date : 2026/10/28 04:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.common.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板占位符渲染工具类。
 * <p>
 * 全项目唯一的模板变量渲染入口。此前项目里并存 6 份实现、3 种占位符语法：
 * </p>
 * <table border="1">
 *   <caption>统一前的实现与语法</caption>
 *   <tr><th>服务</th><th>语法</th><th>缺失变量</th></tr>
 *   <tr><td>通知中心 / 话术 / 自动回复</td><td>{@code {var}}</td><td>保留原占位符</td></tr>
 *   <tr><td>消息模板 / 模板中心 / 合同模板</td><td>{@code {{var}}}</td><td>替换为空串</td></tr>
 *   <tr><td>欢迎语</td><td>{@code ${var}}</td><td>替换为空串</td></tr>
 * </table>
 * <p>
 * 语法不统一造成过真实故障：合同模板按直觉写成 {@code ${customerName}}，而合同渲染器
 * 只认 {@code {{...}}}，结果合同正文带着未替换的占位符进入正式文件，全程没有任何报错。
 * </p>
 * <p>
 * <b>本类的取舍</b>：
 * </p>
 * <ul>
 *   <li><b>三种语法全部识别</b>——库里已有真实数据分别使用三种写法，强行统一成一种
 *       会让存量模板静默失效，代价高于收益；</li>
 *   <li><b>缺失变量保留原占位符</b>——替换成空串会让「漏传变量」表现为「内容缺一块」，
 *       既难排查也可能产出语义错误的正式文件。保留原样后，
 *       {@link #hasUnresolvedPlaceholder(String)} 能把它检出来；</li>
 *   <li><b>单次扫描</b>——不逐个变量反复 {@code String.replace}，避免变量值本身含有
 *       占位符时被二次替换（如变量值里恰好写了 {@code {amount}}）。</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public final class TemplateRenderUtils {

    /**
     * 占位符正则，同时匹配三种写法。
     * <p>分组 1/2/3 分别对应 {@code ${var}} / {@code {{var}}} / {@code {var}}，取非空的那组。</p>
     * <p>
     * 变量名用 {@code [^{}\s]+} 而不是 {@code \w+}：营销与运营模板里存在中文占位符
     * （如 {@code {{客户类型}}}），{@code \w+} 只匹配 ASCII 字母数字下划线，会把中文占位符
     * 当作普通文本漏掉，导致残留检测失效。
     * </p>
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "\\$\\{\\s*([^{}\\s]+)\\s*}"
                    + "|\\{\\{\\s*([^{}\\s]+)\\s*}}"
                    + "|\\{\\s*([^{}\\s]+)\\s*}");

    /** 工具类，私有构造防止实例化 */
    private TemplateRenderUtils() {
    }

    /**
     * 渲染模板：把占位符替换为变量值，缺失的变量保留原占位符。
     *
     * @param template  模板内容，可为 {@code null}
     * @param variables 变量映射，可为 {@code null}
     * @return 渲染后的字符串；模板为 {@code null} 时返回 {@code null}
     */
    public static String render(String template, Map<String, ?> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String varName = resolveVarName(matcher);
            Object value = variables.get(varName);
            // 缺失变量保留原占位符，便于 hasUnresolvedPlaceholder 检出
            String replacement = value == null ? matcher.group() : String.valueOf(value);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 提取模板中出现的变量名（去重，保持出现顺序）。
     *
     * @param template 模板内容，可为 {@code null}
     * @return 变量名列表；无占位符时返回空列表
     */
    public static List<String> extractVariables(String template) {
        List<String> result = new ArrayList<>();
        if (template == null || template.isEmpty()) {
            return result;
        }
        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        while (matcher.find()) {
            seen.add(resolveVarName(matcher));
        }
        result.addAll(seen);
        return result;
    }

    /**
     * 判断渲染后是否仍残留未替换的占位符。
     * <p>
     * 用于生成正式文件（合同、通知正文）前的最后一道检查：残留说明有变量没传进来，
     * 此时应当提示或阻断，而不是把带占位符的内容直接交付。
     * </p>
     *
     * @param rendered 渲染后的内容，可为 {@code null}
     * @return 存在未替换占位符返回 {@code true}
     */
    public static boolean hasUnresolvedPlaceholder(String rendered) {
        return rendered != null && !rendered.isEmpty()
                && PLACEHOLDER_PATTERN.matcher(rendered).find();
    }

    /**
     * 从匹配结果中取出变量名。
     * <p>三个捕获组只会有一个非空，其余为 {@code null}。</p>
     *
     * @param matcher 已 {@code find()} 成功的匹配器
     * @return 变量名
     */
    private static String resolveVarName(Matcher matcher) {
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String group = matcher.group(i);
            if (group != null) {
                return group;
            }
        }
        return "";
    }
}
