/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : GeTuiPushNotificationService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmUserDeviceEntity;
import org.hiylo.scrm.repository.ScrmUserDeviceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 个推 (GeTui) 推送服务实现。
 * <p>
 * 对接个推 REST API v2, 通过 {@link ScrmUserDeviceRepository} 按 userId 解析其注册的活跃
 * 个推 client_id 列表, 调用 {@code /v2/{appKey}/push/single/cid} 下发通知。
 * </p>
 *
 * <p>启用条件: {@code scrm.push.getui.enabled=true} 且配置 AppKey / MasterSecret。
 * 启用后本类注册为名为 {@code pushNotificationService} 的 Bean, {@link NoOpPushNotificationService}
 * 因同一属性的 {@code @ConditionalOnProperty} 条件不满足而自动退让; 未启用时仍由 NoOp 占位, 调用方无感知。</p>
 *
 * <p>鉴权: 调用 {@code /v2/{appKey}/auth} 获取 token (sign = SHA-256(appKey + timestamp + masterSecret)),
 * token 缓存至 expire_time, 临界过期提前 {@code token-refresh-advance-seconds} 刷新;
 * 推送遇到鉴权失败 (code=10001) 时自动重新鉴权并重试一次。</p>
 *
 * <p>注意: 当前仅实现在线推送 (push_message.notification, 应用在前台时直达)。
 * 离线厂商通道 (APNs / FCM / 厂商通道) 需在个推控制台配置证书后扩展 push_channel 字段。</p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Service("pushNotificationService")
@ConditionalOnProperty(prefix = "scrm.push.getui", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class GeTuiPushNotificationService implements PushNotificationService {

    /** 个推 REST API v2 成功状态码 */
    private static final String CODE_SUCCESS = "0";
    /** 个推鉴权失败状态码 (token 失效, 需重新获取) */
    private static final String CODE_AUTH_FAIL = "10001";
    /** 单次 push/single/cid 的 audience.cid 上限 (个推限制 1000) */
    private static final int MAX_CIDS_PER_REQUEST = 1000;
    /** 设备活跃状态值 */
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** expire_time 异常时的兜底有效期 (秒, 1 小时) */
    private static final long FALLBACK_TOKEN_TTL_SECONDS = 3600L;

    /** 用户设备数据仓库 */
    private final ScrmUserDeviceRepository userDeviceRepository;
    /** JSON 序列化器 */
    private final ObjectMapper mapper = new ObjectMapper();
    /** HTTP 客户端 */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 个推应用 AppKey */
    @Value("${scrm.push.getui.app-key:}")
    private String appKey;
    /** 个推应用 MasterSecret (支持 Jasypt ENC() 密文) */
    @Value("${scrm.push.getui.master-secret:}")
    private String masterSecret;
    /** 个推 REST API 基础地址 */
    @Value("${scrm.push.getui.base-url:https://restapi.getui.com}")
    private String baseUrl;
    /** 单次 HTTP 请求超时 (秒) */
    @Value("${scrm.push.getui.timeout-seconds:10}")
    private long timeoutSeconds;
    /** token 提前刷新安全余量 (秒, 避免临界过期导致 401) */
    @Value("${scrm.push.getui.token-refresh-advance-seconds:300}")
    private long tokenRefreshAdvanceSeconds;

    /** 鉴权 token 缓存 (并发读取用 volatile, 写入在 synchronized 块内) */
    private volatile String cachedToken;
    /** token 过期时间 (epoch 秒), 0 表示未获取 */
    private volatile long tokenExpireAtSec;

    /**
     * 向单个用户的所有活跃设备推送通知。
     *
     * @param userId  目标用户 ID
     * @param title   通知标题
     * @param content 通知内容
     * @param type    通知类型 (如 MESSAGE / TASK / ALERT)
     * @param extra   附加透传参数 (可空)
     */
    @Override
    public void pushToUser(String userId, String title, String content, String type, Map<String, Object> extra) {
        List<String> cids = activeCids(userId);
        if (cids.isEmpty()) {
            log.debug("[GeTui] 用户无活跃设备, 跳过推送: userId={}, type={}", userId, type);
            return;
        }
        pushCids(cids, title, content, type, extra);
    }

    /**
     * 向多个用户的所有活跃设备批量推送通知。
     *
     * @param userIds 目标用户 ID 列表
     * @param title   通知标题
     * @param content 通知内容
     * @param type    通知类型 (如 MESSAGE / TASK / ALERT)
     * @param extra   附加透传参数 (可空)
     */
    @Override
    public void pushToUsers(
            List<String> userIds, String title, String content, String type, Map<String, Object> extra) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<String> cids = userIds.stream()
                .flatMap(uid -> activeCids(uid).stream())
                .distinct()
                .collect(Collectors.toList());
        if (cids.isEmpty()) {
            log.debug("[GeTui] 批量推送目标用户均无活跃设备: userIds={}, type={}", userIds, type);
            return;
        }
        pushCids(cids, title, content, type, extra);
    }

    // ==================== 推送核心 ====================

    /**
     * 将通知推送到给定 client_id 列表 (按 {@link #MAX_CIDS_PER_REQUEST} 分批)。
     *
     * @param cids    目标 client_id 列表
     * @param title   通知标题
     * @param content 通知正文
     * @param type    通知类型 (透传至 payload.type)
     * @param extra   附加透传数据 (可空)
     */
    private void pushCids(List<String> cids, String title, String content, String type, Map<String, Object> extra) {
        String payload = buildPayload(type, extra);
        for (int i = 0; i < cids.size(); i += MAX_CIDS_PER_REQUEST) {
            List<String> chunk = cids.subList(i, Math.min(i + MAX_CIDS_PER_REQUEST, cids.size()));
            try {
                doPushWithRetry(chunk, title, content, payload, type);
            } catch (Exception e) {
                // 推送失败不影响业务主流程, 仅记录错误 (与 NoOp 容错语义一致)
                log.error("[GeTui] 推送失败: chunkSize={}, type={}, err={}", chunk.size(), type, e.getMessage(), e);
            }
        }
    }

    /**
     * 执行单批推送, 遇鉴权失败重新获取 token 后重试一次。
     *
     * @param cids     本批 client_id
     * @param title    通知标题
     * @param content  通知正文
     * @param payload  点击通知后的透传 payload (JSON 字符串)
     * @param type     通知类型 (仅用于日志)
     */
    private void doPushWithRetry(List<String> cids, String title, String content, String payload, String type)
            throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            String token = getToken();
            if (token == null) {
                log.error("[GeTui] 无法获取 token, 跳过推送: type={}", type);
                return;
            }
            GeTuiResponse resp = doPush(cids, title, content, payload, token);
            if (resp == null) {
                return; // 网络/解析异常已在 doPush 内记录
            }
            if (CODE_SUCCESS.equals(resp.code)) {
                log.info("[GeTui] 推送成功: cids={}, type={}, taskId={}", cids.size(), type, resp.taskId);
                return;
            }
            if (!CODE_AUTH_FAIL.equals(resp.code) || attempt != 0) {
                log.error("[GeTui] 推送返回错误: code={}, msg={}, type={}", resp.code, resp.msg, type);
                return;
            }
            log.warn("[GeTui] token 失效 (code=10001), 重新鉴权后重试: type={}", type);
            invalidateToken();
        }
        log.error("[GeTui] 重试后仍鉴权失败, 放弃: type={}", type);
    }

    /**
     * 调用个推 push/single/cid 接口下发通知。
     *
     * @param cids    目标 client_id 列表
     * @param title   通知标题
     * @param content 通知正文
     * @param payload 透传 payload
     * @param token   鉴权 token
     * @return 个推响应; 网络异常返回 null
     */
    private GeTuiResponse doPush(List<String> cids, String title, String content, String payload, String token)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("request_id", UUID.randomUUID().toString());
        body.put("audience", Map.of("cid", cids));
        // push_message.notification: 在线通知, 应用前台直达
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("title", title);
        notification.put("body", content);
        notification.put("click_type", "payload");
        notification.put("payload", payload);
        body.put("push_message", Map.of("notification", notification));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v2/" + appKey + "/push/single/cid"))
                .header("Content-Type", "application/json")
                .header("token", token)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            log.error("[GeTui] push HTTP 异常: status={}, body={}", resp.statusCode(), truncate(resp.body(), 500));
            return null;
        }
        return parseResponse(resp.body());
    }

    // ==================== 鉴权 ====================

    /**
     * 获取有效 token, 过期或未获取时重新鉴权 (双重检查锁)。
     *
     * @return 有效 token; 鉴权失败返回 null
     */
    private String getToken() {
        long now = System.currentTimeMillis() / 1000;
        if (cachedToken != null && tokenExpireAtSec - tokenRefreshAdvanceSeconds > now) {
            return cachedToken;
        }
        synchronized (this) {
            now = System.currentTimeMillis() / 1000;
            if (cachedToken != null && tokenExpireAtSec - tokenRefreshAdvanceSeconds > now) {
                return cachedToken;
            }
            return auth();
        }
    }

    /**
     * 失效缓存的 token, 下次 {@link #getToken} 触发重新鉴权。
     */
    private void invalidateToken() {
        cachedToken = null;
    }

    /**
     * 调用个推 auth 接口获取 token。
     * <p>sign = SHA-256(appKey + timestamp + masterSecret) 十六进制小写。</p>
     *
     * @return token; 失败返回 null
     */
    private String auth() {
        if (appKey.isBlank() || masterSecret.isBlank()) {
            log.error("[GeTui] AppKey 或 MasterSecret 未配置, 无法鉴权 (检查 GETUI_APP_KEY/GETUI_MASTER_SECRET)");
            return null;
        }
        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String sign = sha256Hex(appKey + timestamp + masterSecret);
            Map<String, Object> body = Map.of("sign", sign, "timestamp", timestamp);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v2/" + appKey + "/auth"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.error("[GeTui] auth HTTP 异常: status={}, body={}", resp.statusCode(), truncate(resp.body(), 500));
                return null;
            }
            JsonNode root = mapper.readTree(resp.body());
            String code = root.path("code").asText();
            if (!CODE_SUCCESS.equals(code)) {
                log.error("[GeTui] auth 失败: code={}, msg={}", code, root.path("msg").asText());
                return null;
            }
            JsonNode data = root.path("data");
            String token = data.path("token").asText();
            // expire_time 为 epoch 秒字符串; 解析失败时兜底 1 小时
            long expire = data.path("expire_time").asLong();
            if (token.isBlank()) {
                log.error("[GeTui] auth 返回 token 为空: body={}", truncate(resp.body(), 500));
                return null;
            }
            long now = System.currentTimeMillis() / 1000;
            if (expire <= now) {
                expire = now + FALLBACK_TOKEN_TTL_SECONDS;
            }
            this.cachedToken = token;
            this.tokenExpireAtSec = expire;
            log.info("[GeTui] 鉴权成功, token 有效至 epoch={} (提前 {}s 刷新)", expire, tokenRefreshAdvanceSeconds);
            return token;
        } catch (Exception e) {
            log.error("[GeTui] 鉴权异常: {}", e.getMessage(), e);
            return null;
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 查询用户的所有活跃设备 client_id。
     *
     * @param userId 用户 ID
     * @return 活跃 client_id 列表 (无设备时为空)
     */
    private List<String> activeCids(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        List<ScrmUserDeviceEntity> devices = userDeviceRepository.findByUserId(userId);
        if (devices.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> cids = new ArrayList<>(devices.size());
        for (ScrmUserDeviceEntity d : devices) {
            if (STATUS_ACTIVE.equalsIgnoreCase(d.getStatus()) && d.getClientId() != null && !d.getClientId().isBlank()) {
                cids.add(d.getClientId());
            }
        }
        return cids;
    }

    /**
     * 构造点击通知后的透传 payload (JSON 字符串, 含 type 与 extra)。
     *
     * @param type  通知类型
     * @param extra 附加透传数据 (可空)
     * @return payload JSON 字符串
     */
    private String buildPayload(String type, Map<String, Object> extra) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", type);
            if (extra != null && !extra.isEmpty()) {
                payload.put("extra", extra);
            }
            return mapper.writeValueAsString(payload);
        } catch (Exception e) {
            // 理论不会失败 (Map 序列化无外部依赖)
            log.warn("[GeTui] payload 序列化失败, 降级为简单 type: {}", e.getMessage());
            return "{\"type\":\"" + type + "\"}";
        }
    }

    /**
     * 解析个推响应, 提取 code / msg / data.taskid。
     *
     * @param body 响应体
     * @return 响应封装; 解析失败返回 null
     */
    private GeTuiResponse parseResponse(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            GeTuiResponse r = new GeTuiResponse();
            r.code = root.path("code").asText();
            r.msg = root.path("msg").asText();
            r.taskId = root.path("data").path("taskid").asText();
            return r;
        } catch (Exception e) {
            log.error("[GeTui] 响应解析失败: body={}", truncate(body, 500), e);
            return null;
        }
    }

    /**
     * SHA-256 摘要 → 十六进制小写字符串。
     *
     * @param input 原文
     * @return 64 位十六进制摘要
     */
    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 截断日志字符串, 避免刷屏。
     *
     * @param s   原始字符串
     * @param max 最大长度
     * @return 截断后的字符串
     */
    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }

    /**
     * 个推响应简单封装 (code / msg / taskid)
 * @since V1.0
     * @author Hsi Chu
     */
    private static class GeTuiResponse {
        /** 状态码, 0 表示成功 */
        String code;
        /** 提示信息 */
        String msg;
        /** 任务 ID (成功时返回) */
        String taskId;
    }
}
