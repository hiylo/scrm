/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AgentHubWebSocketHandler.java
 * Date : 2026/10/08 11:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmAccountDto;
import org.hiylo.scrm.dto.ScrmConversationMessageDto;
import org.hiylo.scrm.dto.callback.ConversationEventCallbackDto;
import org.hiylo.scrm.service.ConversationMediaService;
import org.hiylo.scrm.service.OutboundEnqueuedEvent;
import org.hiylo.scrm.service.ScrmAccountService;
import org.hiylo.scrm.service.ScrmAutoReplyService;
import org.hiylo.scrm.service.ScrmConversationMessageService;
import org.hiylo.scrm.service.ScrmOutboundMessageService;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 远程 Agent 集线器（AgentHub）WebSocket 处理器。
 * <p>
 * 桌面执行端（wx-console，Windows + 微信）在 NAT 后，主动出站建立 {@code ws(s)://host:port/agent}
 * 长连，服务端复用该连接接收事件、下发指令。协议见 {@code wx-console/docs/REMOTE-AGENT.md}
 * §2（出站 WSS / 心跳 / 退避重连）与 §3（JSON 帧：hello / welcome / close / ping / pong /
 * event / 指令应答）。
 * </p>
 * <p>
 * 本处理器是 AgentHub 的<b>接收侧</b>：把桌面端上报的个微会话/消息落进 SCRM 自己的库，
 * 从而与企微（wework）合并在同一个 SCRM 会话列表。落库复用
 * {@link ScrmConversationMessageService#saveMessageFromCallback}，与 REST 回调
 * {@code /scrm/callback/conversation-event} 完全同一条链路（自动建档账号/客户/会话 +
 * 按 {@code platformMessageId} 幂等去重）。
 * </p>
 * <ul>
 *   <li>握手：首帧 {@code hello}（device_id/version/token/capabilities）→ 校验 token
 *       （= {@code scrm.callback.agent-secret}）→ {@code welcome}；校验失败 {@code close(401)}。</li>
 *   <li>心跳：客户端 {@code ping} → 服务端 {@code pong}；{@value #IDLE_TIMEOUT_MS}ms
 *       无消息主动断开（协议 §2.1）。</li>
 *   <li>事件：{@code wechat.new_message} 单条新消息、{@code sessions.snapshot} 全量历史回填，
 *       统一映射为 {@link ConversationEventCallbackDto} 后落库。</li>
 *   <li>指令应答：桌面端回填 {@code {id, ok, result|error}}，当 id 形如
 *       {@code ob-<outboundId>} 时回写出站队列状态（SENT / FAILED，失败走指数退避重试）。</li>
 *   <li>实时代发：监听 {@link OutboundEnqueuedEvent}（AFTER_COMMIT），对在线设备
 *       推送 {@code wechat.send_text} 指令，与轮询 {@code /scrm/callback/outbound/pending}
 *       共用「PENDING→IN_PROGRESS」抢占语义防双发；设备离线时消息保持 PENDING 由
 *       轮询兜底或设备上线后追补。</li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentHubWebSocketHandler extends TextWebSocketHandler {

    /** Session 属性键：设备 ID（hello 帧上报） */
    public static final String ATTR_DEVICE_ID = "agentDeviceId";

    /** Session 属性键：设备登录微信账号（hello 帧 account，作为 SCRM 平台账号标识） */
    public static final String ATTR_ACCOUNT = "agentAccount";

    /** Session 属性键：是否已通过 hello 握手 */
    public static final String ATTR_AUTHORIZED = "agentAuthorized";

    /** 帧类型：握手 */
    private static final String TYPE_HELLO = "hello";

    /** 帧类型：心跳 */
    private static final String TYPE_PING = "ping";

    /** 帧类型：事件 */
    private static final String TYPE_EVENT = "event";

    /** 事件名：新消息 */
    private static final String EVENT_NEW_MESSAGE = "wechat.new_message";

    /** 事件名：会话/消息快照（welcome 后桌面端自动上报一次全量） */
    private static final String EVENT_SESSIONS_SNAPSHOT = "sessions.snapshot";

    /** SCRM 平台类型：个人微信 */
    private static final String PLATFORM_WECHAT_PERSONAL = "wechat_personal";

    /** 指令方法名：发送文本（wx-console CommandRouter 白名单能力） */
    private static final String METHOD_SEND_TEXT = "wechat.send_text";

    /** 指令方法名：发送图片（wx-console CommandRouter 白名单能力，剪贴板粘贴路线） */
    private static final String METHOD_SEND_IMAGE = "wechat.send_image";

    /** 指令方法名：发送文件（wx-console CommandRouter 白名单能力，剪贴板粘贴路线） */
    private static final String METHOD_SEND_FILE = "wechat.send_file";

    /** 消息类型：图片 */
    private static final String MESSAGE_TYPE_IMAGE = "IMAGE";

    /** 消息类型：文件 */
    private static final String MESSAGE_TYPE_FILE = "FILE";

    /** 实时代发指令 ID 前缀：{@code ob-<outboundId>}，应答帧据此回填出站队列 */
    private static final String COMMAND_ID_PREFIX = "ob-";

    /** 60s 无消息主动断开（协议 §2.1） */
    private static final long IDLE_TIMEOUT_MS = 60_000L;

    /** 会话/消息快照单次最大处理消息数（防御超大帧） */
    private static final int SNAPSHOT_MAX_MESSAGES = 2000;

    /** 文本正文截断上限 */
    private static final int MAX_TEXT_LENGTH = 2000;

    /** 按 deviceId → WebSocketSession 的设备注册表（线程安全） */
    private final ConcurrentMap<String, WebSocketSession> devices = new ConcurrentHashMap<>();

    /** 设备最后活动时间戳（毫秒） */
    private final ConcurrentMap<String, Long> lastActive = new ConcurrentHashMap<>();

    /** JSON 序列化器（Spring Boot 自动注入） */
    private final ObjectMapper objectMapper;

    /** 会话消息服务（落库链路，与 REST 回调同源） */
    private final ScrmConversationMessageService conversationMessageService;

    /** 出站消息队列服务（实时代发抢占 + 应答回执） */
    private final ScrmOutboundMessageService outboundMessageService;

    /** 账号服务（账号 ID → 平台账号 UID，匹配在线设备） */
    private final ScrmAccountService accountService;

    /** 自动回复服务（实时入站新消息命中规则后进入出站链路） */
    private final ScrmAutoReplyService autoReplyService;

    /** 会话媒体存储服务（媒体出站消息生成预签名下载 URL 下发执行侧） */
    private final ConversationMediaService conversationMediaService;

    /** 客户数据访问层（好友上报幂等建档；非 final：测试可直接 new 不注入） */
    @Autowired
    private ScrmCustomerRepository customerRepository;

    /** 回调共享密钥，亦作为 AgentHub 握手令牌（fail-closed：为空则拒绝所有连接） */
    @Value("${scrm.callback.agent-secret:}")
    private String agentSecret;

    /**
     * 连接建立：仅登记会话，等待首帧 hello 握手。
     *
     * @param session 新建立的 WebSocket 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("AgentHub 新连接: sessionId={}, 待握手", session.getId());
    }

    /**
     * 处理文本帧：首帧 hello 握手，之后由已认证会话处理心跳/事件/指令应答。
     *
     * @param session 客户端会话
     * @param message 文本消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String raw = message.getPayload();
        if (raw == null || raw.isBlank()) {
            return;
        }
        Map<String, Object> frame;
        try {
            frame = parseFrame(raw);
        } catch (Exception e) {
            log.warn("AgentHub 帧解析失败: sessionId={}, err={}", session.getId(), e.getMessage());
            return;
        }
        if (frame == null) {
            return;
        }
        String type = str(frame.get("type"));
        if (TYPE_HELLO.equals(type)) {
            onHello(session, asMap(frame.get("data")));
            return;
        }
        if (!Boolean.TRUE.equals(session.getAttributes().get(ATTR_AUTHORIZED))) {
            sendClose(session, 401, "unauthorized: hello required");
            closeQuietly(session);
            return;
        }
        String deviceId = str(session.getAttributes().get(ATTR_DEVICE_ID));
        touch(deviceId);
        if (TYPE_PING.equals(type)) {
            sendFrame(session, Map.of("type", "pong"));
            return;
        }
        if (TYPE_EVENT.equals(type)) {
            onEvent(session, deviceId, frame);
            return;
        }
        Object id = frame.get("id");
        if (id != null) {
            onCommandReply(deviceId, id.toString(), frame);
        }
    }

    /**
     * 连接关闭：从注册表移除并记录日志。
     *
     * @param session 关闭的会话
     * @param status  关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String deviceId = str(session.getAttributes().get(ATTR_DEVICE_ID));
        if (deviceId != null) {
            devices.remove(deviceId, session);
            lastActive.remove(deviceId);
        }
        log.info("AgentHub 连接关闭: sessionId={}, deviceId={}, status={}", session.getId(), deviceId, status);
    }

    /**
     * 处理 hello 握手：校验令牌，通过后回 welcome，并把旧连接踢下线（单设备单连接）。
     *
     * @param session   WebSocket 会话
     * @param helloData hello 帧 data
     */
    private void onHello(WebSocketSession session, Map<String, Object> helloData) {
        String deviceId = str(helloData.get("device_id"));
        String token = str(helloData.get("token"));
        String account = str(helloData.get("account"));
        if (deviceId == null || deviceId.isBlank() || token == null || token.isBlank()) {
            sendClose(session, 400, "bad hello: device_id/token required");
            closeQuietly(session);
            return;
        }
        if (agentSecret == null || agentSecret.isBlank() || !secureEquals(agentSecret, token)) {
            log.warn("AgentHub 握手校验失败: deviceId={}, 令牌无效", deviceId);
            sendClose(session, 401, "bad token");
            closeQuietly(session);
            return;
        }
        // 账号缺省降级为 deviceId（与 onEvent 的 accountKey 一致），避免 attributes 存 null 触发 NPE
        if (account == null || account.isBlank()) {
            account = deviceId;
        }
        session.getAttributes().put(ATTR_DEVICE_ID, deviceId);
        session.getAttributes().put(ATTR_ACCOUNT, account);
        session.getAttributes().put(ATTR_AUTHORIZED, Boolean.TRUE);
        session.getAttributes().put("connectedAt", System.currentTimeMillis());

        // 单设备单连接：旧连接先踢下线
        WebSocketSession old = devices.put(deviceId, session);
        if (old != null && old.isOpen() && !old.getId().equals(session.getId())) {
            sendClose(old, 0, "new connection");
            closeQuietly(old);
        }
        touch(deviceId);
        sendFrame(session, Map.of("type", "welcome", "data", Map.of(
                "server_time", System.currentTimeMillis(),
                "heartbeat", 30)));
        log.info("AgentHub 设备上线: deviceId={}, account={}, version={}, 在线设备数={}",
                deviceId, account, str(helloData.get("version")), devices.size());
    }

    /**
     * 处理事件帧：新消息 / 会话消息快照统一映射为回调 DTO 落库。
     *
     * @param session  会话
     * @param deviceId 设备 ID
     * @param frame    事件帧
     */
    private void onEvent(WebSocketSession session, String deviceId, Map<String, Object> frame) {
        String event = str(frame.get("event"));
        Map<String, Object> data = asMap(frame.get("data"));
        if (data == null) {
            log.warn("AgentHub 事件缺少 data: deviceId={}, event={}", deviceId, event);
            return;
        }
        String account = str(session.getAttributes().get(ATTR_ACCOUNT));
        String accountKey = (account == null || account.isBlank()) ? deviceId : account;
        if (EVENT_NEW_MESSAGE.equals(event)) {
            persistMessage(data, accountKey, deviceId, true);
        } else if (EVENT_SESSIONS_SNAPSHOT.equals(event)) {
            onSessionsSnapshot(data, accountKey, deviceId);
        } else {
            log.debug("AgentHub 忽略事件: deviceId={}, event={}", deviceId, event);
        }
    }

    /**
     * 处理会话消息快照（welcome 后桌面端自动上报一次全量，用于历史回填）。
     * <p>
     * 只取 {@code messages[]} 逐条落库（会话表以消息为事实源自动维护），
     * {@code sessions[]} 仅用于日志统计。
     * </p>
     *
     * @param data       快照 data（sessions[] / messages[] / seq）
     * @param accountKey 账号标识
     * @param deviceId   设备 ID
     */
    @SuppressWarnings("unchecked")
    private void onSessionsSnapshot(Map<String, Object> data, String accountKey, String deviceId) {
        Object sessionsObj = data.get("sessions");
        int sessionCount = sessionsObj instanceof List<?> list ? list.size() : 0;
        Object messagesObj = data.get("messages");
        int saved = 0;
        if (messagesObj instanceof List<?> messages && !messages.isEmpty()) {
            int limit = Math.min(messages.size(), SNAPSHOT_MAX_MESSAGES);
            for (int i = 0; i < limit; i++) {
                Object item = messages.get(i);
                if (item instanceof Map<?, ?> map) {
                    if (persistMessage((Map<String, Object>) map, accountKey, deviceId, false)) {
                        saved++;
                    }
                }
            }
        }
        log.info("AgentHub 会话快照入库: deviceId={}, seq={}, sessions={}, 消息={}/{}",
                deviceId, data.get("seq"), sessionCount, saved,
                messagesObj instanceof List<?> l ? l.size() : 0);
        // 好友列表随快照携带: 幂等建档为潜在客户（只读, 不触发任何发送/决策）
        try {
            int contactsSaved = persistContacts(data, accountKey, deviceId);
            if (contactsSaved > 0) {
                log.info("AgentHub 好友列表建档: deviceId={}, contacts={}", deviceId, contactsSaved);
            }
        } catch (Exception e) {
            log.warn("AgentHub 好友列表建档失败 (不影响快照落库): deviceId={}, err={}",
                    deviceId, e.getMessage());
        }
    }

    /**
     * 好友列表幂等建档为客户（REMOTE-AGENT 好友上报）。
     * <p>
     * 按 (平台类型 + 平台客户 UID + 归属账号) 幂等：已存在跳过；不存在创建
     * {@code lifecycle=NEW} 的潜在客户。只读建档，不触发任何自动回复/发送。
     * </p>
     *
     * @param data       快照 data（含 contacts[]）
     * @param accountKey 账号标识（hello.account，缺省 deviceId）
     * @param deviceId   设备 ID（日志）
     * @return 新建客户数
     */
    @SuppressWarnings("unchecked")
    private int persistContacts(Map<String, Object> data, String accountKey, String deviceId) {
        Object contactsObj = data.get("contacts");
        if (!(contactsObj instanceof List<?> contacts) || contacts.isEmpty()) {
            return 0;
        }
        // 账号已存在（快照能到达说明账号已建档，但容错：查不到则跳过建档）
        Long accountId = resolveAccountIdByKey(accountKey);
        if (accountId == null) {
            log.debug("AgentHub 好友建档跳过: 账号未建档 accountKey={}", accountKey);
            return 0;
        }
        if (customerRepository == null) {
            log.debug("AgentHub 好友建档跳过: customerRepository 未注入");
            return 0;
        }
        int created = 0;
        int limit = Math.min(contacts.size(), SNAPSHOT_MAX_MESSAGES);
        for (int i = 0; i < limit; i++) {
            Object item = contacts.get(i);
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String uid = str(map.get("username"));
            if (uid == null || uid.isBlank()) {
                continue;
            }
            try {
                Optional<ScrmCustomerEntity> existed = customerRepository
                        .findByPlatformTypeAndPlatformCustomerUidAndOwnerAccountId(
                                PLATFORM_WECHAT_PERSONAL, uid, accountId);
                if (existed.isPresent()) {
                    continue;
                }
                ScrmCustomerEntity entity = new ScrmCustomerEntity();
                entity.setPlatformType(PLATFORM_WECHAT_PERSONAL);
                entity.setPlatformCustomerUid(uid);
                entity.setNickname(truncate(firstNonBlank(str(map.get("remark")), str(map.get("nick_name"))), 255));
                entity.setOwnerAccountId(accountId);
                entity.setLifecycle("NEW");
                customerRepository.save(entity);
                created++;
            } catch (Exception e) {
                log.debug("AgentHub 好友建档单条失败(跳过): uid={}, err={}", uid, e.getMessage());
            }
        }
        if (created > 0) {
            log.info("AgentHub 好友建档: deviceId={}, accountId={}, 新建 {} 个潜在客户",
                    deviceId, accountId, created);
        }
        return created;
    }

    /** 取第一个非空字符串（remark 优先，其次 nick_name） */
    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /** 按平台账号标识解析账号 ID（无则返回 null） */
    private Long resolveAccountIdByKey(String accountKey) {
        if (accountKey == null || accountKey.isBlank()) {
            return null;
        }
        try {
            ScrmAccountDto account = accountService.getAccountByPlatform(
                    PLATFORM_WECHAT_PERSONAL, accountKey);
            return account != null ? account.getId() : null;
        } catch (Exception e) {
            log.debug("AgentHub 账号解析失败 accountKey={}, err={}", accountKey, e.getMessage());
            return null;
        }
    }

    /**
     * 把一条微信侧消息映射为 SCRM 会话事件回调并落库。
     * <p>
     * 账号维度：微信登录账号（hello 的 account，缺省用 deviceId）→ {@code platformAccountUid}；
     * 客户维度：微信会话标识（单聊为对方 wxid，群聊为 {@code xxx@chatroom}）→
     * {@code platformCustomerUid}。消息按 {@code local_id} 作为 {@code platformMessageId}
     * 在会话内幂等去重，快照回填与后续新消息不会重复。
     * </p>
     * <p>
     * {@code triggerAutoReply=true} 时（实时 {@code wechat.new_message}），落库成功后
     * 异步触发自动回复匹配（IN 方向文本消息命中规则后发布 {@link OutboundReplyRequestedEvent}
     * 进入出站链路）；快照回填（历史消息）传 false 不触发，避免 welcome 后全量刷屏。
     * </p>
     *
     * @param data             消息数据
     * @param accountKey       账号标识
     * @param deviceId         设备 ID（日志用）
     * @param triggerAutoReply 是否触发自动回复匹配（新消息 true / 快照回填 false）
     * @return 是否成功落库（false 表示数据不完整或落库异常）
     */
    @SuppressWarnings("unchecked")
    private boolean persistMessage(Map<String, Object> data, String accountKey, String deviceId,
                                   boolean triggerAutoReply) {
        String session = str(data.get("session"));
        if (session == null || session.isBlank()) {
            return false;
        }
        try {
            ConversationEventCallbackDto dto = new ConversationEventCallbackDto();
            dto.setPlatformType(PLATFORM_WECHAT_PERSONAL);
            dto.setPlatformAccountUid(accountKey);
            dto.setAccountDisplayName(accountKey);
            dto.setPlatformCustomerUid(session);
            dto.setCustomerNickname(truncate(str(data.get("session_name")), 255));
            boolean self = Boolean.TRUE.equals(asBool(data.get("is_self")));
            dto.setDirection(self ? "OUTBOUND" : "INBOUND");
            dto.setMessageType("TEXT");
            dto.setContent(messageContent(data));
            dto.setPlatformMessageId(str(data.get("local_id")));
            dto.setSentAt(toLocalDateTime(firstNotNull(data.get("create_time"), data.get("msg_time"))));
            ScrmConversationMessageDto saved = conversationMessageService.saveMessageFromCallback(dto);
            // 实时新消息（IN 方向）命中自动回复规则 → 出站链路（AgentHub 实时代发 / 轮询兜底）
            if (triggerAutoReply && "INBOUND".equalsIgnoreCase(dto.getDirection())) {
                autoReplyService.matchInboundMessage(
                        PLATFORM_WECHAT_PERSONAL,
                        saved == null ? null : saved.getConversationId(),
                        dto.getContent());
            }
            return true;
        } catch (Exception e) {
            log.warn("AgentHub 消息落库失败: deviceId={}, session={}, err={}",
                    deviceId, session, e.getMessage());
            return false;
        }
    }

    /**
     * 从消息数据提取入库正文（非文本类型用 {@code [kind]} 占位，保证会话列表可见）。
     *
     * @param data 消息数据
     * @return 截断后的正文
     */
    private String messageContent(Map<String, Object> data) {
        String text = str(data.get("text"));
        if (text != null && !text.isBlank()) {
            return truncate(text, MAX_TEXT_LENGTH);
        }
        String kind = str(data.get("kind"));
        return "[" + (kind == null || kind.isBlank() ? "message" : kind) + "]";
    }

    /**
     * 处理指令应答帧：形如 {@code ob-<outboundId>} 的指令 ID 回写出站队列状态。
     * <p>
     * 桌面端执行 {@code wechat.send_text} 后回填 {@code {id, ok, result|error}}。
     * ok=true → SENT；ok=false → 提取 error.code/error.message 回执 FAILED
     * （未超重试上限自动退回 PENDING 指数退避）。非 <tt>ob-</tt> 前缀的指令
     * （如保留的探活类）仅记录日志。
     * </p>
     *
     * @param deviceId 设备 ID
     * @param id       指令 ID（<tt>ob-&lt;outboundId&gt;</tt>）
     * @param frame    应答帧
     */
    private void onCommandReply(String deviceId, String id, Map<String, Object> frame) {
        if (id == null || !id.startsWith(COMMAND_ID_PREFIX)) {
            log.debug("AgentHub 收到非出站指令应答: deviceId={}, id={}", deviceId, id);
            return;
        }
        Long outboundId = parseOutboundId(id);
        if (outboundId == null) {
            log.warn("AgentHub 出站指令应答 ID 非法: deviceId={}, id={}", deviceId, id);
            return;
        }
        boolean ok = Boolean.TRUE.equals(asBool(frame.get("ok")));
        String errorCode = null;
        String errorMessage = null;
        if (!ok) {
            Map<String, Object> error = asMap(frame.get("error"));
            if (error != null) {
                errorCode = str(error.get("code"));
                errorMessage = str(error.get("message"));
            }
        }
        try {
            outboundMessageService.ackByOutboundId(outboundId, ok, errorCode, errorMessage);
            log.info("AgentHub 出站应答回填: deviceId={}, outboundId={}, ok={}, err={}",
                    deviceId, outboundId, ok,
                    errorMessage == null || errorMessage.isBlank() ? "" : errorMessage);
        } catch (Exception e) {
            log.warn("AgentHub 出站应答回填失败: deviceId={}, outboundId={}, err={}",
                    deviceId, outboundId, e.getMessage());
        }
    }

    /**
     * 解析出站指令 ID（{@code ob-<outboundId>} → 数字 ID）。
     *
     * @param id 指令 ID
     * @return 出站队列记录 ID，格式非法返回 null
     */
    private Long parseOutboundId(String id) {
        String suffix = id.substring(COMMAND_ID_PREFIX.length());
        try {
            return Long.parseLong(suffix.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 监听出站消息入队事件，对在线设备实时代发 wechat.send_text。
     * <p>
     * AFTER_COMMIT 保证出站记录已持久化（避免读到未提交的 PENDING 行），
     * 事务提交后于监听线程（虚拟线程）执行，不阻塞入队方。推送流程：
     * </p>
     * <ol>
     *   <li>按 event.accountId 解析账号 platformAccountUid（self wxid）；</li>
     *   <li>匹配携带该 account 的在线设备连接（多个同账号连接取最新）；</li>
     *   <li>无匹配设备 → 不抢占，消息保持 PENDING 由轮询兜底（或设备上线后追补）；</li>
     *   <li>匹配设备 → {@code claimForPush} 抢占（PENDING→IN_PROGRESS），成功才下发指令；</li>
     *   <li>抢占成功但发送帧异常 → 回执 FAILED 走退避重试（确认补偿语义）。</li>
     * </ol>
     *
     * @param event 出站入队事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboundEnqueued(OutboundEnqueuedEvent event) {
        if (event == null || event.getOutboundId() == null
                || event.getTargetPlatformId() == null || event.getTargetPlatformId().isBlank()) {
            return;
        }
        WebSocketSession targetSession = findSessionByAccount(event.getAccountId());
        if (targetSession == null) {
            log.debug("AgentHub 实时代发跳过(设备离线): outboundId={}, accountId={}, 保持 PENDING 待轮询/上线追补",
                    event.getOutboundId(), event.getAccountId());
            return;
        }
        // 抢占（PENDING→IN_PROGRESS），失败说明已被轮询端取走/已终态，不双发
        try {
            if (!outboundMessageService.claimForPush(event.getOutboundId())) {
                log.debug("AgentHub 实时代发抢占失败(已被并行方取走或已终态): outboundId={}",
                        event.getOutboundId());
                return;
            }
        } catch (Exception e) {
            log.warn("AgentHub 实时代发抢占异常: outboundId={}, err={}", event.getOutboundId(), e.getMessage());
            return;
        }
        String method = resolveSendMethod(event.getMessageType());
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("session", event.getTargetPlatformId());
        params.put("verify", true);
        if (METHOD_SEND_TEXT.equals(method)) {
            params.put("text", event.getContent() == null ? "" : event.getContent());
        } else {
            String mediaUrl = resolveMediaUrl(event);
            String filePath = mediaUrl == null ? event.getMediaObjectKey() : mediaUrl;
            params.put("path", filePath == null ? "" : filePath);
            if (event.getMediaFileName() != null && !event.getMediaFileName().isBlank()) {
                params.put("name", event.getMediaFileName());
            }
        }
        Map<String, Object> command = new java.util.HashMap<>();
        command.put("id", COMMAND_ID_PREFIX + event.getOutboundId());
        command.put("method", method);
        command.put("params", params);
        sendFrame(targetSession, command);
        log.info("AgentHub 实时代发指令: outboundId={}, method={}, target={}, media={}",
                event.getOutboundId(), method, event.getTargetPlatformId(),
                event.getMediaObjectKey() != null ? event.getMediaObjectKey() : "-");
    }

    /**
     * 按消息类型解析发送指令方法。
     * <p>
     * IMAGE → {@code wechat.send_image}；FILE → {@code wechat.send_file}；
     * 其余（TEXT 等）→ {@code wechat.send_text}。
     * </p>
     *
     * @param messageType 消息类型
     * @return 指令方法名
     */
    private String resolveSendMethod(String messageType) {
        if (messageType != null) {
            String upper = messageType.toUpperCase(Locale.ROOT);
            if (MESSAGE_TYPE_IMAGE.equals(upper)) {
                return METHOD_SEND_IMAGE;
            }
            if (MESSAGE_TYPE_FILE.equals(upper)) {
                return METHOD_SEND_FILE;
            }
        }
        return METHOD_SEND_TEXT;
    }

    /**
     * 解析媒体出站消息的下载地址（优先预签名 URL，失败回退对象 key）。
     * <p>
     * 通过 {@link ConversationMediaService} 新鲜生成 1h 预签名 URL 下发执行侧下载；
     * 生成失败时回退对象存储 key（执行侧可凭 key 另走下载接口）。
     * </p>
     *
     * @param event 出站入队事件
     * @return 预签名 URL；无媒体或生成失败返回 null
     */
    private String resolveMediaUrl(OutboundEnqueuedEvent event) {
        if (event.getMediaObjectKey() == null || event.getMediaObjectKey().isBlank()) {
            return null;
        }
        try {
            return conversationMediaService.getMediaUrl(event.getMediaObjectKey(), 60);
        } catch (Exception e) {
            log.warn("AgentHub 实时代发媒体 URL 生成失败: outboundId={}, err={}",
                    event.getOutboundId(), e.getMessage());
            return null;
        }
    }

    /**
     * 按账号 ID 查找在线设备连接（账号解析 self wxid 匹配 hello.account）。
     * <p>
     * 存在多个连接匹配时返回最后建立的连接（同一微信账号只应有一个设备在线，
     * hello 握手已做单设备单连接踢下线保护）。
     * </p>
     *
     * @param accountId 账号 ID
     * @return 匹配的 WebSocket 会话，无匹配返回 null
     */
    private WebSocketSession findSessionByAccount(Long accountId) {
        if (accountId == null || devices.isEmpty()) {
            return null;
        }
        String accountUid;
        try {
            ScrmAccountDto accountDto = accountService.getAccountInternal(accountId);
            accountUid = accountDto == null ? null : accountDto.getPlatformAccountUid();
        } catch (Exception e) {
            log.debug("AgentHub 实时代发: 账号解析失败 accountId={}, err={}", accountId, e.getMessage());
            return null;
        }
        if (accountUid == null || accountUid.isBlank()) {
            return null;
        }
        WebSocketSession matched = null;
        long newest = -1L;
        for (WebSocketSession session : devices.values()) {
            if (!session.isOpen()) {
                continue;
            }
            Object sessionAccount = session.getAttributes().get(ATTR_ACCOUNT);
            if (sessionAccount == null || !accountUid.equals(String.valueOf(sessionAccount))) {
                continue;
            }
            Long connectedAt = asLong(session.getAttributes().get("connectedAt"));
            if (connectedAt == null || connectedAt >= newest) {
                matched = session;
                newest = connectedAt == null ? -1L : connectedAt;
            }
        }
        return matched;
    }

    /**
     * 心跳保洁：周期清理超时/失效连接（由 {@code @Scheduled} 调用）。
     */
    @Scheduled(fixedDelay = 30_000L)
    public void sweepIdleConnections() {
        long now = System.currentTimeMillis();
        devices.forEach((deviceId, session) -> {
            if (!session.isOpen()) {
                devices.remove(deviceId, session);
                lastActive.remove(deviceId);
                return;
            }
            Long last = lastActive.get(deviceId);
            if (last != null && now - last > IDLE_TIMEOUT_MS) {
                log.warn("AgentHub 连接超时断开: deviceId={}", deviceId);
                sendClose(session, 0, "idle timeout");
                closeQuietly(session);
                devices.remove(deviceId, session);
                lastActive.remove(deviceId);
            }
        });
    }

    /**
     * 当前在线设备快照（供管理端点查询）。
     *
     * @return deviceId → 账号标识
     */
    public Map<String, String> onlineDevices() {
        Map<String, String> result = new LinkedHashMap<>();
        devices.forEach((deviceId, session) -> {
            Object account = session.getAttributes().get(ATTR_ACCOUNT);
            result.put(deviceId, account == null ? "" : account.toString());
        });
        return result;
    }

    // ------------------------------------------------------------------ 工具

    private void touch(String deviceId) {
        if (deviceId != null) {
            lastActive.put(deviceId, System.currentTimeMillis());
        }
    }

    private Map<String, Object> parseFrame(String raw) throws Exception {
        Object parsed = objectMapper.readValue(raw, Object.class);
        if (parsed instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> frame = (Map<String, Object>) map;
            return frame;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private void sendFrame(WebSocketSession session, Map<String, Object> frame) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
            }
        } catch (IOException e) {
            log.warn("AgentHub 发送失败: sessionId={}, err={}", session.getId(), e.getMessage());
        }
    }

    private void sendClose(WebSocketSession session, int code, String reason) {
        sendFrame(session, Map.of("type", "close", "code", code, "reason", reason));
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        } catch (IOException ignored) {
            // 关闭失败按连接断开处理
        }
    }

    private String str(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        return String.valueOf(value);
    }

    private Boolean asBool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return "true".equalsIgnoreCase(s.strip()) || "1".equals(s);
        }
        return null;
    }

    private Long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s.strip());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Object firstNotNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(Object millis) {
        Long ms = asLong(millis);
        if (ms == null || ms <= 0) {
            return LocalDateTime.now();
        }
        // 兼容秒级时间戳（微信 4.x DB / AgentLink 上报的 create_time 为 Unix 秒，
        // 0 ~ 10^12 区间视为秒，自动放大到毫秒；10^12 以上已是毫秒）
        if (ms < 10_000_000_000L) {
            ms *= 1000L;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
    }

    private boolean secureEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
