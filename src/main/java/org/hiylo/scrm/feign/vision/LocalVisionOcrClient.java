/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : LocalVisionOcrClient.java
 * Date : 2026/10/23 18:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.VisionModelProperties;
import org.hiylo.scrm.entity.ScrmAiProviderEntity;
import org.hiylo.scrm.feign.OcrApiException;
import org.hiylo.scrm.feign.OcrRecognizer;
import org.hiylo.scrm.feign.dto.ocr.OcrField;
import org.hiylo.scrm.feign.dto.ocr.OcrRecognizeResponse;
import org.hiylo.scrm.feign.dto.ocr.OcrVerification;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 本地视觉大模型证照识别实现（数据不出内网）。
 * <p>
 * 调用本地 llama-server 的 OpenAI 兼容视觉接口（{@code /v1/chat/completions},
 * content 为 text + image_url 数组）, 把证照图片直接抽成结构化字段 JSON; 再用
 * {@link LicenseFieldValidator} 做确定性校验（身份证校验位 / 跨字段交叉 / 信用代码
 * 校验）, 把问题写进字段 {@code problems} 并置 {@code status=invalid}, 驱动
 * 证照域转人工复核。
 * </p>
 * <p>
 * <b>为什么选本地</b>：身份证等属个人敏感数据, 云端视觉模型会把图片传出内网;
 * 本地 4B 档实测（4060Ti 8G）识别中文证照约 1~4 秒、干净件字段全对, 退化件
 * 由校验层拦截——隐私与准确率兼得。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
public class LocalVisionOcrClient implements OcrRecognizer {

    /** 识别类型：营业执照 */
    private static final String TYPE_LICENSE = "license";

    /** 识别类型：身份证 */
    private static final String TYPE_IDCARD = "idcard";

    /** 识别类型：开户许可证 */
    private static final String TYPE_ACCOUNT_LICENSE = "account_license";

    /** 字段状态：正常 */
    private static final String STATUS_OK = "ok";

    /** 字段状态：缺失 */
    private static final String STATUS_MISSING = "missing";

    /** 字段状态：校验不通过 */
    private static final String STATUS_INVALID = "invalid";

    /** 营业执照字段：中文键 → 标准化 key */
    private static final Map<String, String> LICENSE_KEYS = orderedMap(
            "企业名称", "enterprise_name",
            "统一社会信用代码", "credit_code",
            "类型", "company_type",
            "法定代表人", "legal_representative",
            "注册资本", "registered_capital",
            "成立日期", "establish_date",
            "营业期限", "business_term",
            "住所", "address",
            "经营范围", "business_scope");

    /** 身份证字段：中文键 → 标准化 key */
    private static final Map<String, String> IDCARD_KEYS = orderedMap(
            "姓名", "name",
            "性别", "gender",
            "民族", "ethnicity",
            "出生", "birth_date",
            "住址", "address",
            "公民身份号码", "id_number",
            "签发机关", "issuing_authority",
            "有效期限", "valid_period");

    /** 开户许可证字段：中文键 → 标准化 key */
    private static final Map<String, String> ACCOUNT_KEYS = orderedMap(
            "账户名称", "account_name",
            "账号", "account_number",
            "开户行", "bank_name");

    /** 营业执照必填字段 */
    private static final List<String> LICENSE_REQUIRED =
            List.of("企业名称", "统一社会信用代码", "法定代表人");

    /** 身份证正面必填字段 */
    private static final List<String> IDCARD_FRONT_REQUIRED =
            List.of("姓名", "公民身份号码");

    /** 身份证反面必填字段 */
    private static final List<String> IDCARD_BACK_REQUIRED = List.of("签发机关");

    /** 开户许可证必填字段 */
    private static final List<String> ACCOUNT_REQUIRED =
            List.of("账户名称", "账号", "开户行");

    /** 视觉模型配置 */
    private final VisionModelProperties properties;

    /** 进程管理器（managed 模式下按需拉起） */
    private final VisionModelManager modelManager;

    /** JSON 解析器 */
    private final ObjectMapper objectMapper;

    /** HTTP 客户端（环境变量路径的 RestClient，与老构造器同一实例） */
    private final RestClient restClient;

    /**
     * 视觉调用目标端点参数（两种来源：环境变量 properties 或 AI Provider 实体）。
     */
    private static final class VisionEndpoint {
        /** 基础地址 */
        final String baseUrl;
        /** 对话补全路径 */
        final String chatPath;
        /** 模型名 */
        final String model;
        /** 是否关闭思考（推理型模型必须关闭，否则 reasoning 吃光 token） */
        final boolean enableThinking;
        /** 温度 */
        final double temperature;
        /** 最大 token 数 */
        final int maxTokens;
        /** API 密钥（可空） */
        final String apiKey;
        /** 引擎标识（响应中展示，如 local-vision:<model>） */
        final String engine;
        /** 设备标识 */
        final String device;

        VisionEndpoint(String baseUrl, String chatPath, String model,
                       boolean enableThinking, double temperature, int maxTokens,
                       String apiKey, String engine, String device) {
            this.baseUrl = baseUrl;
            this.chatPath = chatPath;
            this.model = model;
            this.enableThinking = enableThinking;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
            this.apiKey = apiKey;
            this.engine = engine;
            this.device = device;
        }
    }

    /**
     * 构造本地视觉识别客户端。
     *
     * @param properties   视觉模型配置
     * @param modelManager 进程管理器
     * @param objectMapper JSON 解析器
     */
    public LocalVisionOcrClient(VisionModelProperties properties,
                                VisionModelManager modelManager,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.modelManager = modelManager;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(1, properties.getReadTimeoutSeconds())));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        // 指向 OpenAI 兼容网关（Magpie / LiteLLM）时需要鉴权；本地 llama-server 无 key 则不加
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder = builder.defaultHeader("Authorization", "Bearer " + properties.getApiKey());
        }
        this.restClient = builder.build();
    }

    /**
     * 识别证照图片（环境变量配置路径：scrm.vision.*）。
     *
     * @param file      证照图片
     * @param type      识别类型
     * @param requestId 链路标识（可空）
     * @return 统一识别响应
     * @throws OcrApiException 未启用 / 文件非法 / 模型不可用 / 调用失败
     */
    @Override
    public OcrRecognizeResponse recognize(MultipartFile file, String type, String requestId)
            throws OcrApiException {
        if (!properties.isEnabled()) {
            throw new OcrApiException("本地视觉识别未启用（scrm.vision.enabled=false）", false);
        }
        if (file == null || file.isEmpty()) {
            throw new OcrApiException("识别文件不能为空", false);
        }
        if (file.getSize() > (long) properties.getMaxFileSizeMb() * 1024 * 1024) {
            throw new OcrApiException("文件超过 " + properties.getMaxFileSizeMb() + "MB 上限", false);
        }
        String normalizedType = normalizeType(type);
        String reqId = (reqId0(requestId, "vision"));
        VisionEndpoint endpoint = new VisionEndpoint(
                properties.getBaseUrl(), properties.getChatPath(), properties.getModel(),
                properties.isEnableThinking(), properties.getTemperature(), properties.getMaxTokens(),
                properties.getApiKey(), "local-vision:" + properties.getModel(), "local");
        try {
            modelManager.ensureAvailable();
        } catch (VisionModelException e) {
            throw new OcrApiException("视觉模型不可用: " + e.getMessage(), false);
        }
        return doRecognize(file, normalizedType, reqId, endpoint);
    }

    /**
     * 识别证照图片（AI Provider 路由路径：系统管理配置的多模态 provider）。
     * <p>
     * Provider 体系下证照 OCR 由 {@link OcrRecognizerRouter} 按能力（image）
     * 路由到默认多模态 provider（如 Magpie 网关 group/sensenova-pool），
     * 不再依赖环境变量 scrm.vision.*。图片经内网网关识别，不占本机 GPU。
     * </p>
     *
     * @param file      证照图片
     * @param type      识别类型
     * @param requestId 链路标识（可空）
     * @param provider  多模态 AI Provider 实体
     * @return 统一识别响应
     * @throws OcrApiException 参数非法 / 调用失败
     */
    public OcrRecognizeResponse recognizeWithProvider(MultipartFile file, String type,
                                                      String requestId,
                                                      ScrmAiProviderEntity provider)
            throws OcrApiException {
        if (file == null || file.isEmpty()) {
            throw new OcrApiException("识别文件不能为空", false);
        }
        String normalizedType = normalizeType(type);
        String reqId = reqId0(requestId, "provider");
        int maxMb = properties.getMaxFileSizeMb();
        if (file.getSize() > (long) maxMb * 1024 * 1024) {
            throw new OcrApiException("文件超过 " + maxMb + "MB 上限", false);
        }
        String model = provider.getDefaultModel() == null || provider.getDefaultModel().isBlank()
                ? properties.getModel() : provider.getDefaultModel().trim();
        // 证照抽取用视觉专用参数（低温度降幻觉 + 大 max_tokens 防 reasoning 吃光正文），
        // 不沿用 provider 的通用对话参数（温度 0.7 过高、maxTokens 500 对 9 字段 JSON 太小）
        double temperature = properties.getTemperature();
        int maxTokens = properties.getMaxTokens();
        boolean enableThinking = false; // 证照抽取一律关思考，避免 reasoning 吃光 max_tokens
        VisionEndpoint endpoint = new VisionEndpoint(
                trimSlash(provider.getApiBaseUrl()),
                provider.getChatPath() == null || provider.getChatPath().isBlank()
                        ? "/v1/chat/completions" : provider.getChatPath().trim(),
                model, enableThinking, temperature, maxTokens,
                provider.getApiKey(), "local-vision:" + model, "local");
        return doRecognize(file, normalizedType, reqId, endpoint);
    }

    /**
     * 生成链路标识。
     *
     * @param requestId 调用方传入的标识（可空）
     * @param prefix    缺省标识前缀
     * @return 非空标识
     */
    private static String reqId0(String requestId, String prefix) {
        return (requestId == null || requestId.isBlank())
                ? prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                : requestId;
    }

    /**
     * 去除 URL 尾部斜杠。
     *
     * @param url 原始地址
     * @return 规范化地址
     */
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

    /**
     * 核心识别流程（调用视觉模型 + 字段解析 + 确定性校验）。
     *
     * @param file       图片文件
     * @param type       规范化识别类型
     * @param requestId  链路标识
     * @param endpoint   目标端点参数
     * @return 统一识别响应
     * @throws OcrApiException 调用失败
     */
    private OcrRecognizeResponse doRecognize(MultipartFile file, String type, String requestId,
                                             VisionEndpoint endpoint) throws OcrApiException {
        long start = System.currentTimeMillis();
        String rawContent;
        try {
            rawContent = callVision(file, type, endpoint);
            // 网关偶发返回空正文（reasoning 吃光 max_tokens / 抖动）：重试一次，
            // 避免直接把「字段全空 + 强制人工复核」的劣质结果落库
            Map<String, String> firstPass = parseFields(rawContent, type);
            if (firstPass.isEmpty() || firstPass.values().stream().allMatch(String::isBlank)) {
                log.warn("[视觉OCR] 首轮结果字段全空(正文={}字符), 重试一次: type={}",
                        rawContent == null ? 0 : rawContent.length(), type);
                long retryStart = System.currentTimeMillis();
                rawContent = callVision(file, type, endpoint);
                log.warn("[视觉OCR] 重试完成: 耗时={}ms", System.currentTimeMillis() - retryStart);
            }
        } catch (Exception e) {
            log.warn("[视觉OCR] 调用失败: type={}, err={}", type, e.getMessage());
            throw new OcrApiException("视觉模型调用失败: " + e.getMessage(), false);
        }
        long elapsed = System.currentTimeMillis() - start;
        Map<String, String> values = parseFields(rawContent, type);
        return buildResponse(requestId, type, values, rawContent, elapsed, endpoint);
    }

    /**
     * 调用视觉模型（OpenAI 兼容视觉格式）。
     *
     * @param file     图片
     * @param type     识别类型
     * @param endpoint 目标端点参数
     * @return 模型返回的正文
     * @throws Exception 读取文件或请求失败
     */
    private String callVision(MultipartFile file, String type, VisionEndpoint endpoint)
            throws Exception {
        byte[] bytes = file.getBytes();
        String mime = detectMime(file.getOriginalFilename(), file.getContentType());
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);

        Map<String, Object> textPart = Map.of("type", "text", "text", buildPrompt(type));
        Map<String, Object> imagePart = Map.of("type", "image_url",
                "image_url", Map.of("url", dataUrl));
        Map<String, Object> message = Map.of("role", "user",
                "content", List.of(textPart, imagePart));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", endpoint.model);
        body.put("messages", List.of(message));
        body.put("temperature", endpoint.temperature);
        body.put("max_tokens", endpoint.maxTokens);
        // 推理型模型（sensenova / deepseek 等）必须显式关闭思考，否则 reasoning 吃光 max_tokens、
        // 正文返回空。llama-server 靠启动参数 --reasoning off，网关靠这个请求字段。
        if (!endpoint.enableThinking) {
            body.put("enable_thinking", false);
        }

        RestClient client = buildRestClient(endpoint);
        String raw = client.post()
                .uri(endpoint.chatPath)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        JsonNode root = objectMapper.readTree(raw == null ? "{}" : raw);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        return content.isMissingNode() || content.isNull() ? "" : content.asText();
    }

    /**
     * 按目标端点构建 RestClient（每次调用新建，端点参数来自 provider 动态配置）。
     *
     * @param endpoint 端点参数
     * @return RestClient
     */
    private RestClient buildRestClient(VisionEndpoint endpoint) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(1, properties.getReadTimeoutSeconds())));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(endpoint.baseUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if (endpoint.apiKey != null && !endpoint.apiKey.isBlank()) {
            builder = builder.defaultHeader("Authorization", "Bearer " + endpoint.apiKey);
        }
        return builder.build();
    }

    /**
     * 按识别类型构造抽取提示词。
     *
     * @param type 识别类型
     * @return 提示词
     */
    private String buildPrompt(String type) {
        String common = "严格输出单个 JSON 对象，不要输出任何多余文字、不要用 Markdown 代码块；"
                + "逐字抄写，看不清或没有的字段输出空字符串，绝对不得猜测或编造数字。";
        switch (type) {
            case TYPE_IDCARD:
                return "识别这张中国居民身份证，判断是人像面还是国徽面。"
                        + "若为人像面，输出字段：姓名、性别、民族、出生、住址、公民身份号码；"
                        + "若为国徽面，输出字段：签发机关、有效期限。" + common;
            case TYPE_ACCOUNT_LICENSE:
                return "识别这张银行开户许可证，输出字段：账户名称、账号、开户行。" + common;
            case TYPE_LICENSE:
            default:
                return "识别这张营业执照，输出字段：企业名称、统一社会信用代码、类型、法定代表人、"
                        + "注册资本、成立日期、营业期限、住所、经营范围。" + common;
        }
    }

    /**
     * 解析模型输出的字段 JSON。
     *
     * @param raw  模型原始输出
     * @param type 识别类型
     * @return 中文字段键 → 值（保序）
     */
    private Map<String, String> parseFields(String raw, String type) {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, String> known = keysOf(type);
        known.keySet().forEach(k -> result.put(k, ""));
        if (raw == null || raw.isBlank()) {
            return result;
        }
        String json = stripFence(raw);
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.warn("[视觉OCR] 模型输出无 JSON 对象, head={}", truncate(raw, 120));
            return result;
        }
        try {
            JsonNode node = objectMapper.readTree(json.substring(start, end + 1));
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (known.containsKey(key)) {
                    JsonNode v = entry.getValue();
                    result.put(key, v == null || v.isNull() ? "" : v.asText("").trim());
                }
            });
        } catch (Exception e) {
            log.warn("[视觉OCR] JSON 解析失败: err={}, head={}", e.getMessage(), truncate(json, 120));
        }
        return result;
    }

    /**
     * 组装统一识别响应（字段 + 校验 + 复核问题）。
     *
     * @param requestId 链路标识
     * @param type      识别类型
     * @param values    识别值
     * @param raw       模型原始输出
     * @param elapsedMs 耗时
     * @param endpoint  目标端点参数（engine/device 取自于此）
     * @return 统一响应
     */
    private OcrRecognizeResponse buildResponse(String requestId, String type,
                                               Map<String, String> values,
                                               String raw, long elapsedMs,
                                               VisionEndpoint endpoint) {
        OcrRecognizeResponse response = new OcrRecognizeResponse();
        response.setRequestId(requestId);
        response.setOk(true);
        response.setMode("vl");
        response.setEngine(endpoint.engine);
        response.setDevice(endpoint.device);
        response.setElapsedMs(elapsedMs);
        response.setType(type);
        response.setText(raw);

        boolean idcardBack = TYPE_IDCARD.equals(type)
                && (hasText(values.get("签发机关")) || hasText(values.get("有效期限")))
                && !hasText(values.get("公民身份号码"));
        response.setDocType(TYPE_IDCARD.equals(type) ? (idcardBack ? "idcard_back" : "idcard_front") : type);

        Map<String, List<String>> problems = validate(type, values, idcardBack);
        List<String> required = requiredOf(type, idcardBack);
        Map<String, String> keys = keysOf(type);

        List<OcrField> fields = new ArrayList<>();
        values.forEach((cnKey, value) -> {
            OcrField field = new OcrField();
            field.setKey(keys.get(cnKey));
            field.setName(cnKey);
            field.setValue(hasText(value) ? value : null);
            field.setSource("vl");
            field.setRequired(required.contains(cnKey));
            field.setConfidence(hasText(value) ? 0.9 : 0.0);
            List<String> fieldProblems = problems.getOrDefault(cnKey, List.of());
            field.setProblems(new ArrayList<>(fieldProblems));
            if (!fieldProblems.isEmpty()) {
                field.setStatus(STATUS_INVALID);
                field.setVerified(false);
            } else if (!hasText(value)) {
                field.setStatus(STATUS_MISSING);
                field.setVerified(false);
            } else {
                field.setStatus(STATUS_OK);
                field.setVerified(true);
            }
            fields.add(field);
        });
        response.setFields(fields);

        OcrVerification verification = new OcrVerification();
        List<String> allProblems = new ArrayList<>();
        problems.forEach((k, list) -> list.forEach(p -> allProblems.add(k + "：" + p)));
        verification.setProblems(allProblems);
        verification.setChecked(values.size());
        verification.setOk(allProblems.isEmpty());
        response.setVerification(verification);

        if (!allProblems.isEmpty()) {
            response.setWarnings(new ArrayList<>(List.of(
                    "存在校验不通过的字段，已转人工复核（共 " + allProblems.size() + " 项）")));
        }
        log.info("[视觉OCR] 完成: type={}, docType={}, 字段={}, 问题={}, 耗时={}ms",
                type, response.getDocType(), values.size(), allProblems.size(), elapsedMs);
        return response;
    }

    /**
     * 按类型执行确定性校验。
     *
     * @param type       识别类型
     * @param values     识别值
     * @param idcardBack 是否身份证反面
     * @return 字段键 → 问题清单
     */
    private Map<String, List<String>> validate(String type, Map<String, String> values,
                                               boolean idcardBack) {
        switch (type) {
            case TYPE_IDCARD:
                return idcardBack
                        ? validateIdCardBack(values)
                        : LicenseFieldValidator.validateIdCard(values);
            case TYPE_ACCOUNT_LICENSE:
                return LicenseFieldValidator.validateAccountLicense(values);
            case TYPE_LICENSE:
            default:
                return LicenseFieldValidator.validateBusinessLicense(values);
        }
    }

    /**
     * 身份证反面校验（有效期区间格式）。
     *
     * @param values 识别值
     * @return 问题清单
     */
    private Map<String, List<String>> validateIdCardBack(Map<String, String> values) {
        Map<String, List<String>> problems = new LinkedHashMap<>();
        if (!hasText(values.get("签发机关"))) {
            problems.computeIfAbsent("签发机关", k -> new ArrayList<>())
                    .add("必填项未识别（图片可能模糊/遮挡）");
        }
        String valid = values.get("有效期限");
        if (!hasText(valid)) {
            problems.computeIfAbsent("有效期限", k -> new ArrayList<>()).add("未识别到有效期限");
        } else if (!LicenseFieldValidator.isValidDateRange(valid)) {
            problems.computeIfAbsent("有效期限", k -> new ArrayList<>())
                    .add("有效期格式无法解析：" + valid);
        }
        return problems;
    }

    /**
     * 取识别类型对应的字段键表。
     *
     * @param type 识别类型
     * @return 中文字段键 → 标准 key
     */
    private Map<String, String> keysOf(String type) {
        switch (type) {
            case TYPE_IDCARD:
                return IDCARD_KEYS;
            case TYPE_ACCOUNT_LICENSE:
                return ACCOUNT_KEYS;
            case TYPE_LICENSE:
            default:
                return LICENSE_KEYS;
        }
    }

    /**
     * 取识别类型的必填字段。
     *
     * @param type       识别类型
     * @param idcardBack 是否身份证反面
     * @return 必填中文字段键
     */
    private List<String> requiredOf(String type, boolean idcardBack) {
        switch (type) {
            case TYPE_IDCARD:
                return idcardBack ? IDCARD_BACK_REQUIRED : IDCARD_FRONT_REQUIRED;
            case TYPE_ACCOUNT_LICENSE:
                return ACCOUNT_REQUIRED;
            case TYPE_LICENSE:
            default:
                return LICENSE_REQUIRED;
        }
    }

    /**
     * 规范化识别类型。
     *
     * @param type 原始类型
     * @return 规范化类型
     */
    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return TYPE_LICENSE;
        }
        String t = type.trim().toLowerCase(Locale.ROOT);
        switch (t) {
            case TYPE_IDCARD:
            case "id_card":
            case "identity":
                return TYPE_IDCARD;
            case TYPE_ACCOUNT_LICENSE:
            case "bank":
                return TYPE_ACCOUNT_LICENSE;
            case TYPE_LICENSE:
            case "business_license":
                return TYPE_LICENSE;
            default:
                return TYPE_LICENSE;
        }
    }

    /**
     * 推断图片 MIME 类型。
     *
     * @param fileName    文件名
     * @param contentType 上传声明的类型
     * @return MIME
     */
    private String detectMime(String fileName, String contentType) {
        if (contentType != null && contentType.startsWith("image/")) {
            return contentType;
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "image/png";
    }

    /**
     * 去除 Markdown 代码围栏。
     *
     * @param raw 原始文本
     * @return 去围栏文本
     */
    private String stripFence(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return s.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return s;
    }

    /**
     * 非空判断。
     *
     * @param s 文本
     * @return 是否有内容
     */
    private boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /**
     * 截断文本（日志用）。
     *
     * @param s   文本
     * @param max 最大长度
     * @return 截断后文本
     */
    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /**
     * 构造保序 Map（静态字面量用）。
     *
     * @param pairs 交替的键值
     * @return 保序 Map（字段展示顺序与证照版式一致）
     */
    private static Map<String, String> orderedMap(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
