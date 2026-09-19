/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateCenterRenderService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmTemplateRenderDto;
import org.hiylo.scrm.entity.ScrmMessageTemplateCenterEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SCRM 消息模板中心渲染子域服务。
 * <p>
 * 承载模板多渠道渲染 (变量替换 + 渠道适配)、变量校验、编辑器预览、变量提取与批量渲染,
 * 提供 {@code splitChannels} 供统计兄弟类以 package 级静态复用。
 * 模板实体查询复用 {@link ScrmMessageTemplateCenterTemplateService} 的 {@code findTemplateOrThrow}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMessageTemplateCenterRenderService {

    /** 变量插值正则: 匹配 {{varName}} 形式, 捕获组 1 为变量名 */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}}");

    /** HTML 标签剥离正则 */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    /** 短信渠道单条最大长度 */
    private static final int SMS_MAX_LENGTH = 500;

    /** 模板管理子域服务 (查询模板实体) */
    private final ScrmMessageTemplateCenterTemplateService templateService;

    /** JSON 序列化/反序列化器 (variables 字段读写) */
    private final ObjectMapper objectMapper;

    /**
     * 渲染模板: 变量替换 → 渠道适配 → 返回渲染结果。
     *
     * @param renderDto 渲染参数
     * @return 渲染结果 (含 channel 与渠道适配后的字段)
     * @throws ScrmException 模板不存在 / 渠道不适用 / 变量校验失败
     */
    @Transactional(readOnly = true)
    public Map<String, Object> render(ScrmTemplateRenderDto renderDto) throws ScrmException {
        if (renderDto == null || renderDto.getTemplateId() == null) {
            throw ScrmException.badRequest("渲染参数不能为空");
        }
        return renderForChannel(renderDto.getTemplateId(), renderDto.getChannel(), renderDto.getVariables());
    }

    /**
     * 按渠道渲染模板: 确定渠道 → 校验变量 → 替换变量 → 渠道适配。
     *
     * @param templateId 模板 ID
     * @param channel    目标渠道（可空, 空则取模板首个适用渠道）
     * @param variables  变量值映射
     * @return 渲染结果
     * @throws ScrmException 模板不存在 / 渠道不适用 / 变量校验失败
     */
    @Transactional(readOnly = true)
    public Map<String, Object> renderForChannel(Long templateId, String channel, Map<String, Object> variables)
            throws ScrmException {
        ScrmMessageTemplateCenterEntity template = templateService.findTemplateOrThrow(templateId);
        // 确定渠道: 传入为空时取模板首个适用渠道
        String targetChannel = channel;
        if (targetChannel == null || targetChannel.isBlank()) {
            targetChannel = pickFirstChannel(template.getChannels());
            if (targetChannel == null) {
                throw ScrmException.badRequest("模板未配置适用渠道, 无法渲染: templateId=" + templateId);
            }
        } else if (!isChannelApplicable(template.getChannels(), targetChannel)) {
            throw ScrmException.badRequest("渠道不适用该模板: channel=" + targetChannel
                    + ", applicable=" + template.getChannels());
        }
        // 渠道适配前先校验变量 (仅记录, 不阻断预览类场景; 此处正式渲染阻断)
        Map<String, Object> validation = validateVariables(template, variables);
        Boolean valid = (Boolean) validation.get("valid");
        if (Boolean.FALSE.equals(valid)) {
            throw ScrmException.badRequest("变量校验失败: 缺失必填=" + validation.get("missingRequired")
                    + ", 类型不匹配=" + validation.get("typeMismatches"));
        }
        // 变量替换
        String renderedSubject = doReplace(template.getSubject(), variables);
        String renderedContent = doReplace(template.getContent(), variables);
        String renderedPlain = doReplace(template.getPlainContent(), variables);
        String renderedHtml = doReplace(template.getHtmlContent(), variables);
        // 渠道适配
        Map<String, Object> result = adaptToChannel(template, targetChannel,
                renderedSubject, renderedContent, renderedPlain, renderedHtml);
        result.put("templateId", template.getId());
        result.put("templateCode", template.getTemplateCode());
        result.put("templateName", template.getTemplateName());
        result.put("templateType", template.getTemplateType());
        return result;
    }

    /**
     * 验证变量: 检查必填项是否提供、类型是否匹配。
     *
     * @param templateId 模板 ID
     * @param variables  变量值映射
     * @return 校验结果 {valid, missingRequired, typeMismatches}
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> validateVariables(
            Long templateId, Map<String, Object> variables) throws ScrmException {
        ScrmMessageTemplateCenterEntity template = templateService.findTemplateOrThrow(templateId);
        return validateVariables(template, variables);
    }

    /**
     * 预览模板: 渲染全部字段 (不做渠道适配, 不校验必填), 用于编辑器实时预览。
     *
     * @param templateId 模板 ID
     * @param variables  变量值映射
     * @return 预览结果
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(Long templateId, Map<String, Object> variables) throws ScrmException {
        ScrmMessageTemplateCenterEntity template = templateService.findTemplateOrThrow(templateId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("templateId", template.getId());
        result.put("templateCode", template.getTemplateCode());
        result.put("subject", doReplace(template.getSubject(), variables));
        result.put("content", doReplace(template.getContent(), variables));
        result.put("plainContent", doReplace(template.getPlainContent(), variables));
        result.put("htmlContent", doReplace(template.getHtmlContent(), variables));
        result.put("channels", template.getChannels());
        result.put("templateType", template.getTemplateType());
        return result;
    }

    /**
     * 从内容中提取变量名列表 (去重保留顺序)。
     *
     * @param content 模板内容
     * @return 变量名列表
     */
    public List<String> extractVariables(String content) {
        List<String> variables = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return variables;
        }
        Set<String> seen = new HashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (seen.add(name)) {
                variables.add(name);
            }
        }
        return variables;
    }

    /**
     * 批量渲染多个模板。
     *
     * @param renderDtos 渲染参数列表
     * @return 渲染结果列表 (与入参顺序一致, 单条失败时该条置为 error)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> batchRender(List<ScrmTemplateRenderDto> renderDtos) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (renderDtos == null) {
            return results;
        }
        for (ScrmTemplateRenderDto dto : renderDtos) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("templateId", dto == null ? null : dto.getTemplateId());
            try {
                item.put("result", render(dto));
                item.put("success", true);
            } catch (ScrmException e) {
                item.put("success", false);
                item.put("error", e.getMessage());
            }
            results.add(item);
        }
        return results;
    }

    /**
     * 渲染纯文本: 将 content 中的 {{key}} 替换为变量值, 缺失替换为空串。
     *
     * @param content   模板内容
     * @param variables 变量值映射
     * @return 渲染后的纯文本
     */
    public String renderPlainText(String content, Map<String, Object> variables) {
        return doReplace(content, variables);
    }

    /**
     * 渲染 HTML: 将 htmlContent 中的 {{key}} 替换为变量值, 缺失替换为空串。
     *
     * @param htmlContent HTML 内容
     * @param variables   变量值映射
     * @return 渲染后的 HTML
     */
    public String renderHtml(String htmlContent, Map<String, Object> variables) {
        return doReplace(htmlContent, variables);
    }

    /**
     * 变量替换核心: 将 text 中的 {{key}} 替换为 variables.get(key) 的字符串值, 缺失替换为空串。
     *
     * @param text      待替换文本
     * @param variables 变量值映射
     * @return 替换后文本
     */
    private String doReplace(String text, Map<String, Object> variables) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = (variables != null && variables.containsKey(key)) ? variables.get(key) : null;
            String replacement = value != null ? String.valueOf(value) : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 校验变量 (内部重载, 直接基于模板实体)。
     *
     * @param template  模板实体
     * @param variables 变量值映射
     * @return 校验结果
     */
    private Map<String, Object> validateVariables(
            ScrmMessageTemplateCenterEntity template, Map<String, Object> variables) {
        List<String> missing = new ArrayList<>();
        List<String> typeMismatch = new ArrayList<>();
        List<Map<String, Object>> varDefs = parseVariableDefs(template.getVariables());
        for (Map<String, Object> def : varDefs) {
            String name = (String) def.get("name");
            Boolean required = (Boolean) def.get("required");
            String type = (String) def.get("type");
            boolean present = variables != null && variables.containsKey(name) && variables.get(name) != null;
            if (Boolean.TRUE.equals(required) && !present) {
                missing.add(name);
            }
            if (present && type != null && !checkType(variables.get(name), type)) {
                typeMismatch.add(name + "(" + type + ")");
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", missing.isEmpty() && typeMismatch.isEmpty());
        result.put("missingRequired", missing);
        result.put("typeMismatches", typeMismatch);
        return result;
    }

    /**
     * 解析变量定义 JSON 为 List<Map>。
     *
     * @param variablesJson 变量定义 JSON 字符串
     * @return 变量定义列表
     */
    private List<Map<String, Object>> parseVariableDefs(String variablesJson) {
        if (variablesJson == null || variablesJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(variablesJson, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            log.warn("变量定义 JSON 解析失败, 忽略校验: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 校验变量值类型是否匹配定义。
     *
     * @param value 变量值
     * @param type  类型 (STRING/NUMBER/INTEGER/BOOLEAN/DATE)
     * @return 是否匹配
     */
    private boolean checkType(Object value, String type) {
        if (value == null || type == null) {
            return true;
        }
        String upper = type.toUpperCase();
        try {
            switch (upper) {
                case "STRING":
                    return true;
                case "NUMBER":
                case "DECIMAL":
                case "FLOAT":
                case "DOUBLE":
                    Double.parseDouble(String.valueOf(value));
                    return true;
                case "INTEGER":
                case "INT":
                case "LONG":
                    Long.parseLong(String.valueOf(value));
                    return true;
                case "BOOLEAN":
                case "BOOL":
                    String s = String.valueOf(value).toLowerCase();
                    return "true".equals(s) || "false".equals(s);
                default:
                    return true;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 渠道适配: 按目标渠道裁剪/重组渲染字段。
     *
     * @param template       模板实体
     * @param channel        目标渠道
     * @param subject        渲染后主题
     * @param content        渲染后内容
     * @param plainContent   渲染后纯文本
     * @param htmlContent    渲染后 HTML
     * @return 渠道适配结果
     */
    private Map<String, Object> adaptToChannel(ScrmMessageTemplateCenterEntity template, String channel,
                                                 String subject, String content, String plainContent,
                                                        String htmlContent) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("channel", channel);
        switch (channel) {
            case "SMS":
                // 短信: 仅纯文本, 无主题, 超长截断
                String smsContent = plainContent != null ? plainContent : stripHtml(content);
                if (smsContent != null && smsContent.length() > SMS_MAX_LENGTH) {
                    smsContent = smsContent.substring(0, SMS_MAX_LENGTH);
                }
                result.put("content", smsContent);
                break;
            case "EMAIL":
                // 邮件: 主题 + HTML (无 HTML 时退化为内容)
                result.put("subject", subject);
                result.put("htmlContent", htmlContent != null ? htmlContent : content);
                result.put("plainContent", plainContent);
                break;
            case "APP_PUSH":
                // APP 推送: 标题 (主题) + 正文 (纯文本)
                result.put("title", subject);
                result.put("content", plainContent != null ? plainContent : stripHtml(content));
                break;
            case "WECHAT":
                // 微信: 纯文本内容
                result.put("content", plainContent != null ? plainContent : content);
                break;
            case "WORK_WECHAT":
                // 企微: 纯文本内容 + 企微链接
                result.put("content", plainContent != null ? plainContent : content);
                result.put("wechatLink", template.getWechatLink());
                break;
            case "WEB_SOCKET":
                // WebSocket: JSON 负载
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("subject", subject);
                payload.put("content", content);
                payload.put("templateType", template.getTemplateType());
                result.put("payload", payload);
                break;
            case "DOUYIN":
            case "KUAISHOU":
                // 抖音/快手: 纯文本内容
                result.put("content", plainContent != null ? plainContent : content);
                break;
            default:
                result.put("subject", subject);
                result.put("content", content);
                result.put("plainContent", plainContent);
                result.put("htmlContent", htmlContent);
        }
        return result;
    }

    /**
     * 剥离 HTML 标签, 转为纯文本。
     *
     * @param html HTML 字符串
     * @return 纯文本
     */
    private String stripHtml(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        return HTML_TAG_PATTERN.matcher(html).replaceAll("");
    }

    /**
     * 拆分渠道字符串为渠道列表。
     *
     * @param channels 渠道字符串 (逗号分隔)
     * @return 渠道列表
     */
    static List<String> splitChannels(String channels) {
        List<String> list = new ArrayList<>();
        if (channels == null || channels.isBlank()) {
            return list;
        }
        for (String ch : channels.split(",")) {
            String trimmed = ch.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }

    /**
     * 取模板首个适用渠道。
     *
     * @param channels 渠道字符串
     * @return 首个渠道 (无则 null)
     */
    private String pickFirstChannel(String channels) {
        List<String> list = splitChannels(channels);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 判断渠道是否适用模板。
     *
     * @param channels     模板渠道字符串
     * @param channel      目标渠道
     * @return 是否适用
     */
    private boolean isChannelApplicable(String channels, String channel) {
        return splitChannels(channels).contains(channel);
    }
}