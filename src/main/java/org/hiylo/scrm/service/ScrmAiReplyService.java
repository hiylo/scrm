/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiReplyService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmPersonaEntity;
import org.hiylo.scrm.feign.AiChatClient;
import org.hiylo.scrm.feign.dto.AiChatRequest;
import org.hiylo.scrm.feign.dto.AiChatResponse;

import org.hiylo.scrm.repository.ScrmPersonaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * SCRM AI 自动回复服务
 * <p>
 * Lua 行为流脚本调用 {@code ai.generate(lastMsg, state.personaId, state.replyRules)} 时，
 * 经由 scrm-server 回调端点进入本服务。本服务依据人设（昵称 / 签名 / 话术风格标签）与
 * 回复规则构建系统提示词，调用 ai-server 对话补全接口生成符合人设风格的回复内容。
 * </p>
 * <p>ai-server 不可达时通过 Feign 降级返回降级提示，本服务进一步替换为客户友好的兜底回复，
 * 保证消息流不中断。</p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAiReplyService {

    /** 默认人设昵称（人设缺失时使用） */
    private static final String DEFAULT_NICKNAME = "AI 助手";

    /** 默认人设签名（人设缺失时使用） */
    private static final String DEFAULT_SIGNATURE = "贴心、专业、高效";

    /** AI 不可用时的客户友好兜底回复 */
    private static final String FALLBACK_REPLY = "您好，我稍后回复您。";

    /** 系统提示词角色 */
    private static final String ROLE_SYSTEM = "system";

    /** 用户消息角色 */
    private static final String ROLE_USER = "user";

    /** ai-server Feign 客户端 */
    private final AiChatClient aiChatClient;

    /** 人设数据访问层 */
    private final ScrmPersonaRepository personaRepository;

    /** JSON 序列化 / 反序列化器（解析 styleTags JSON 数组） */
    private final ObjectMapper objectMapper;

    /** AI 模型名称，默认 gpt-4o-mini */
    @Value("${scrm.ai.model:gpt-4o-mini}")
    private String model;

    /** 采样温度，默认 0.7 */
    @Value("${scrm.ai.temperature:0.7}")
    private double temperature;

    /** 生成最大 token 数，默认 500 */
    @Value("${scrm.ai.max-tokens:500}")
    private int maxTokens;

    /**
     * 根据人设与回复规则生成 AI 回复内容。
     * <p>
     * 流程：
     * <ol>
     *   <li>按 personaId 查询人设，缺失时使用默认人设</li>
     *   <li>解析 styleTags JSON 数组为逗号分隔字符串，构建系统提示词</li>
     *   <li>调用 ai-server 对话补全接口</li>
     *   <li>从响应提取首条回复；AI 不可用 / 响应空 / 触发降级时返回兜底回复</li>
     * </ol>
     *
     * @param incomingMessage 用户入站消息
     * @param personaId       人设 ID（可为空，空则使用默认人设）
     * @param replyRules      回复规则（可为空）
     * @return AI 生成的回复内容；AI 不可用时返回 {@value #FALLBACK_REPLY}
     */
    public String generateReply(String incomingMessage, String personaId, String replyRules) {
        ScrmPersonaEntity persona = loadPersona(personaId);
        String systemPrompt = buildSystemPrompt(persona, replyRules);
        log.info("生成 AI 回复: personaId={}, nickname={}, incomingMessage={}",
                personaId, persona.getNickname(), truncate(incomingMessage, 80));

        try {
            AiChatRequest request = buildRequest(systemPrompt, incomingMessage);
            AiChatResponse response = aiChatClient.chatCompletion(request);
            String content = response == null ? null : response.getFirstContent();

            // 响应为空 / 触发 Feign 降级 / 内容为空 → 返回兜底回复
            if (content == null || content.isBlank()
                    || AiChatClient.FALLBACK_CONTENT.equals(content)) {
                log.warn("AI 回复内容为空或触发降级，返回兜底回复: personaId={}", personaId);
                return FALLBACK_REPLY;
            }
            log.info("AI 回复生成成功: personaId={}, model={}, finishReason={}, reply={}",
                    personaId, response.getModel(), extractFinishReason(response), truncate(content, 80));
            return content;
        } catch (Exception e) {
            log.warn("调用 ai-server 生成回复异常，返回兜底回复: personaId={}, err={}",
                    personaId, e.getMessage());
            return FALLBACK_REPLY;
        }
    }

    /**
     * 构建 AI 对话补全请求 DTO。
     * <p>消息列表固定为两条：system 提示词 + user 入站消息。
     *
     * @param systemPrompt 系统提示词（人设 + 回复规则）
     * @param userMessage  用户入站消息
     * @return AI 对话补全请求
     */
    public AiChatRequest buildRequest(String systemPrompt, String userMessage) {
        return AiChatRequest.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .messages(List.of(
                        new AiChatRequest.AiChatMessage(ROLE_SYSTEM, systemPrompt),
                        new AiChatRequest.AiChatMessage(ROLE_USER, userMessage)
                ))
                .build();
    }

    /**
     * 加载人设：按 personaId 查询，缺失时返回默认人设实体。
     *
     * @param personaId 人设 ID（可为空）
     * @return 人设实体（不为 null）
     */
    private ScrmPersonaEntity loadPersona(String personaId) {
        if (personaId == null || personaId.isBlank()) {
            log.debug("personaId 为空，使用默认人设");
            return defaultPersona();
        }
        Optional<ScrmPersonaEntity> opt = personaRepository.findByPersonaId(personaId);
        if (opt.isEmpty()) {
            log.warn("人设不存在，使用默认人设: personaId={}", personaId);
            return defaultPersona();
        }
        return opt.get();
    }

    /**
     * 构建默认人设实体（仅填充提示词所需字段）。
     *
     * @return 默认人设实体
     */
    private ScrmPersonaEntity defaultPersona() {
        ScrmPersonaEntity persona = new ScrmPersonaEntity();
        persona.setNickname(DEFAULT_NICKNAME);
        persona.setSignature(DEFAULT_SIGNATURE);
        persona.setStyleTags(null);
        return persona;
    }

    /**
     * 构建系统提示词。
     * <p>模板：{@code 你是{name}，个性签名：{signature}，话术风格：{styleTags}。
     * 请按照以下回复规则回复用户消息：{replyRules}。回复要自然、口语化，符合人设风格。}
     *
     * @param persona    人设实体
     * @param replyRules 回复规则（可为空）
     * @return 系统提示词
     */
    private String buildSystemPrompt(ScrmPersonaEntity persona, String replyRules) {
        String nickname = nullToEmpty(persona.getNickname(), DEFAULT_NICKNAME);
        String signature = nullToEmpty(persona.getSignature(), DEFAULT_SIGNATURE);
        String styleTags = parseStyleTags(persona.getStyleTags());
        String rules = nullToEmpty(replyRules, "无特殊规则");
        return String.format(
                "你是%s，个性签名：%s，话术风格：%s。请按照以下回复规则回复用户消息：%s。回复要自然、口语化，符合人设风格。",
                nickname, signature, styleTags, rules);
    }

    /**
     * 解析 styleTags JSON 数组字符串为逗号分隔的纯文本。
     * <p>解析失败时回退为原始字符串，保证提示词可构建。
     *
     * @param styleTagsJson 话术风格标签 JSON 数组字符串（如 {@code ["热情", "幽默"]}）
     * @return 逗号分隔字符串（如 {@code 热情,幽默}）；输入为空时返回 "默认"
     */
    private String parseStyleTags(String styleTagsJson) {
        if (styleTagsJson == null || styleTagsJson.isBlank()) {
            return "默认";
        }
        try {
            List<String> tags = objectMapper.readValue(styleTagsJson, new TypeReference<List<String>>() {});
            return tags.isEmpty() ? "默认" : String.join(",", tags);
        } catch (Exception e) {
            log.warn("解析 styleTags JSON 失败，回退为原始字符串: styleTags={}, err={}",
                    styleTagsJson, e.getMessage());
            return styleTagsJson;
        }
    }

    /**
     * 空值转默认值：输入为空时返回默认值。
     *
     * @param value        原始值
     * @param defaultValue 默认值
     * @return 非空字符串
     */
    private String nullToEmpty(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    /**
     * 提取响应首条候选的结束原因（日志用）。
     *
     * @param response AI 响应
     * @return 结束原因，无法提取时返回 null
     */
    private String extractFinishReason(AiChatResponse response) {
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            return null;
        }
        AiChatResponse.AiChatChoice first = response.getChoices().get(0);
        return first == null ? null : first.getFinishReason();
    }

    /**
     * 截断字符串用于日志输出，避免日志过长。
     *
     * @param text   原始文本
     * @param maxLen 最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    /**
     * 提供给上层获取当前生效模型名（用于响应回显）。
     *
     * @return 模型名
     */
    public String getModel() {
        return model;
    }

    /**
     * 提供给上层获取当前人设 ID（默认人设时返回固定标识）。
     *
     * @param personaId 原始 personaId
     * @return 实际生效的人设 ID
     */
    public String resolvePersonaId(String personaId) {
        if (personaId == null || personaId.isBlank()) {
            return "default";
        }
        return personaId;
    }

}
