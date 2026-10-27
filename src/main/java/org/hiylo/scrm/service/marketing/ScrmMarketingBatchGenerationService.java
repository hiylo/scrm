/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingBatchGenerationService.java
 * Date : 2026/10/23 16:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.marketing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.ScrmMarketingBatchProperties;
import org.hiylo.scrm.dto.ScrmMarketingBatchGenerateDto;
import org.hiylo.scrm.dto.ScrmMarketingVariantDto;
import org.hiylo.scrm.entity.ScrmMessageTemplateCenterEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.feign.AiChatClient;
import org.hiylo.scrm.feign.dto.AiChatRequest;
import org.hiylo.scrm.feign.dto.AiChatResponse;
import org.hiylo.scrm.service.ScrmMessageTemplateCenterService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 财税营销文案 AI 批量生成服务（模块④核心）。
 * <p>
 * 链路: 按模板编码加载模板 → 校验占位符取值 → 渲染「模板 + 参数」为提示词输入 →
 * 拼接 prompts/marketing-batch.md 主指令 → 调用既有 {@link AiChatClient}（OpenAI 兼容,
 * 含降级）→ 解析 JSON 数组变体 → {@link MarketingComplianceValidator} 程序化拦截 →
 * 返回通过项 (status=DRAFT / review_status=PENDING, 由 {@link ScrmMarketingTemplateService}
 * 落库)。人工审核前不得分发（见 {@code ScrmMessageTemplateCenterService} 审核闸门）。
 * </p>
 * <p>
 * 降级: AI 不可用 / 输出非法 JSON / 全部变体不合规 → 返回 error 与空 variants,
 * 不产生任何 DRAFT 入库（避免「假生成」进入人工审核队列）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMarketingBatchGenerationService {

    /** 系统提示词角色 */
    private static final String ROLE_SYSTEM = "system";

    /** 用户消息角色 */
    private static final String ROLE_USER = "user";

    /** 提示词占位符: 场景（可空, 无则用模板分类） */
    private static final String PH_SCENARIO = "{场景}";

    /** 提示词占位符: 模板标题 */
    private static final String PH_TEMPLATE_TITLE = "{模板标题}";

    /** 提示词占位符: 模板正文 */
    private static final String PH_TEMPLATE_BODY = "{模板正文}";

    /** 提示词占位符: 占位符取值 JSON */
    private static final String PH_VARIABLES = "{占位符 JSON：客户类型/服务内容/活动信息/期限 等}";

    /** 提示词占位符: 语气档位 */
    private static final String PH_TONE = "{语气档位}";

    /** 提示词占位符: 字数上限 */
    private static final String PH_CHAR_LIMIT = "{字数上限}";

    /** 提示词占位符: 渠道字数上限 (资源文件「企微/微信正文:{渠道字数上限}」子字段) */
    private static final String PH_CHANNEL_CHAR_LIMIT = "{渠道字数上限}";

    /** 提示词占位符: 渠道 */
    private static final String PH_CHANNEL = "{渠道：企微/微信/短信/邮件}";

    /** 提示词占位符: 变体数量 */
    private static final String PH_VARIANT_COUNT = "{变体数量}";

    /** 消息模板中心服务 (模板加载) */
    private final ScrmMessageTemplateCenterService templateCenterService;

    /** AI 对话客户端 (复用既有通道, 含降级) */
    private final AiChatClient aiChatClient;

    /** JSON 序列化/反序列化器 (占位符取值与 AI 输出解析) */
    private final ObjectMapper objectMapper;

    /** 提示词资源加载器 */
    private final ResourceLoader resourceLoader;

    /** 批量生成配置 */
    private final ScrmMarketingBatchProperties properties;

    /** 合规校验器 (程序化拦截) */
    private final MarketingComplianceValidator complianceValidator;

    /**
     * 批量生成多套营销文案变体。
     * <p>
     * AI 不可用 / 输出非法 / 全部变体不合规时, 返回 {@code error != null} 且
     * {@code variants} 为空列表（不产生 DRAFT 入库）。通过的变体由
     * {@link ScrmMarketingTemplateService#saveVariantsAsDrafts} 落库。
     * </p>
     *
     * @param dto 生成请求 (模板编码 + 占位符取值 + 语气/渠道/变体数量)
     * @return 生成结果: {template, channel, variantCount, variants[], warnings[], error?}
     * @throws ScrmException 模板不存在 / 占位符取值非法
     */
    public Map<String, Object> batchGenerate(ScrmMarketingBatchGenerateDto dto) throws ScrmException {
        if (dto == null || dto.getTemplateCode() == null || dto.getTemplateCode().isBlank()) {
            throw ScrmException.badRequest("模板编码不能为空");
        }
        ScrmMessageTemplateCenterEntity template =
                templateCenterService.getTemplateByCode(dto.getTemplateCode());
        String channel = resolveChannel(dto, template);
        int variantCount = resolveVariantCount(dto.getVariantCount());
        int charLimit = complianceValidator.resolveCharLimit(channel, dto.getCharLimit());
        String tone = dto.getTone() == null || dto.getTone().isBlank() ? "亲切自然" : dto.getTone();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("templateCode", template.getTemplateCode());
        result.put("templateName", template.getTemplateName());
        result.put("channel", channel);
        result.put("variantCount", variantCount);
        result.put("tone", tone);

        // AI 主指令 + 参数注入 → 调用大模型
        String userPrompt = buildUserPrompt(template, dto, variantCount, channel, charLimit, tone);
        String raw = callAi(template, userPrompt);
        if (raw == null) {
            result.put("error", "AI 服务暂不可用, 未生成任何变体");
            result.put("variants", new ArrayList<>());
            result.put("warnings", new ArrayList<>());
            return result;
        }
        List<ScrmMarketingVariantDto> parsed = parseVariants(raw);
        if (parsed.isEmpty()) {
            result.put("error", "AI 输出无法解析为合规变体 (非 JSON 数组或字段缺失)");
            result.put("variants", new ArrayList<>());
            result.put("warnings", new ArrayList<>());
            return result;
        }
        // 程序化合规拦截: 不合规项整批打回, 不进入人工审核
        List<ScrmMarketingVariantDto> passed = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (ScrmMarketingVariantDto variant : parsed) {
            List<String> violations = complianceValidator.validate(variant, channel, dto.getCharLimit() != null
                    ? dto.getCharLimit() : charLimit);
            if (violations.isEmpty()) {
                // 若无 charCount, 服务层按口径补算
                if (variant.getCharCount() == null) {
                    variant.setCharCount(complianceValidator.charCount(variant.getContent()));
                }
                // 合并人工重点审核建议 (非硬性打回项, 入 riskNotes 供审核人核对)
                List<String> advisories = complianceValidator.collectAdvisories(variant);
                if (!advisories.isEmpty()) {
                    if (variant.getRiskNotes() == null) {
                        variant.setRiskNotes(new ArrayList<>());
                    }
                    variant.getRiskNotes().addAll(advisories);
                }
                passed.add(variant);
            } else {
                warnings.add("变体「" + (variant.getTitle() == null ? "" : variant.getTitle())
                        + "」未通过合规校验: " + String.join(", ", violations));
            }
        }
        result.put("variants", passed);
        result.put("warnings", warnings);
        if (passed.isEmpty()) {
            result.put("error", "全部变体未通过合规校验, 已打回 (见 warnings)");
        }
        return result;
    }

    /**
     * 确定目标渠道: 入参优先, 其次模板首个适用渠道。
     *
     * @param dto      生成请求
     * @param template 模板实体
     * @return 渠道编码 (WORK_WECHAT/WECHAT/SMS/EMAIL/...)
     */
    private String resolveChannel(ScrmMarketingBatchGenerateDto dto,
                                  ScrmMessageTemplateCenterEntity template) {
        if (dto.getChannel() != null && !dto.getChannel().isBlank()) {
            return dto.getChannel().toUpperCase();
        }
        String channels = template.getChannels();
        if (channels != null && !channels.isBlank()) {
            return channels.split(",")[0].trim();
        }
        return "WORK_WECHAT";
    }

    /**
     * 确定变体数量: 入参优先, 缺失取配置默认, 超出配置上限收敛。
     *
     * @param variantCount 入参变体数量 (可空)
     * @return 变体数量
     */
    private int resolveVariantCount(Integer variantCount) {
        if (variantCount == null || variantCount <= 0) {
            return properties.getDefaultVariantCount();
        }
        return Math.min(variantCount, properties.getMaxVariantCount());
    }

    /**
     * 构建用户消息: 场景/模板标题/模板正文/占位符取值/语气/字数/渠道 注入主指令。
     *
     * @param template     模板实体
     * @param dto          生成请求
     * @param variantCount 变体数量
     * @param channel      目标渠道
     * @param charLimit    字数上限
     * @param tone         语气档位
     * @return 用户消息全文 (主指令 + 参数)
     */
    private String buildUserPrompt(ScrmMessageTemplateCenterEntity template,
                                   ScrmMarketingBatchGenerateDto dto,
                                   int variantCount, String channel, int charLimit, String tone) {
        String mainPrompt = loadMainPrompt();
        String scenario = template.getCategory() == null || template.getCategory().isBlank()
                ? (template.getApplicableScenarios() == null ? "" : template.getApplicableScenarios())
                : template.getCategory();
        // 渠道文案: 提示词要求「企微/微信/短信/邮件」自然语言
        String channelLabel = toChannelLabel(channel);
        String variablesJson;
        try {
            variablesJson = objectMapper.writeValueAsString(dto.getVariables() == null
                    ? Map.of() : dto.getVariables());
        } catch (Exception e) {
            variablesJson = "{}";
        }
        String prompt = mainPrompt
                .replace(PH_SCENARIO, nullToEmpty(scenario))
                .replace(PH_TEMPLATE_TITLE, nullToEmpty(template.getTemplateName()))
                .replace(PH_TEMPLATE_BODY, nullToEmpty(template.getContent()))
                .replace(PH_VARIABLES, variablesJson)
                .replace(PH_TONE, tone)
                .replace(PH_CHAR_LIMIT, String.valueOf(charLimit))
                .replace(PH_CHANNEL_CHAR_LIMIT, String.valueOf(charLimit))
                .replace(PH_CHANNEL, channelLabel)
                .replace(PH_VARIANT_COUNT, String.valueOf(variantCount));
        log.info("构建营销批量生成提示词: templateCode={}, channel={}, variantCount={}, charLimit={}, tone={}",
                template.getTemplateCode(), channel, variantCount, charLimit, tone);
        return prompt;
    }

    /**
     * 渠道编码转提示词自然语言标签。
     *
     * @param channel 渠道编码
     * @return 自然语言渠道 (默认 企微)
     */
    private String toChannelLabel(String channel) {
        if (channel == null) {
            return "企微";
        }
        switch (channel.toUpperCase()) {
            case "WECHAT":
                return "微信";
            case "SMS":
                return "短信";
            case "EMAIL":
                return "邮件";
            default:
                return "企微";
        }
    }

    /**
     * 加载批量生成主指令 (classpath:prompts/marketing-batch.md)。
     * <p>资源缺失时降级为内置精简主指令, 保证链路不中断（生成质量以资源文件为准）。</p>
     *
     * @return 主指令文本
     */
    private String loadMainPrompt() {
        String location = properties.getPromptResource();
        if (location == null || location.isBlank()) {
            location = "classpath:prompts/marketing-batch.md";
        }
        try {
            Resource resource = resourceLoader.getResource(location);
            if (resource.exists()) {
                return resource.getContentAsString(StandardCharsets.UTF_8);
            }
            log.warn("营销批量生成提示词资源不存在, 使用内置精简主指令: location={}", location);
        } catch (Exception e) {
            log.warn("加载营销批量生成提示词失败, 使用内置精简主指令: location={}, err={}",
                    location, e.getMessage());
        }
        return "你是财税行业资深营销文案撰稿人, 只使用简体中文。根据给定模板与参数为【"
                + "{模板标题}】批量生成 {" + PH_VARIANT_COUNT + "} 套完整成文变体;"
                + " 变体之间开头钩子/正文结构/行动号召互不相同; 不得保留未替换占位符;"
                + " 不得使用保证收益/绝对化用语/夸大承诺/违规暗示/虚假紧迫表述;"
                + " 涉税内容必须附「具体以官方文件及主管税务机关答复为准」;"
                + " 严格输出 JSON 数组, 每项含 title/content/tone/charCount/riskNotes。";
    }

    /**
     * 调用 AI 批量生成, 返回原始响应文本。
     *
     * @param template   模板实体 (取配置缺失时的全局模型)
     * @param userPrompt 用户消息
     * @return AI 返回文本; AI 不可用/降级时返回 null (不抛异常)
     */
    private String callAi(ScrmMessageTemplateCenterEntity template, String userPrompt) {
        try {
            AiChatRequest request = AiChatRequest.builder()
                    .model(properties.getModel())
                    .temperature(properties.getTemperature())
                    .maxTokens(properties.getMaxTokens())
                    .messages(List.of(
                            new AiChatRequest.AiChatMessage(ROLE_SYSTEM, buildSystemPrompt(template)),
                            new AiChatRequest.AiChatMessage(ROLE_USER, userPrompt)
                    ))
                    .build();
            AiChatResponse response = aiChatClient.chatCompletion(request);
            String content = response == null ? null : response.getFirstContent();
            if (content == null || content.isBlank() || AiChatClient.FALLBACK_CONTENT.equals(content)) {
                log.warn("营销批量生成触发降级: templateCode={}, model={}", template.getTemplateCode(), request.getModel());
                return null;
            }
            return content;
        } catch (Exception e) {
            log.warn("批量生成调用 AI 异常: templateCode={}, err={}", template.getTemplateCode(), e.getMessage());
            return null;
        }
    }

    /**
     * 构建系统提示词: 人设 + 输出纪律 (资源主指令之外的固定约束)。
     *
     * @param template 模板实体
     * @return 系统提示词
     */
    private String buildSystemPrompt(ScrmMessageTemplateCenterEntity template) {
        return "你是财税行业的资深营销文案撰稿人, 服务于一家合规经营的财税服务公司"
                + "（业务范围: 代理记账、工商注册/注销、资质代办、税务申报、财税咨询）。"
                + "只使用简体中文, 面向中国大陆中小微企业与个体工商户经营者。"
                + "输出必须为合法 JSON 数组, 不得包含 JSON 以外的任何文字。当前模板: "
                + (template == null ? "" : template.getTemplateName());
    }

    /**
     * 解析 AI 输出 JSON 数组为变体列表。
     * <p>兼容 {@code ```json ... ```} 代码块包裹; 解析失败或空数组返回空列表。</p>
     *
     * @param raw AI 原始输出
     * @return 变体列表 (可能为空)
     */
    private List<ScrmMarketingVariantDto> parseVariants(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        String json = raw.trim();
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                json = json.substring(firstNewline + 1, lastFence).trim();
            }
        }
        if (!json.startsWith("[")) {
            log.warn("AI 批量生成输出不是 JSON 数组, 丢弃: head={}", truncate(json, 120));
            return new ArrayList<>();
        }
        try {
            List<ScrmMarketingVariantDto> variants =
                    objectMapper.readValue(json, new TypeReference<List<ScrmMarketingVariantDto>>() { });
            return variants == null ? new ArrayList<>() : variants;
        } catch (Exception e) {
            log.warn("解析 AI 批量生成 JSON 失败, 丢弃: err={}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 空值安全: null 转空串。
     *
     * @param value 原始值
     * @return 非空字符串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 截断字符串用于日志。
     *
     * @param text   原始文本
     * @param maxLen 最大长度
     * @return 截断后文本
     */
    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}