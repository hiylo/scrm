/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkCallbackController.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmPlatformConfigEntity;
import org.hiylo.scrm.repository.ScrmPlatformConfigRepository;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.wework.WeworkCallbackCrypto;
import org.hiylo.scrm.wework.WeworkCallbackEventDto;
import org.hiylo.scrm.wework.WeworkCallbackEventService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 企业微信回调 Controller
 * <p>
 * 接收企业微信推送的回调验证与事件通知。回调端点:
 * <ul>
 *   <li>{@code GET /scrm/wework/callback} - 验证 URL 有效性 (企业微信配置回调时触发)</li>
 *   <li>{@code POST /scrm/wework/callback} - 接收事件/数据变更通知 (通讯录变更 / 外部联系人变更 / 会话审计等)</li>
 * </ul>
 * 回调 URL 不经过网关鉴权, 由企业微信直接推送; 签名验证代替鉴权保证请求来源可信。
 * </p>
 * <p>
 * 企微平台配置从 scrm_platform_config 表按 platformType = "wework" 读取,
 * 配置缺失时返回 403。
 * </p>
 * <p>
 * 支持 IP 白名单校验：通过 {@code scrm.wework.callback-allowed-ips} 配置合法来源 IP，
 * 未配置时跳过校验（方便开发环境），配置后仅允许白名单内 IP 访问回调端点。
 * </p>
 * <p>
 * 平台配置 (Token / EncodingAESKey / CorpID) 从 {@code scrm_platform_config} 表按
 * platformType = "wework" 读取。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/wework/callback")
@RequiredArgsConstructor
public class WeworkCallbackController {

    /** 企微平台类型标识 */
    private static final String PLATFORM_TYPE_WEWORK = "wework";

    /** 事件类型: 外部联系人变更 */
    private static final String EVENT_CHANGE_EXTERNAL_CONTACT = "change_external_contact";

    /** 事件类型: 内部通讯录变更 */
    private static final String EVENT_CHANGE_CONTACT = "change_contact";

    /** 事件类型: 进入会话 */
    private static final String EVENT_ENTER_CHAT = "enter_chat";

    /** 事件类型: 消息审计 */
    private static final String EVENT_MSG_AUDIT = "msg_audit";

    /** XML 中 &lt;Encrypt&gt; 元素提取正则 */
    private static final Pattern ENCRYPT_PATTERN = Pattern.compile("<Encrypt><!\\[CDATA\\[(.*?)]]></Encrypt>");

    /** XML 中 &lt;ChangeType&gt; 或 &lt;Event&gt; 元素提取正则 */
    private static final Pattern CHANGE_TYPE_PATTERN = Pattern.compile(
            "<ChangeType><!\\[CDATA\\[(.*?)]]></ChangeType>");

    /** XML 中 &lt;Event&gt; 元素提取正则 */
    private static final Pattern EVENT_PATTERN = Pattern.compile("<Event><!\\[CDATA\\[(.*?)]]></Event>");

    /** XML 中 &lt;UserID&gt; 或 &lt;UserId&gt; 元素提取正则 */
    private static final Pattern USER_ID_PATTERN = Pattern.compile("<User[Ii]d><!\\[CDATA\\[(.*?)]]></User[Ii]d>");

    /** XML 中 &lt;ExternalUserID&gt; 元素提取正则 */
    private static final Pattern EXTERNAL_USER_ID_PATTERN = Pattern.compile(
            "<ExternalUserID><!\\[CDATA\\[(.*?)]]></ExternalUserID>");

    /** XML 中 &lt;Department&gt; 或 &lt;DeptId&gt; 元素提取正则 */
    private static final Pattern DEPARTMENT_ID_PATTERN = Pattern.compile(
            "<(?:Department|DeptId)><!\\[CDATA\\[(.*?)]]></(?:Department|DeptId)>");

    /** XML 中 &lt;ChatId&gt; 元素提取正则 */
    private static final Pattern CHAT_ID_PATTERN = Pattern.compile("<ChatId><!\\[CDATA\\[(.*?)]]></ChatId>");

    /** XML 中 &lt;TimeStamp&gt; 元素提取正则 */
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("<TimeStamp><!\\[CDATA\\[(.*?)]]></TimeStamp>");

    /** 平台配置数据访问层 */
    private final ScrmPlatformConfigRepository platformConfigRepository;

    /** 回调事件处理服务 */
    private final WeworkCallbackEventService callbackEventService;

    /** 回调 IP 白名单（逗号分隔），为空则跳过 IP 校验 */
    @Value("${scrm.wework.callback-allowed-ips:}")
    private String callbackAllowedIps;

    /** 可信反向代理 IP 列表（逗号分隔）；请求来源为可信代理时才信任 X-Forwarded-For / X-Real-IP */
    @Value("${scrm.wework.trusted-proxy-ips:}")
    private String trustedProxyIps;

    /**
     * 验证 URL 回调 (GET)
     * <p>
     * 企业微信配置回调 URL 时会发送 GET 请求验证, 参数包含 msg_signature / timestamp / nonce / echostr。
     * 验签通过后, 对 echostr 做 AES 解密并返回明文 (plain text, 非 JSON), 企业微信以此确认回调 URL 可达。
     * </p>
     *
     * @param msgSignature  消息签名
     * @param timestamp     时间戳
     * @param nonce         随机数
     * @param echostr       加密的验证字符串
     * @param request       HTTP 请求（用于 IP 白名单校验）
     * @return 解密后的 echostr 明文 (text/plain), 验签失败返回 403
     */
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60, message = "企微回调验证过于频繁，请稍后重试")
    @GetMapping
    public ResponseEntity<String> verifyCallback(
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestParam("echostr") String echostr,
            HttpServletRequest request) {
        log.info("收到企业微信回调验证: timestamp={}, nonce={}", timestamp, nonce);

        // IP 白名单校验
        if (!isAllowedIp(request)) {
            log.warn("企业微信回调验证 IP 不在白名单: remoteAddr={}", getClientIp(request));
            return ResponseEntity.status(403).body("forbidden");
        }

        // 加载平台配置
        Optional<ScrmPlatformConfigEntity> configOpt = loadPlatformConfig();
        if (!configOpt.isPresent()) {
            log.warn("企业微信平台配置未找到, 拒绝回调验证:");
            return ResponseEntity.status(403).body("forbidden");
        }

        ScrmPlatformConfigEntity config = configOpt.get();
        String token = config.getToken();
        String aesKey = config.getAesKey();
        String corpId = config.getCorpId();

        // 签名验证: SHA1(sort([token, timestamp, nonce, echostr])) == msg_signature
        boolean valid = WeworkCallbackCrypto.verifySignature(token, timestamp, nonce, echostr, msgSignature);
        if (!valid) {
            log.warn("企业微信回调验证签名失败: msg_signature={}", msgSignature);
            return ResponseEntity.status(403).body("forbidden");
        }

        // 解密 echostr 并返回明文
        try {
            String decrypted = WeworkCallbackCrypto.decryptMessage(aesKey, corpId, echostr);
            log.info("企业微信回调验证成功, 返回解密 echostr:");
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(decrypted);
        } catch (IllegalArgumentException e) {
            log.error("企业微信回调验证解密失败: errorType=IllegalArgumentException, echostr={}, error={}", truncate(echostr, 100), e.getMessage(), e);
            return ResponseEntity.status(500).body("internal server error: decryption failed");
        } catch (Exception e) {
            log.error("企业微信回调验证解密未知异常: errorType={}, error={}",
                    e.getClass().getName(), e.getMessage(), e);
            return ResponseEntity.status(500).body("internal server error: decryption failed");
        }
    }

    /**
     * 接收事件/数据变更回调 (POST)
     * <p>
     * 企业微信以 POST 推送事件通知, 请求体为 XML 格式, 包含加密的 &lt;Encrypt&gt; 元素。
     * 处理流程:
     * <ol>
     *   <li>从 XML 提取 Encrypt 密文</li>
     *   <li>验签: SHA1(sort([token, timestamp, nonce, encrypt])) == msg_signature</li>
     *   <li>AES 解密消息体</li>
     *   <li>解析解密后的 XML, 提取 Event / ChangeType 等字段</li>
     *   <li>根据事件类型分发到对应处理方法</li>
     * </ol>
     * 企业微信要求回调返回 "success" 纯文本。
     * </p>
     *
     * @param msgSignature  消息签名
     * @param timestamp     时间戳
     * @param nonce         随机数
     * @param xmlBody       请求体 XML
     * @param request       HTTP 请求（用于 IP 白名单校验）
     * @return "success" 纯文本 (text/plain), 验签/解密失败返回 403
     */
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60, message = "企微事件回调过于频繁，请稍后重试")
    @PostMapping(consumes = MediaType.TEXT_XML_VALUE)
    public ResponseEntity<String> onCallback(
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestBody String xmlBody,
            HttpServletRequest request) {
        log.info("收到企业微信事件回调: timestamp={}, nonce={}", timestamp, nonce);

        // IP 白名单校验
        if (!isAllowedIp(request)) {
            log.warn("企业微信事件回调 IP 不在白名单: remoteAddr={}", getClientIp(request));
            return ResponseEntity.status(403).body("forbidden");
        }

        // 加载平台配置
        if (xmlBody == null || xmlBody.isBlank()) {
            log.warn("企业微信回调请求体为空");
            return ResponseEntity.badRequest().body("bad request: empty body");
        }

        Optional<ScrmPlatformConfigEntity> configOpt = loadPlatformConfig();
        if (!configOpt.isPresent()) {
            log.warn("企业微信平台配置未找到, 拒绝事件回调:");
            return ResponseEntity.status(403).body("forbidden");
        }

        ScrmPlatformConfigEntity config = configOpt.get();
        String token = config.getToken();
        String aesKey = config.getAesKey();
        String corpId = config.getCorpId();

        // 提取 <Encrypt> 元素
        String encrypt;
        try {
            encrypt = extractXmlValue(xmlBody, ENCRYPT_PATTERN);
        } catch (Exception e) {
            log.error("企业微信回调 XML 解析失败: rawXml={}, error={}", truncate(xmlBody, 500), e.getMessage(), e);
            return ResponseEntity.status(500).body("internal server error: malformed xml");
        }
        if (encrypt == null || encrypt.isBlank()) {
            log.warn("企业微信回调 XML 缺少 Encrypt 元素: rawXml={}", truncate(xmlBody, 500));
            return ResponseEntity.badRequest().body("bad request: missing encrypt element");
        }

        // 签名验证
        boolean valid = WeworkCallbackCrypto.verifySignature(token, timestamp, nonce, encrypt, msgSignature);
        if (!valid) {
            log.warn("企业微信事件回调签名验证失败: msg_signature={}", msgSignature);
            return ResponseEntity.status(403).body("forbidden");
        }

        // 解密消息体
        String decryptedXml;
        try {
            decryptedXml = WeworkCallbackCrypto.decryptMessage(aesKey, corpId, encrypt);
        } catch (IllegalArgumentException e) {
            log.error("企业微信回调消息解密失败: encrypt={}, errorType=IllegalArgumentException, error={}",
                    truncate(encrypt, 100), e.getMessage(), e);
            return ResponseEntity.badRequest().body("bad request: decryption failed");
        } catch (Exception e) {
            log.error("企业微信回调消息解密未知异常: encrypt={}, errorType={}, error={}",
                    truncate(encrypt, 100), e.getClass().getName(), e.getMessage(), e);
            return ResponseEntity.status(500).body("internal server error: decryption failed");
        }

        log.info("企业微信回调消息解密成功:, xml={}", truncate(decryptedXml, 500));

        // 解析事件并分发
        WeworkCallbackEventDto event = parseEvent(decryptedXml);
        dispatchEvent(event);

        // 企业微信要求返回 "success" 纯文本
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body("success");
    }

    // ==================== 内部方法 ====================

    /**
     * 加载企业微信平台配置
     * <p>
     * 从 scrm_platform_config 按 platformType = "wework" 查询。
     * </p>
     *
     * @return 平台配置 (可能为空)
     */
    private Optional<ScrmPlatformConfigEntity> loadPlatformConfig() {
        Optional<ScrmPlatformConfigEntity> config = platformConfigRepository
                .findByPlatformType(PLATFORM_TYPE_WEWORK);
        if (!config.isPresent()) {
            log.warn("未找到企业微信平台配置:");
        }
        return config;
    }

    /**
     * IP 白名单校验
     * <p>
     * 如果白名单未配置（空字符串），跳过校验（开发环境友好）。
     * 如果白名单已配置，检查请求 IP 是否在白名单内。
     * 支持逗号分隔的 IP 列表。
     * </p>
     *
     * @param request HTTP 请求
     * @return true=允许，false=拒绝
     */
    private boolean isAllowedIp(HttpServletRequest request) {
        if (callbackAllowedIps == null || callbackAllowedIps.isBlank()) {
            // 白名单未配置，跳过校验（开发环境）
            return true;
        }

        String clientIp = getClientIp(request);
        if (clientIp == null || clientIp.isBlank()) {
            log.warn("无法获取请求 IP，拒绝访问");
            return false;
        }

        List<String> allowedIps = parseIpList(callbackAllowedIps);
        boolean allowed = allowedIps.contains(clientIp);
        if (!allowed) {
            log.warn("IP 不在白名单: clientIp={}, allowedIps={}", clientIp, allowedIps);
        }
        return allowed;
    }

    /**
     * 获取客户端真实 IP
     * <p>
     * 默认直接使用 {@link HttpServletRequest#getRemoteAddr()} (TCP 对端地址, 不可伪造)。
     * 仅当请求来源 IP 命中配置的可信反向代理列表 ({@code scrm.wework.trusted-proxy-ips}) 时,
     * 才信任 X-Forwarded-For 的最右/最后一个值 (由可信代理追加的原始客户端 IP) 或 X-Real-IP,
     * 避免用户直接伪造转发头绕过 IP 白名单。
     * </p>
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        List<String> trustedProxies = parseIpList(trustedProxyIps);
        if (!trustedProxies.isEmpty() && trustedProxies.contains(remoteAddr)) {
            // 请求来自可信反向代理, X-Forwarded-For 取最右/最后一个值 (由代理追加)
            String forwardedIp = lastForwardedIp(request.getHeader("X-Forwarded-For"));
            if (forwardedIp != null && !forwardedIp.isBlank()) {
                return forwardedIp;
            }
            // 部分代理仅使用 X-Real-IP (同样仅在可信代理场景下信任)
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank() && !"unknown".equalsIgnoreCase(realIp)) {
                return realIp.trim();
            }
        }
        return remoteAddr;
    }

    /**
     * 从 X-Forwarded-For 提取最右/最后一个有效 IP。
     * <p>
     * X-Forwarded-For 格式为 {@code client, proxy1, trustedProxy}, 由每个转发节点追加入口地址,
     * 取值应从最右侧扫描跳过末位可信代理, 取最后一个非空且非 unknown 的值。
     * </p>
     *
     * @param xff X-Forwarded-For 头值 (可空)
     * @return 原始客户端 IP, 无有效值时返回 null
     */
    private String lastForwardedIp(String xff) {
        if (xff == null || xff.isBlank()) {
            return null;
        }
        String[] parts = xff.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String ip = parts[i].trim();
            if (!ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }
        }
        return null;
    }

    /**
     * 解析逗号分隔的 IP 白名单
     *
     * @param ipListStr 逗号分隔的 IP 字符串
     * @return IP 列表
     */
    private List<String> parseIpList(String ipListStr) {
        List<String> ips = new ArrayList<>();
        for (String ip : ipListStr.split(",")) {
            String trimmed = ip.trim();
            if (!trimmed.isEmpty()) {
                ips.add(trimmed);
            }
        }
        return ips;
    }

    /**
     * 从解密后的 XML 解析事件数据
     *
     * @param xml 解密后的 XML 字符串
     * @return 回调事件 DTO
     */
    private WeworkCallbackEventDto parseEvent(String xml) {
        WeworkCallbackEventDto dto = new WeworkCallbackEventDto();
        dto.setRawXml(xml);

        // 优先取 ChangeType (外部联系人/通讯录变更), 其次取 Event (会话审计)
        String changeType = extractXmlValue(xml, CHANGE_TYPE_PATTERN);
        String event = extractXmlValue(xml, EVENT_PATTERN);
        dto.setChangeType(changeType);
        dto.setEventType(changeType != null ? changeType : event);

        dto.setUserId(extractXmlValue(xml, USER_ID_PATTERN));
        dto.setExternalUserId(extractXmlValue(xml, EXTERNAL_USER_ID_PATTERN));
        dto.setDepartmentId(extractXmlValue(xml, DEPARTMENT_ID_PATTERN));
        dto.setChatId(extractXmlValue(xml, CHAT_ID_PATTERN));
        dto.setTimestamp(extractXmlValue(xml, TIMESTAMP_PATTERN));

        return dto;
    }

    /**
     * 根据事件类型分发到对应处理方法
     *
     * @param event 回调事件 DTO
     */
    private void dispatchEvent(WeworkCallbackEventDto event) {
        String eventType = event.getEventType();
        if (eventType == null) {
            log.warn("企业微信回调事件类型为空, 忽略: rawXml={}", truncate(event.getRawXml(), 200));
            return;
        }

        try {
            if (eventType.startsWith(EVENT_CHANGE_EXTERNAL_CONTACT)) {
                callbackEventService.handleExternalContactChange(event);
            } else if (eventType.startsWith(EVENT_CHANGE_CONTACT)) {
                callbackEventService.handleContactChange(event);
            } else if (EVENT_ENTER_CHAT.equals(eventType) || EVENT_MSG_AUDIT.equals(eventType)) {
                callbackEventService.handleChatAudit(event);
            } else {
                log.info("企业微信回调事件类型未处理: eventType={}", eventType);
            }
        } catch (Exception e) {
            // 事件处理异常不阻断回调返回, 企业微信期望返回 success
            log.warn("企业微信回调事件处理异常 (不影响回调返回): eventType={}, err={}",
                    eventType, e.getMessage(), e);
        }
    }

    /**
     * 从 XML 中提取指定正则匹配的第一个 CDATA 值
     *
     * @param xml     XML 字符串
     * @param pattern 编译好的正则 Pattern
     * @return 匹配值 (可能为 null)
     */
    private String extractXmlValue(String xml, Pattern pattern) {
        Matcher matcher = pattern.matcher(xml);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 截断字符串用于日志输出, 避免日志过长刷屏
     *
     * @param text   原始文本
     * @param maxLen 最大长度
     * @return 截断后的文本 (超长时追加 "..."), 输入为 null 时返回 null
     */
    private String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
