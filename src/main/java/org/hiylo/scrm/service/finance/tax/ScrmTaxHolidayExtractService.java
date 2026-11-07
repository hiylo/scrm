/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxHolidayExtractService.java
 * Date : 2026/10/27 14:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.tax;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmTaxHolidayDraftEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.feign.AiChatClient;
import org.hiylo.scrm.feign.dto.AiChatRequest;
import org.hiylo.scrm.feign.dto.AiChatResponse;
import org.hiylo.scrm.repository.ScrmTaxHolidayDraftRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 假期 AI 抽取服务。
 * <p>
 * 交给模型的只有<b>公告原文纯文本</b>，模型负责把它转成结构化 JSON；
 * 抓取动作（访问 gov.cn）由调用方完成——模型本身不能联网。
 * </p>
 * <p>
 * <b>产出只落草稿，绝不直接生效</b>：假期直接决定申报截止日与逾期判定，
 * 属国家级硬事实。模型可能编造不存在的假期、算错区间、漏掉调休补班日，
 * 因此必须由人工核对公告原文后确认。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmTaxHolidayExtractService {

    /** AI 调用最多尝试次数（含首次）。只有可恢复错误才重试 */
    private static final int AI_MAX_ATTEMPTS = 3;

    /** 首次重试前的等待（毫秒），之后按倍数递增 */
    private static final long AI_RETRY_BASE_DELAY_MS = 2_000L;

    /** 公告原文送给模型的最大长度（超出部分截断，避免超出上下文） */
    private static final int MAX_SOURCE_CHARS = 6000;

    /** 抽取结果的 JSON 解析器 */
    private final ObjectMapper objectMapper;

    /** AI 对话客户端（复用既有 scrm.ai.* 通道） */
    private final AiChatClient aiChatClient;

    /** 草稿数据访问层 */
    private final ScrmTaxHolidayDraftRepository draftRepository;

    /**
     * 从公告原文中抽取假期与调休安排，落草稿。
     *
     * @param year        目标年份
     * @param sourceTitle 公告标题
     * @param sourceUrl   公告 URL
     * @param sourceText  公告原文纯文本
     * @return 生成的草稿（状态恒为 PENDING）
     * @throws ScrmException 参数非法 / AI 未启用 / 抽取结果无法解析
     */
    public ScrmTaxHolidayDraftEntity extract(int year, String sourceTitle, String sourceUrl,
                                             String sourceText) throws ScrmException {
        if (sourceText == null || sourceText.isBlank()) {
            throw ScrmException.badRequest("公告原文不能为空：模型只能解析你提供的文本，不能自行联网抓取");
        }
        String trimmed = sourceText.length() > MAX_SOURCE_CHARS
                ? sourceText.substring(0, MAX_SOURCE_CHARS) : sourceText;

        AiChatRequest request = new AiChatRequest();
        request.setTemperature(0.0);
        // 抽取任务输出较长（7 个假期 + 若干补班日），给足 token
        request.setMaxTokens(1500);
        request.setEnableThinking(false);
        List<AiChatRequest.AiChatMessage> messages = new ArrayList<>();
        messages.add(message("system", buildSystemPrompt(year)));
        messages.add(message("user", trimmed));
        request.setMessages(messages);

        AiChatResponse response = callAiWithRetry(request);
        String content = response.getFirstContent();

        Map<String, Object> parsed;
        try {
            parsed = parseModelJson(content);
        } catch (Exception e) {
            log.warn("[假期-AI] 模型输出无法解析为 JSON: err={}", e.getMessage());
            throw ScrmException.badRequest("AI 返回的内容不是合法 JSON，无法解析（原文已保存供排查）");
        }

        // 拒绝空结果：模型面对「没有假期信息」的输入会返回合法但空的 {"holidays":[],"workdays":[]}，
        // 这种草稿一旦被采纳会清空该年份现有假期数据——比没有草稿危险得多，必须拦在入库前
        int holidayCount = countItems(parsed.get("holidays"));
        int workdayCount = countItems(parsed.get("workdays"));
        if (holidayCount == 0) {
            log.warn("[假期-AI] 模型未抽到任何假期, 拒绝生成草稿: year={}, workdays={}", year, workdayCount);
            throw ScrmException.badRequest(
                    "AI 未从公告中抽到任何假期（公告可能尚未发布或正文不含安排），"
                            + "已拒绝生成空草稿，请稍后重试或手工录入");
        }

        ScrmTaxHolidayDraftEntity draft = new ScrmTaxHolidayDraftEntity();
        draft.setHolidayYear(year);
        draft.setSourceTitle(sourceTitle);
        draft.setSourceUrl(sourceUrl);
        draft.setSourceText(trimmed);
        draft.setRawModelOutput(content);
        draft.setHolidaysJson(toJson(parsed.get("holidays")));
        draft.setWorkdaysJson(toJson(parsed.get("workdays")));
        draft.setModelName(response == null ? null : response.getModel());
        draft.setExtractMode(ScrmTaxHolidayDraftEntity.MODE_AI);
        draft.setDraftStatus(ScrmTaxHolidayDraftEntity.STATUS_PENDING);
        draft = draftRepository.save(draft);
        log.info("[假期-AI] 抽取完成并落草稿: id={}, year={}, holidays={}, workdays={}",
                draft.getId(), year, countItems(parsed.get("holidays")), countItems(parsed.get("workdays")));
        return draft;
    }

    /**
     * 调用 AI，遇到可恢复错误时重试。
     * <p>
     * <b>只重试可恢复的失败</b>（模型超时、连不上、余额不足、返回空）：这些多半是临时故障，
     * 过几秒重试有真实概率成功。参数类问题（原文为空、输出非 JSON）重试结果一样，
     * 白白拖长等待、还会把配额浪费掉，因此直接失败。
     * </p>
     * <p>
     * 全部尝试失败后才抛异常，由上层转人工——一次失败就打扰人没有必要。
     * </p>
     *
     * @param request AI 请求
     * @return 成功的模型响应（非空, 且首条内容为有效正文）
     * @throws ScrmException 重试耗尽仍失败
     */
    private AiChatResponse callAiWithRetry(AiChatRequest request) throws ScrmException {
        for (int attempt = 1; attempt <= AI_MAX_ATTEMPTS; attempt++) {
            AiChatResponse response = aiChatClient.chatCompletion(request);
            String content = response == null ? null : response.getFirstContent();
            boolean ok = content != null && !content.isBlank()
                    && !AiChatClient.FALLBACK_CONTENT.equals(content);
            if (ok) {
                return response;
            }
            if (attempt == AI_MAX_ATTEMPTS) {
                log.error("[假期-AI] 抽取失败且已重试 {} 次, 转人工介入", AI_MAX_ATTEMPTS);
                throw ScrmException.badRequest(
                        "AI 服务连续 " + AI_MAX_ATTEMPTS + " 次不可用（模型调不通或余额不足），"
                                + "请检查 scrm.ai.* 配置后重试，或改为手工录入");
            }
            long delay = AI_RETRY_BASE_DELAY_MS * attempt;
            log.warn("[假期-AI] 第 {}/{} 次调用失败, {}ms 后重试",
                    attempt, AI_MAX_ATTEMPTS, delay);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw ScrmException.badRequest("AI 抽取被中断");
            }
        }
        // 不会走到这里：循环内要么返回、要么抛异常
        throw ScrmException.badRequest("AI 服务不可用");
    }

    /**
     * 构造抽取提示词。
     * <p>
     * 明确要求：只从给定文本抽取、不补全、不推测；输出严格 JSON。
     * 「宁可少抽也不要编造」是核心约束——编造一个假期会让截止日算错。
     * </p>
     *
     * @param year 目标年份
     * @return 系统提示词
     */
    private String buildSystemPrompt(int year) {
        return "你是中国法定节假日数据抽取器。任务：从用户提供的国务院办公厅公告原文中，"
                + "抽取 " + year + " 年的全部法定节假日放假安排与调休上班日。\n"
                + "严格规则：\n"
                + "1. 只使用原文中明确出现的信息，绝不推测、绝不补全、绝不编造。\n"
                + "2. 原文未提及的假期或调休，直接省略，不要用常识填充。\n"
                + "3. 日期统一格式 YYYY-MM-DD，必须带年份。\n"
                + "4. 放假区间用起止日表示且包含首尾两天；原文写「X月X日至X月X日」就照此转换。\n"
                + "5. 调休上班日即原文中「X月X日（星期六）上班」这类日期。\n"
                + "6. 只输出 JSON，不要 markdown 代码块，不要任何解释文字。\n"
                + "输出格式：\n"
                + "{\"holidays\":[{\"name\":\"国庆节\",\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-08\"}],"
                + "\"workdays\":[{\"workDate\":\"2026-10-10\",\"remark\":\"国庆调休上班\"}]}";
    }

    /**
     * 解析模型输出为 Map。
     * <p>
     * 模型可能不听话地包上 ```json 代码块或前后加解释，这里做容错剥离：
     * 取第一个 '{' 到最后一个 '}' 之间的内容。
     * </p>
     *
     * @param content 模型输出
     * @return 解析结果
     * @throws Exception JSON 非法
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseModelJson(String content) throws Exception {
        String json = content.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("输出中没有 JSON 对象");
        }
        json = json.substring(start, end + 1);
        return objectMapper.readValue(json, Map.class);
    }

    /**
     * 任意对象转 JSON 字符串。
     *
     * @param value 待序列化对象
     * @return JSON 字符串，失败返回 "[]"
     */
    private String toJson(Object value) {
        if (value == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[假期-AI] 明细序列化失败: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * 统计数组元素个数。
     *
     * @param value 数组
     * @return 元素个数
     */
    private int countItems(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        if (value instanceof JsonNode node && node.isArray()) {
            return node.size();
        }
        return 0;
    }

    /**
     * 构造一条对话消息。
     *
     * @param role    角色
     * @param content 内容
     * @return 消息
     */
    private AiChatRequest.AiChatMessage message(String role, String content) {
        AiChatRequest.AiChatMessage msg = new AiChatRequest.AiChatMessage();
        msg.setRole(role);
        msg.setContent(content);
        return msg;
    }
}
