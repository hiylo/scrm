/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : QixinEnterpriseApiClient.java
 * Date : 2026/10/23 09:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.feign.dto.QixinEnterpriseProfile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 启信宝企业工商信息客户端（{@link QixinEnterpriseApi} 的 HTTP 实现）。
 * <p>
 * 传输层用 Spring {@link RestClient}（与 {@code AiChatClient} 一致, 不引入
 * spring-cloud-openfeign）。鉴权用请求头 {@code Authorization: <token>}, token
 * 仅从 {@link QixinApiConfig} 读取, <b>不写日志、不落库</b>。
 * </p>
 * <p><b>开关语义</b>（缺一即拒绝发请求, 由调用方记 PARTIAL 并保留旧快照）:
 * </p>
 * <ul>
 *   <li>{@code scrm.inspection.qixin.enabled=false}（默认）→ 抛 {@link QixinApiException}</li>
 *   <li>token 为空 → 抛 {@link QixinApiException}</li>
 *   <li>企业名称与信用代码同时为空 → 抛 {@link QixinApiException}</li>
 * </ul>
 * <p><b>响应解析</b>: 启信宝开放平台响应统一为 {@code {status/code, message, data}} 信封,
 * 不同接口的 data 内字段命名略有差异, 这里按多别名兜底解析（regStatus/businessStatus、
 * legalPerson/operName、partners/shareholderList 等）, 解析失败信息写进异常描述,
 * 待真实凭证联调时按实际字段微调（T02 已登记「先打 20 遍样例验证字段」）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
public class QixinEnterpriseApiClient implements QixinEnterpriseApi {

    /** 工商照面接口路径（启信宝 1.41, 可在联调时按实际文档调整） */
    private static final String PROFILE_PATH = "/api/v1/enterprise/profile";

    /** 经营异常接口路径（启信宝 1.55） */
    private static final String ABNORMAL_PATH = "/api/v1/enterprise/abnormal";

    /** 鉴权请求头名（启信宝凭据页签发的访问令牌） */
    private static final String AUTH_HEADER = "Authorization";

    /** 响应业务成功码集合（启信宝以 200 / "200" / "0" 表示成功） */
    private static final String[] SUCCESS_CODES = {"200", "0"};

    /** 日期输出格式 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** API 配置 */
    private final QixinApiConfig config;

    /** JSON 解析器（Spring 容器内的应用级 ObjectMapper） */
    private final ObjectMapper objectMapper;

    /** HTTP 客户端, base-url 由配置注入 */
    private final RestClient restClient;

    /**
     * 构造客户端。
     *
     * @param config       启信宝 API 配置
     * @param objectMapper JSON 解析器
     */
    public QixinEnterpriseApiClient(QixinApiConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.max(1, config.getConnectTimeoutSeconds())));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(1, config.getReadTimeoutSeconds())));
        this.restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 查询工商照面。
     *
     * @param enterpriseName 企业名称
     * @param creditCode     统一社会信用代码
     * @return 归一化工商快照
     * @throws QixinApiException 未启用 / 凭证缺失 / 调用失败 / 返回码非成功
     */
    @Override
    public QixinEnterpriseProfile queryProfile(String enterpriseName, String creditCode)
            throws QixinApiException {
        JsonNode data = call(PROFILE_PATH, enterpriseName, creditCode);
        QixinEnterpriseProfile profile = new QixinEnterpriseProfile();
        // 注意: 不能用 varargs 重载传 fallback（会被当成又一个别名），必须用数组重载
        profile.setEnterpriseName(firstText(data,
                new String[]{"name", "enterpriseName", "entName"}, enterpriseName));
        profile.setCreditCode(firstText(data,
                new String[]{"creditCode", "creditcode", "unifiedCode"}, creditCode));
        profile.setLegalPerson(firstText(data, "legalPerson", "operName", "legalPersonName", "fddbr"));
        profile.setBusinessStatus(firstText(data, "regStatus", "businessStatus", "status", "enterpriseStatus"));
        profile.setCancelDate(firstText(data, "cancelDate", "cancel_date"));
        profile.setRevokeDate(firstText(data, "revokeDate", "revoke_date"));
        profile.setAdministrativePenalty(firstText(data, "penaltyInfo", "administrativePenalty"));
        JsonNode partners = firstNode(data, "partners", "shareholderList", "shareholders");
        if (partners != null && partners.isArray()) {
            for (JsonNode partner : partners) {
                String holder = firstText(partner, "name", "stockName", "shareholderName");
                String ratio = firstText(partner, "percent", "stockPercent", "ratio");
                if (holder != null && !holder.isBlank()) {
                    profile.getShareholders().add(ratio == null ? holder : holder + "(" + ratio + ")");
                }
            }
        }
        profile.setQueryTime(LocalDateTime.now().format(DATE_FORMATTER));
        profile.setRawJson(data.toString());
        log.info("[启信宝-照面] 查询完成: creditCode={}, status={}, shareholders={}",
                maskCreditCode(profile.getCreditCode()), profile.getBusinessStatus(),
                profile.getShareholders().size());
        return profile;
    }

    /**
     * 查询经营异常名录。
     *
     * @param enterpriseName 企业名称
     * @param creditCode     统一社会信用代码
     * @return 归一化工商快照（仅经营异常项）
     * @throws QixinApiException 未启用 / 凭证缺失 / 调用失败 / 返回码非成功
     */
    @Override
    public QixinEnterpriseProfile queryAbnormal(String enterpriseName, String creditCode)
            throws QixinApiException {
        JsonNode data = call(ABNORMAL_PATH, enterpriseName, creditCode);
        QixinEnterpriseProfile profile = new QixinEnterpriseProfile();
        // 注意: 不能用 varargs 重载传 fallback（会被当成又一个别名），必须用数组重载
        profile.setEnterpriseName(firstText(data,
                new String[]{"name", "enterpriseName", "entName"}, enterpriseName));
        profile.setCreditCode(firstText(data,
                new String[]{"creditCode", "creditcode", "unifiedCode"}, creditCode));
        JsonNode list = firstNode(data, "items", "list", "abnormalList", "records");
        JsonNode first = list != null && list.isArray() && !list.isEmpty() ? list.get(0) : data;
        profile.setAbnormal(list != null && list.isArray() ? !list.isEmpty() : false);
        profile.setAbnormalReason(firstText(first, "inReason", "putReason", "abnormalReason", "reason"));
        profile.setAbnormalDate(firstText(first, "inDate", "putDate", "abnormalDate", "date"));
        profile.setQueryTime(LocalDateTime.now().format(DATE_FORMATTER));
        profile.setRawJson(data.toString());
        log.info("[启信宝-经营异常] 查询完成: creditCode={}, abnormal={}",
                maskCreditCode(profile.getCreditCode()), profile.isAbnormal());
        return profile;
    }

    /**
     * 发起一次查询并解析统一信封。
     * <p>未启用或凭证缺失时在发请求前直接失败, 避免无凭证打第三方接口。</p>
     *
     * @param path           接口路径
     * @param enterpriseName 企业名称
     * @param creditCode     统一社会信用代码
     * @return 信封中的 data 节点（缺失时回落到整个响应体）
     * @throws QixinApiException 前置校验失败 / HTTP 失败 / 业务码非成功 / 响应不可解析
     */
    private JsonNode call(String path, String enterpriseName, String creditCode) throws QixinApiException {
        requireEnabledAndCredential();
        if ((enterpriseName == null || enterpriseName.isBlank())
                && (creditCode == null || creditCode.isBlank())) {
            throw new QixinApiException("企业名称与统一社会信用代码不能同时为空", false);
        }
        String body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path);
                        if (creditCode != null && !creditCode.isBlank()) {
                            uriBuilder.queryParam("creditCode", creditCode);
                        }
                        if (enterpriseName != null && !enterpriseName.isBlank()) {
                            uriBuilder.queryParam("name", enterpriseName);
                        }
                        return uriBuilder.build();
                    })
                    .header(AUTH_HEADER, config.getToken())
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new QixinApiException("启信宝接口调用失败: path=" + path
                    + ", err=" + e.getMessage(), e, true);
        }
        if (body == null || body.isBlank()) {
            throw new QixinApiException("启信宝接口返回空响应: path=" + path, true);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new QixinApiException("启信宝响应非合法 JSON: path=" + path
                    + ", err=" + e.getMessage(), e, true);
        }
        String code = firstText(root, "status", "code", "retCode");
        if (code != null && !isSuccessCode(code)) {
            throw new QixinApiException("启信宝返回业务失败: path=" + path
                    + ", code=" + code + ", message=" + firstText(root, "message", "msg"), false);
        }
        JsonNode data = firstNode(root, "data", "result");
        return data != null ? data : root;
    }

    /**
     * 校验开关与凭证（缺一即失败, 不在无凭证状态下打第三方接口）。
     *
     * @throws QixinApiException 未启用或凭证缺失
     */
    private void requireEnabledAndCredential() throws QixinApiException {
        if (!config.isEnabled()) {
            throw new QixinApiException(
                    "启信宝工商巡检未启用 (scrm.inspection.qixin.enabled=false)", false);
        }
        if (config.getToken() == null || config.getToken().isBlank()) {
            throw new QixinApiException(
                    "启信宝访问令牌未配置 (scrm.inspection.qixin.token)", false);
        }
    }

    /**
     * 判定业务码是否为成功。
     *
     * @param code 业务码
     * @return true 表示成功
     */
    private boolean isSuccessCode(String code) {
        for (String success : SUCCESS_CODES) {
            if (success.equals(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按候选别名取第一个非空文本字段。
     *
     * @param node     节点（可空）
     * @param aliases  候选字段名
     * @param fallback 全部缺失时的兜底值
     * @return 字段文本（可能为 null）
     */
    private String firstText(JsonNode node, String... aliases) {
        return firstText(node, aliases, null);
    }

    /**
     * 按候选别名取第一个非空文本字段。
     *
     * @param node     节点（可空）
     * @param aliases  候选字段名数组
     * @param fallback 兜底值
     * @return 字段文本（可能为 null）
     */
    private String firstText(JsonNode node, String[] aliases, String fallback) {
        JsonNode found = firstNode(node, aliases);
        if (found == null || found.isNull()) {
            return fallback;
        }
        String text = found.asText();
        return text == null || text.isBlank() ? fallback : text;
    }

    /**
     * 按候选别名取第一个非空文本字段（带兜底值的重载）。
     *
     * @param node     节点
     * @param alias    字段名
     * @param fallback 兜底值
     * @return 字段文本
     */
    private String firstText(JsonNode node, String alias, String fallback) {
        return firstText(node, new String[]{alias}, fallback);
    }

    /**
     * 按候选别名取第一个存在的非空节点。
     *
     * @param node    节点（可空）
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

    /**
     * 统一社会信用代码脱敏（日志与错误信息中禁止出现完整代码）。
     *
     * @param creditCode 统一社会信用代码
     * @return 脱敏后的代码（保留前 4 位与后 4 位）
     */
    private String maskCreditCode(String creditCode) {
        if (creditCode == null || creditCode.length() <= 8) {
            return creditCode == null ? null : "****";
        }
        return creditCode.substring(0, 4) + "********" + creditCode.substring(creditCode.length() - 4);
    }
}