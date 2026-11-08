/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmAiVoucherAssistService.java
 * Date : 2026/11/08 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.voucher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.AiProviderRegistry;
import org.hiylo.scrm.config.VisionModelProperties;
import org.hiylo.scrm.dto.ScrmAiVoucherSuggestionDto;
import org.hiylo.scrm.entity.ScrmAccountSubjectEntity;
import org.hiylo.scrm.entity.ScrmAiProviderEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.feign.dto.AiChatRequest;
import org.hiylo.scrm.repository.ScrmAccountSubjectRepository;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 智能凭证助手：票据图片 → 结构化抽取 → 凭证草稿建议。
 * <p>
 * 链路：调用方上传票据图片 + 企业 ID → 本服务选定 AI Provider 多模态通道
 * （与证照 OCR 同一视觉链路）→ 按企业科目表构建抽取提示词 → 模型返回
 * 票据字段 + 建议借贷分录 JSON → <b>确定性校验</b>后生成草稿建议：
 * </p>
 * <ul>
 *   <li>建议科目必须存在于该企业科目表（否则剔除并标记 warning）</li>
 *   <li>借贷必须平衡（否则 valid=false，提示人工核对金额）</li>
 *   <li>金额必须为正且可解析（非法金额剔除）</li>
 * </ul>
 * <p>产出仅为「草稿建议」，由会计人工确认后调既有 createVoucher 落草稿，
 * 绝不自动入账（符合代账审核纪律）。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmAiVoucherAssistService {

    /** 上传大小上限（MB） */
    private static final int MAX_FILE_MB = 20;

    /** 方向：借方 */
    private static final String DIR_DEBIT = "DEBIT";

    /** 方向：贷方 */
    private static final String DIR_CREDIT = "CREDIT";

    /** 科目状态：启用（过滤科目表用） */
    private static final String SUBJECT_STATUS_ACTIVE = "ACTIVE";

    /** 视觉调用失败后的自动重试间隔（毫秒） */
    private static final long RETRY_DELAY_MS = 300L;

    /** AI Provider 运行时路由（取多模态通道） */
    private final AiProviderRegistry providerRegistry;

    /** 企业科目数据访问 */
    private final ScrmAccountSubjectRepository subjectRepository;

    /** 视觉模型参数（超时/文件上限） */
    private final VisionModelProperties visionProperties;

    /** JSON 解析器 */
    private final ObjectMapper objectMapper;

    /**
     * 识别票据并生成凭证草稿建议。
     *
     * @param companyId 企业主体 ID
     * @param file      票据图片（发票/回单）
     * @return 凭证草稿建议
     * @throws ScrmException 文件非法 / 未配置多模态 Provider / 无法调用
     */
    public ScrmAiVoucherSuggestionDto recognizeEntry(Long companyId, MultipartFile file)
            throws ScrmException {
        if (file == null || file.isEmpty()) {
            throw ScrmException.badRequest("票据文件不能为空");
        }
        if (file.getSize() > (long) MAX_FILE_MB * 1024 * 1024) {
            throw ScrmException.badRequest("票据文件超过 " + MAX_FILE_MB + "MB 上限");
        }
        var providerOpt = providerRegistry.resolveImageProvider();
        if (providerOpt.isEmpty()) {
            throw ScrmException.badRequest(
                    "未配置可用的多模态 Provider，无法识别票据；请先在系统管理 → AI Providers 配置");
        }
        List<ScrmAccountSubjectEntity> subjects =
                subjectRepository.findByCompanyIdAndStatusOrderBySubjectCodeAsc(companyId, SUBJECT_STATUS_ACTIVE);
        if (subjects.isEmpty()) {
            throw ScrmException.badRequest("该企业还没有科目表，请先点「初始化标准科目」");
        }

        ScrmAiProviderEntity provider = providerOpt.get();
        String prompt = buildExtractPrompt(subjects);
        String raw = callVisionWithRetry(companyId, file, prompt, provider);
        return buildSuggestion(raw, subjects, provider);
    }

    /**
     * 调用视觉模型，失败/空返回时自动重试一次。
     * <p>
     * 网关偶发抖动（半开连接被 RST、reasoning 吃光 token 导致正文为空）时，
     * 隔 {@link #RETRY_DELAY_MS} 毫秒重试一次即可恢复；两次都失败才报错。
     * 与 {@code LocalVisionOcrClient#doRecognize} 的重试策略保持一致。
     * </p>
     *
     * @param companyId 企业主体 ID（日志用）
     * @param file      票据图片
     * @param prompt    抽取提示词
     * @param provider  多模态 Provider
     * @return 模型返回正文（保证非空白）
     * @throws ScrmException 两次调用均失败
     */
    private String callVisionWithRetry(Long companyId, MultipartFile file, String prompt,
                                       ScrmAiProviderEntity provider) throws ScrmException {
        String lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String raw = callVision(file, prompt, provider);
                if (raw != null && !raw.isBlank()) {
                    return raw;
                }
                lastError = "模型返回空内容";
                log.warn("[AI凭证] 票据识别第 {} 次返回空内容: companyId={}", attempt, companyId);
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("[AI凭证] 票据识别第 {} 次调用失败: companyId={}, err={}",
                        attempt, companyId, e.getMessage());
            }
            if (attempt == 1) {
                sleepQuietly();
            }
        }
        throw ScrmException.badRequest("票据识别调用失败: " + lastError);
    }

    /** 重试前短暂等待（不响应中断之外的任何异常） */
    private static void sleepQuietly() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 解析模型返回 JSON 并做确定性校验生成建议。
     * <p>（纯函数，供单元测试直接调用，不依赖真实 AI。）</p>
     *
     * @param raw      模型返回 JSON 文本
     * @param subjects 企业科目表
     * @param provider Provider（仅用于警示文案）
     * @return 草稿建议
     */
    ScrmAiVoucherSuggestionDto buildSuggestion(String raw, List<ScrmAccountSubjectEntity> subjects,
                                                ScrmAiProviderEntity provider) {
        // 纯函数语义：注入的 mapper 可为 null（单测直接构造），此处兜底新建
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                objectMapper != null ? objectMapper : new com.fasterxml.jackson.databind.ObjectMapper();
        ScrmAiVoucherSuggestionDto dto = new ScrmAiVoucherSuggestionDto();
        dto.setWarnings(new ArrayList<>());
        Map<String, String> subjectNameByCode = new HashMap<>();
        for (ScrmAccountSubjectEntity s : subjects) {
            if (s.getSubjectCode() != null) {
                subjectNameByCode.put(s.getSubjectCode().trim(), s.getSubjectName());
            }
        }
        try {
            JsonNode root = mapper.readTree(raw);
            if (root == null || root.isMissingNode()) {
                dto.setValid(false);
                dto.getWarnings().add("AI 返回内容无法解析");
                return dto;
            }
            dto.setBillType(text(root, "bill_type", "billType"));
            dto.setBillNo(text(root, "bill_no", "billNo"));
            dto.setBillDate(text(root, "bill_date", "billDate"));
            dto.setCounterparty(text(root, "counterparty"));
            dto.setSummary(text(root, "summary"));
            dto.setAmount(amount(root.path("amount")));

            List<ScrmAiVoucherSuggestionDto.SuggestionEntry> validEntries = new ArrayList<>();
            JsonNode rawEntries = root.path("entries");
            for (JsonNode e : rawEntries) {
                String code = text(e, "subject_code", "subjectCode");
                String direction = text(e, "direction").toUpperCase();
                BigDecimal amt = amount(e.path("amount"));
                String name = code == null ? null : subjectNameByCode.get(code.trim());
                if (code == null || name == null) {
                    dto.getWarnings().add("AI 建议科目「" + (code == null ? "?" : code)
                            + "」不在企业科目表，已忽略");
                    continue;
                }
                if (amt.signum() <= 0) {
                    dto.getWarnings().add("AI 建议金额非法，已忽略: " + code);
                    continue;
                }
                validEntries.add(ScrmAiVoucherSuggestionDto.SuggestionEntry.builder()
                        .subjectCode(code.trim())
                        .subjectName(name)
                        .direction(DIR_CREDIT.equals(direction) ? DIR_CREDIT : DIR_DEBIT)
                        .amount(amt)
                        .summary(text(e, "summary"))
                        .build());
            }
            dto.setEntries(validEntries);
            BigDecimal debit = total(validEntries, DIR_DEBIT);
            BigDecimal credit = total(validEntries, DIR_CREDIT);
            dto.setValid(!validEntries.isEmpty() && debit.compareTo(credit) == 0);
            if (dto.isValid() && !dto.getWarnings().isEmpty()) {
                // 有部分科目被忽略但剩余分录平衡 → 仍算有效草稿，带警示
            }
            if (validEntries.isEmpty()) {
                dto.getWarnings().add("未能生成有效分录（科目/金额不合法）");
            } else if (!dto.isValid()) {
                dto.getWarnings().add("借贷不平衡，请人工核对金额后修正");
            }
            return dto;
        } catch (Exception e) {
            log.warn("[AI凭证] 建议解析失败: err={}", e.getMessage());
            dto.setValid(false);
            dto.getWarnings().add("AI 返回内容解析失败，请重试");
            return dto;
        }
    }

    /**
     * 按企业科目表构建抽取提示词（把可用科目注入上下文，让模型只在这些科目里选）。
     */
    private String buildExtractPrompt(List<ScrmAccountSubjectEntity> subjects) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是记账会计。识别这张票据（发票/银行回单/收据），严格输出单个 JSON 对象，")
                .append("不要输出多余文字、不要用 Markdown 代码块。字段：")
                .append("bill_type(票据类型), bill_no(号码), bill_date(YYYY-MM-DD), ")
                .append("counterparty(对方名称), amount(含税金额数字), summary(一句话摘要), ")
                .append("entries(借贷分录数组，每项 subject_code/subject_name/direction(DEBIT或CREDIT)/amount/summary)。")
                .append("借贷必须平衡。看不清或没有的字段输出空字符串，绝对不得编造金额。")
                .append("只能从以下企业科目中选 subject_code: ");
        for (ScrmAccountSubjectEntity s : subjects) {
            sb.append(s.getSubjectCode()).append(' ').append(s.getSubjectName()).append("; ");
        }
        return sb.toString();
    }

    /**
     * 调用 AI Provider 多模态通道（OpenAI 兼容视觉格式，同证照 OCR 链路）。
     */
    private String callVision(MultipartFile file, String prompt, ScrmAiProviderEntity provider)
            throws Exception {
        byte[] bytes = file.getBytes();
        String mime = detectMime(file.getOriginalFilename(), file.getContentType());
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        Map<String, Object> textPart = Map.of("type", "text", "text", prompt);
        Map<String, Object> imagePart = Map.of("type", "image_url",
                "image_url", Map.of("url", dataUrl));
        Map<String, Object> message = Map.of("role", "user",
                "content", List.of(textPart, imagePart));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", provider.getDefaultModel());
        body.put("messages", List.of(message));
        body.put("temperature", 0.1);
        body.put("max_tokens", visionProperties.getMaxTokens());
        body.put("enable_thinking", false);

        String base = trimSlash(provider.getApiBaseUrl());
        String path = provider.getChatPath() == null || provider.getChatPath().isBlank()
                ? "/v1/chat/completions" : provider.getChatPath().trim();
        // 显式连接工厂 + 超时：默认工厂会复用连接池里的 keep-alive 连接，若网关侧已关闭
        // （半开连接），向旧连接写数据会被 RST，表现为毫秒级 "I/O error on POST ... : null"。
        // 与 LocalVisionOcrClient#buildRestClient 保持一致（该写法已验证可稳定调用同一网关）。
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.max(1, visionProperties.getConnectTimeoutSeconds())));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(1, visionProperties.getReadTimeoutSeconds())));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(base)
                .requestFactory(factory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        String apiKey = provider.getApiKey();
        // 网关需要鉴权时带 Bearer；本地无 key 的端点不能发空的 "Bearer "（会被判非法凭据）
        if (apiKey != null && !apiKey.isBlank()) {
            builder = builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        RestClient client = builder.build();
        String raw = client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        JsonNode root = objectMapper.readTree(raw == null ? "{}" : raw);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        return content.isMissingNode() || content.isNull() ? "" : content.asText();
    }

    /** 取 JSON 文本字段（多别名兜底） */
    private static String text(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        for (String k : keys) {
            JsonNode v = node.path(k);
            if (!v.isMissingNode() && !v.isNull()) {
                String s = v.asText("").trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }

    /** 解析金额（容忍数字字符串；非法返回 0） */
    private static BigDecimal amount(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return BigDecimal.ZERO;
        }
        String s = node.asText("").trim().replace(",", "");
        if (s.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /** 按方向汇总金额 */
    private static BigDecimal total(List<ScrmAiVoucherSuggestionDto.SuggestionEntry> entries,
                                    String direction) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ScrmAiVoucherSuggestionDto.SuggestionEntry e : entries) {
            if (direction.equals(e.getDirection()) && e.getAmount() != null) {
                sum = sum.add(e.getAmount());
            }
        }
        return sum;
    }

    /** 探测图片 MIME */
    private static String detectMime(String filename, String fallback) {
        String n = filename == null ? "" : filename.toLowerCase();
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (n.endsWith(".webp")) {
            return "image/webp";
        }
        return (fallback != null && !fallback.isBlank()) ? fallback : "image/jpeg";
    }

    /** 去除 URL 尾部斜杠 */
    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        String s = url.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}