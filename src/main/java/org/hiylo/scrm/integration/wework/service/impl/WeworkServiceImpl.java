/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkServiceImpl.java
 * Date : 2026/09/19 21:20:11
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.integration.wework.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.integration.wework.config.WeworkConfig;
import org.hiylo.scrm.integration.wework.dto.WeworkChatDataDto;
import org.hiylo.scrm.integration.wework.dto.WeworkExternalContactDto;
import org.hiylo.scrm.integration.wework.dto.WeworkGroupChatDto;
import org.hiylo.scrm.integration.wework.dto.WeworkMessageSendDto;
import org.hiylo.scrm.integration.wework.dto.WeworkUserDto;
import org.hiylo.scrm.integration.wework.service.WeworkService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 企业微信平台服务实现
 * <p>
 * 实现 {@link WeworkService} 接口。企微特有方法(通讯录/外部联系人/客户群/会话存档)
 * 通过 WebClient 调用企微开放 API,access_token 通过 /gettoken 接口获取并带本地缓存
 * (有效期略小于企微返回的 7200 秒)。
 * </p>
 *
 * @author Hsi Chu
 * @since 1.0.0
 */
@Slf4j
@Service
public class WeworkServiceImpl implements WeworkService {

    /** access_token 缓存有效期(秒),略小于企微返回的 7200 秒 */
    private static final long TOKEN_CACHE_TTL_SECONDS = 7100L;

    /** 企微配置 */
    private final WeworkConfig config;

    /** Web 客户端 */
    private final WebClient webClient;

    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    /** 缓存的 access_token */
    private volatile String cachedAccessToken;

    /** access_token 缓存到期时间戳(毫秒) */
    private volatile long tokenExpireAt;

    /**
     * 构造企业微信服务实现
     *
     * @param config 企微配置
     */
    public WeworkServiceImpl(WeworkConfig config) {
        this.config = new WeworkConfig(config);
        this.webClient = WebClient.builder().build();
        this.objectMapper = new ObjectMapper();
    }

    // ==================== 通讯录管理 ====================

    /**
     * 获取部门列表
     * <p>调用企微 /department/list 接口。</p>
     *
     * @return 部门列表
     */
    @Override
    public List<Map<String, Object>> getDepartmentList() {
        log.info("获取企业微信部门列表");
        if (config.isMockMode() || !config.isRealApiEnabled()) {
            return mockDepartmentList();
        }
        try {
            String url = config.getApiUrl("/department/list?access_token=" + getAccessToken());
            JsonNode resp = webClient.get().uri(url).retrieve()
                    .bodyToMono(String.class)
                    .map(this::readTree)
                    .block();
            return parseDepartmentList(resp);
        } catch (Exception ex) {
            log.error("获取企业微信部门列表失败", ex);
            return Collections.emptyList();
        }
    }

    /**
     * 获取部门成员列表
     * <p>调用企微 /user/list_id 接口。</p>
     *
     * @param departmentId 部门ID
     * @return 部门成员列表
     */
    @Override
    public List<Map<String, Object>> getDepartmentUserList(Long departmentId) {
        log.info("获取企业微信部门成员列表: departmentId={}", departmentId);
        if (config.isMockMode() || !config.isRealApiEnabled()) {
            return mockDepartmentUserList();
        }
        try {
            String url = config.getApiUrl("/user/list_id?access_token=" + getAccessToken()
                    + "&department_id=" + departmentId);
            JsonNode resp = webClient.get().uri(url).retrieve()
                    .bodyToMono(String.class)
                    .map(this::readTree)
                    .block();
            return parseDepartmentUserList(resp);
        } catch (Exception ex) {
            log.error("获取企业微信部门成员列表失败: departmentId={}", departmentId, ex);
            return Collections.emptyList();
        }
    }

    /**
     * 获取成员详情
     * <p>调用企微 /user/get 接口,并将结果映射为 {@link WeworkUserDto}。</p>
     *
     * @param userId 成员userid
     * @return 用户信息响应
     */
    @Override
    public WeworkUserDto getUserDetail(String userId) {
        log.info("获取企业微信成员详情: userId={}", userId);
        if (config.isMockMode() || !config.isRealApiEnabled()) {
            return createMockUserInfo(userId, "模拟用户", "https://example.com/avatar.jpg");
        }
        try {
            String url = config.getApiUrl("/user/get?access_token=" + getAccessToken()
                    + "&userid=" + userId);
            JsonNode resp = webClient.get().uri(url).retrieve()
                    .bodyToMono(String.class)
                    .map(this::readTree)
                    .block();
            return parseUserInfo(userId, resp);
        } catch (Exception ex) {
            log.error("获取企业微信成员详情失败: userId={}", userId, ex);
            return createMockUserInfo(userId, null, null);
        }
    }

    // ==================== 外部联系人 ====================

    /**
     * 获取外部联系人列表
     * <p>调用企微 /externalcontact/list 接口。</p>
     *
     * @param userId 企业成员userid
     * @return 外部联系人列表
     */
    @Override
    public List<WeworkExternalContactDto> getExternalContactList(String userId) {
        log.info("获取企业微信外部联系人列表: userId={}", userId);
        if (config.isMockMode() || !config.isRealApiEnabled()) {
            return Collections.emptyList();
        }
        try {
            String url = config.getApiUrl("/externalcontact/list?access_token=" + getAccessToken()
                    + "&userid=" + userId);
            JsonNode resp = webClient.get().uri(url).retrieve()
                    .bodyToMono(String.class)
                    .map(this::readTree)
                    .block();
            return parseExternalContactList(resp);
        } catch (Exception ex) {
            log.error("获取企业微信外部联系人列表失败: userId={}", userId, ex);
            return Collections.emptyList();
        }
    }

    /**
     * 获取外部联系人详情
     * <p>调用企微 /externalcontact/get 接口。</p>
     *
     * @param externalUserId 外部联系人userid
     * @return 外部联系人详情
     */
    @Override
    public WeworkExternalContactDto getExternalContactDetail(String externalUserId) {
        log.info("获取企业微信外部联系人详情: externalUserId={}", externalUserId);
        if (config.isMockMode() || !config.isRealApiEnabled()) {
            return WeworkExternalContactDto.success(externalUserId, "模拟客户");
        }
        try {
            String url = config.getApiUrl("/externalcontact/get?access_token=" + getAccessToken()
                    + "&external_userid=" + externalUserId);
            JsonNode resp = webClient.get().uri(url).retrieve()
                    .bodyToMono(String.class)
                    .map(this::readTree)
                    .block();
            return parseExternalContactDetail(resp);
        } catch (Exception ex) {
            log.error("获取企业微信外部联系人详情失败: externalUserId={}", externalUserId, ex);
            return WeworkExternalContactDto.failure("EXTERNAL_CONTACT_ERROR", ex.getMessage());
        }
    }

    // ==================== 客户群 ====================

    /**
     * 获取客户群列表
     * <p>调用企微 /externalcontact/groupchat/list 接口(POST)。</p>
     *
     * @param pageIndex 分页索引(从0开始)
     * @param pageSize  每页数量(最大1000)
     * @return 客户群列表
     */
    @Override
    public List<WeworkGroupChatDto> getGroupChatList(Integer pageIndex, Integer pageSize) {
        log.info("获取企业微信客户群列表: pageIndex={}, pageSize={}", pageIndex, pageSize);
        if (config.isMockMode() || !config.isRealApiEnabled()) {
            return Collections.emptyList();
        }
        try {
            String url = config.getApiUrl("/externalcontact/groupchat/list?access_token=" + getAccessToken());
            Map<String, Object> body = new HashMap<>();
            body.put("limit", pageSize == null ? Integer.valueOf(100) : pageSize);
            body.put("cursor", "");
            JsonNode resp = postJson(url, body);
            return parseGroupChatList(resp);
        } catch (Exception ex) {
            log.error("获取企业微信客户群列表失败", ex);
            return Collections.emptyList();
        }
    }

    /**
     * 获取客户群详情
     * <p>调用企微 /externalcontact/groupchat/get 接口(POST)。</p>
     *
     * @param chatId 客户群ID
     * @return 客户群详情
     */
    @Override
    public WeworkGroupChatDto getGroupChatDetail(String chatId) {
        log.info("获取企业微信客户群详情: chatId={}", chatId);
        if (config.isMockMode() || !config.isRealApiEnabled()) {
            return WeworkGroupChatDto.success(chatId, "模拟客户群");
        }
        try {
            String url = config.getApiUrl("/externalcontact/groupchat/get?access_token=" + getAccessToken());
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("need_name", 1);
            body.put("need_owner", 1);
            JsonNode resp = postJson(url, body);
            return parseGroupChatDetail(resp);
        } catch (Exception ex) {
            log.error("获取企业微信客户群详情失败: chatId={}", chatId, ex);
            return WeworkGroupChatDto.failure("GROUP_CHAT_ERROR", ex.getMessage());
        }
    }

    // ==================== 消息推送 ====================

    /**
     * 向外部联系人发送文本消息
     * <p>调用企微 /message/send 接口(应用消息推送)。</p>
     *
     * @param userId  接收消息的成员userid
     * @param content 文本内容
     * @return 发送结果
     */
    @Override
    public WeworkMessageSendDto sendMessageToExternal(String userId, String content) {
        log.info("发送企业微信消息: userId={}", userId);
        if (config.isMockMode() || !config.isRealApiEnabled()) {
            return WeworkMessageSendDto.success("mock_msg_" + System.currentTimeMillis());
        }
        try {
            String url = config.getApiUrl("/message/send?access_token=" + getAccessToken());
            Map<String, Object> body = new HashMap<>();
            body.put("touser", userId);
            body.put("msgtype", "text");
            body.put("agentid", config.getAgentId());
            Map<String, Object> text = new HashMap<>();
            text.put("content", content);
            body.put("text", text);
            JsonNode resp = postJson(url, body);
            if (resp != null && resp.has("msgid")) {
                return WeworkMessageSendDto.success(resp.get("msgid").asText());
            }
            return WeworkMessageSendDto.failure("SEND_ERROR",
                    resp != null && resp.has("errmsg") ? resp.get("errmsg").asText() : "未知错误");
        } catch (Exception ex) {
            log.error("发送企业微信消息失败: userId={}", userId, ex);
            return WeworkMessageSendDto.failure("SEND_ERROR", ex.getMessage());
        }
    }

    /**
     * 发送客户群消息
     * <p>调用企微 /appchat/send 接口(应用群消息推送)。</p>
     *
     * @param chatId  客户群ID
     * @param content 文本内容
     * @return 发送结果
     */
    @Override
    public WeworkMessageSendDto sendGroupMessage(String chatId, String content) {
        log.info("发送企业微信群消息: chatId={}", chatId);
        if (config.isMockMode() || !config.isRealApiEnabled()) {
            return WeworkMessageSendDto.success("mock_group_msg_" + System.currentTimeMillis());
        }
        try {
            String url = config.getApiUrl("/appchat/send?access_token=" + getAccessToken());
            Map<String, Object> body = new HashMap<>();
            body.put("chatid", chatId);
            body.put("msgtype", "text");
            Map<String, Object> text = new HashMap<>();
            text.put("content", content);
            body.put("text", text);
            JsonNode resp = postJson(url, body);
            if (resp != null && resp.has("msgid")) {
                return WeworkMessageSendDto.success(resp.get("msgid").asText());
            }
            return WeworkMessageSendDto.failure("SEND_ERROR",
                    resp != null && resp.has("errmsg") ? resp.get("errmsg").asText() : "未知错误");
        } catch (Exception ex) {
            log.error("发送企业微信群消息失败: chatId={}", chatId, ex);
            return WeworkMessageSendDto.failure("SEND_ERROR", ex.getMessage());
        }
    }

    // ==================== 会话存档 ====================

    /**
     * 拉取会话存档数据
     * <p>调用企微 /msgaudit/check_conversation 接口(POST)。</p>
     *
     * @param seq   起始seq
     * @param limit 拉取数量
     * @return 会话数据列表
     */
    @Override
    public List<WeworkChatDataDto> getChatDataList(Long seq, Integer limit) {
        log.info("拉取企业微信会话存档: seq={}, limit={}", seq, limit);
        if (config.isMockMode() || !config.isRealApiEnabled()) {
            return Collections.emptyList();
        }
        try {
            String url = config.getApiUrl("/msgaudit/check_conversation?access_token=" + getAccessToken());
            Map<String, Object> body = new HashMap<>();
            body.put("seq", seq == null ? Long.valueOf(0L) : seq);
            body.put("limit", limit == null ? Integer.valueOf(100) : limit);
            JsonNode resp = postJson(url, body);
            return parseChatDataList(resp);
        } catch (Exception ex) {
            log.error("拉取企业微信会话存档失败: seq={}", seq, ex);
            return Collections.emptyList();
        }
    }

    /**
     * 解密会话存档数据
     * <p>
     * 企微会话存档消息体需使用企微提供的 libWeWorkFinanceSdk_C.so(JNI) 进行解密,
     * 该 SDK 为本地依赖,无法在纯 Java 环境中调用。此处返回未解密的 mock 结果,
     * 业务方需在引入 SDK 后通过 native 方法调用 {@code DecryptData} 完成解密。
     * </p>
     *
     * @param encryptChatMessage 加密的消息内容
     * @param encryptRandomKey   加密的随机密钥
     * @return 解密后的会话数据
     */
    @Override
    public WeworkChatDataDto decryptChatData(String encryptChatMessage, String encryptRandomKey) {
        log.warn("企业微会话存档解密需要 libWeWorkFinanceSdk_C.so(JNI) 支持,当前返回 mock 结果");
        WeworkChatDataDto dto = WeworkChatDataDto.failure(
                "DECRYPT_SDK_REQUIRED",
                "会话存档解密需要 libWeWorkFinanceSdk_C.so(JNI) 支持");
        dto.setEncryptChatMessage(encryptChatMessage);
        dto.setEncryptRandomKey(encryptRandomKey);
        return dto;
    }

    // ==================== access_token 缓存失效 ====================

    /**
     * 使 access_token 缓存失效
     * <p>
     * 当企微配置(corpId/secret)发生变更时调用，使当前缓存的 access_token 失效，
     * 下次调用 getAccessToken() 时将使用新配置重新获取 token。
     * </p>
     */
    @Override
    public void invalidateTokenCache() {
        synchronized (this) {
            cachedAccessToken = null;
            tokenExpireAt = 0L;
        }
        log.info("企微 access_token 缓存已失效");
    }

    /**
     * 判断企微平台是否可用: corpId 与 secret 均已配置。
     *
     * @return true 表示企微平台可用
     */
    @Override
    public boolean isAvailable() {
        String corpId = config.getCorpId();
        String secret = config.getSecret();
        return corpId != null && !corpId.isBlank() && secret != null && !secret.isBlank();
    }

    // ==================== access_token 管理 ====================

    /**
     * 获取 access_token(带本地缓存)
     * <p>缓存未命中或已过期时调用企微 gettoken 接口刷新。</p>
     *
     * @return access_token
     */
    private String getAccessToken() {
        long now = System.currentTimeMillis();
        if (cachedAccessToken != null && now < tokenExpireAt) {
            return cachedAccessToken;
        }
        synchronized (this) {
            if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpireAt) {
                return cachedAccessToken;
            }
            return fetchAccessToken();
        }
    }

    /**
     * 调用企微 gettoken 接口获取 access_token
     *
     * @return access_token
     */
    private String fetchAccessToken() {
        if (config.getCorpId() == null || config.getSecret() == null) {
            throw new IllegalStateException("企业微信 corpId/secret 未配置");
        }
        String url = String.format("%s/gettoken?corpid=%s&corpsecret=%s",
                config.getBaseUrl(), config.getCorpId(), config.getSecret());
        log.info("获取企业微信 access_token");
        try {
            JsonNode resp = webClient.get().uri(url).retrieve()
                    .bodyToMono(String.class)
                    .map(this::readTree)
                    .block();
            if (resp != null && resp.has("access_token")) {
                cachedAccessToken = resp.get("access_token").asText();
                tokenExpireAt = System.currentTimeMillis() + TOKEN_CACHE_TTL_SECONDS * 1000L;
                log.info("获取企业微信 access_token 成功");
                return cachedAccessToken;
            }
            String errmsg = resp != null && resp.has("errmsg") ? resp.get("errmsg").asText() : "未知错误";
            throw new RuntimeException("获取企业微信 access_token 失败: " + errmsg);
        } catch (WebClientResponseException ex) {
            throw new RuntimeException("获取企业微信 access_token 失败: " + ex.getMessage(), ex);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 使用 Jackson 2 ObjectMapper 解析响应文本。
     * Boot 4 默认消息编解码器为 Jackson 3，无法直接反序列化 Jackson 2 的 JsonNode，
     * 因此统一以 String 接收后在此转换，保持既有解析逻辑不变。
     *
     * @param content 响应文本
     * @return 解析后的 JsonNode
     */
    private JsonNode readTree(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (Exception e) {
            throw new IllegalStateException("企业微信响应 JSON 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送 JSON POST 请求
     *
     * @param url  请求地址
     * @param body 请求体
     * @return 响应 JSON
     */
    private JsonNode postJson(String url, Map<String, Object> body) {
        return webClient.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::readTree)
                .block();
    }

    /**
     * 创建模拟用户信息响应(mock 模式使用)
     *
     * @param userId   用户ID
     * @param nickname 昵称
     * @param avatar   头像URL
     * @return 用户信息响应
     */
    private WeworkUserDto createMockUserInfo(String userId, String nickname, String avatar) {
        return WeworkUserDto.builder()
                .platformUserId(userId)
                .platformType("WEWORK")
                .nickname(nickname)
                .avatar(avatar)
                .build();
    }

    /**
     * 解析部门列表响应
     *
     * @param resp 响应 JSON
     * @return 部门列表
     */
    private List<Map<String, Object>> parseDepartmentList(JsonNode resp) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (resp == null || !resp.has("department")) {
            return result;
        }
        for (JsonNode item : resp.get("department")) {
            Map<String, Object> dept = new LinkedHashMap<>();
            dept.put("id", item.has("id") ? item.get("id").asLong() : null);
            dept.put("name", item.has("name") ? item.get("name").asText() : null);
            dept.put("parentid", item.has("parentid") ? item.get("parentid").asLong() : null);
            dept.put("order", item.has("order") ? item.get("order").asInt() : null);
            result.add(dept);
        }
        return result;
    }

    /**
     * 解析部门成员列表响应
     *
     * @param resp 响应 JSON
     * @return 部门成员列表
     */
    private List<Map<String, Object>> parseDepartmentUserList(JsonNode resp) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (resp == null || !resp.has("dept_user")) {
            return result;
        }
        for (JsonNode item : resp.get("dept_user")) {
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("userid", item.has("userid") ? item.get("userid").asText() : null);
            user.put("department", item.has("department") ? item.get("department").asLong() : null);
            result.add(user);
        }
        return result;
    }

    /**
     * 解析用户信息响应
     *
     * @param userId 成员userid
     * @param resp   响应 JSON
     * @return 用户信息响应
     */
    private WeworkUserDto parseUserInfo(String userId, JsonNode resp) {
        if (resp == null) {
            return createMockUserInfo(userId, null, null);
        }
        String name = resp.has("name") ? resp.get("name").asText() : userId;
        String avatar = resp.has("avatar") ? resp.get("avatar").asText() : null;
        return WeworkUserDto.builder()
                .platformUserId(userId)
                .platformType("WEWORK")
                .nickname(name)
                .avatar(avatar)
                .build();
    }

    /**
     * 解析外部联系人列表响应
     *
     * @param resp 响应 JSON
     * @return 外部联系人列表
     */
    private List<WeworkExternalContactDto> parseExternalContactList(JsonNode resp) {
        List<WeworkExternalContactDto> result = new ArrayList<>();
        if (resp == null || !resp.has("external_userid")) {
            return result;
        }
        for (JsonNode item : resp.get("external_userid")) {
            String externalUserId = item.asText();
            result.add(WeworkExternalContactDto.success(externalUserId, null));
        }
        return result;
    }

    /**
     * 解析外部联系人详情响应
     *
     * @param resp 响应 JSON
     * @return 外部联系人详情
     */
    private WeworkExternalContactDto parseExternalContactDetail(JsonNode resp) {
        if (resp == null || !resp.has("external_contact")) {
            return WeworkExternalContactDto.failure("PARSE_ERROR", "响应缺少 external_contact 字段");
        }
        JsonNode contact = resp.get("external_contact");
        WeworkExternalContactDto dto = WeworkExternalContactDto.success(
                contact.has("external_userid") ? contact.get("external_userid").asText() : null,
                contact.has("name") ? contact.get("name").asText() : null);
        if (contact.has("avatar")) {
            dto.setAvatar(contact.get("avatar").asText());
        }
        if (contact.has("corp_name")) {
            dto.setCorpName(contact.get("corp_name").asText());
        }
        if (contact.has("corp_full_name")) {
            dto.setCorpFullName(contact.get("corp_full_name").asText());
        }
        if (contact.has("type")) {
            dto.setType(contact.get("type").asInt());
        }
        if (contact.has("gender")) {
            dto.setGender(contact.get("gender").asInt());
        }
        if (resp.has("follow_user") && resp.get("follow_user").size() > 0) {
            JsonNode followUser = resp.get("follow_user").get(0);
            if (followUser.has("userid")) {
                dto.setOwnerUserId(followUser.get("userid").asText());
            }
            if (followUser.has("remark")) {
                dto.setRemark(followUser.get("remark").asText());
            }
            if (followUser.has("description")) {
                dto.setDescription(followUser.get("description").asText());
            }
            if (followUser.has("createtime")) {
                dto.setAddTime(LocalDateTime.now());
            }
        }
        return dto;
    }

    /**
     * 解析客户群列表响应
     *
     * @param resp 响应 JSON
     * @return 客户群列表
     */
    private List<WeworkGroupChatDto> parseGroupChatList(JsonNode resp) {
        List<WeworkGroupChatDto> result = new ArrayList<>();
        if (resp == null || !resp.has("group_chat_list")) {
            return result;
        }
        for (JsonNode item : resp.get("group_chat_list")) {
            String chatId = item.has("chat_id") ? item.get("chat_id").asText() : null;
            String name = item.has("name") ? item.get("name").asText() : null;
            result.add(WeworkGroupChatDto.success(chatId, name));
        }
        return result;
    }

    /**
     * 解析客户群详情响应
     *
     * @param resp 响应 JSON
     * @return 客户群详情
     */
    private WeworkGroupChatDto parseGroupChatDetail(JsonNode resp) {
        if (resp == null || !resp.has("group_chat")) {
            return WeworkGroupChatDto.failure("PARSE_ERROR", "响应缺少 group_chat 字段");
        }
        JsonNode chat = resp.get("group_chat");
        WeworkGroupChatDto dto = WeworkGroupChatDto.success(
                chat.has("chat_id") ? chat.get("chat_id").asText() : null,
                chat.has("name") ? chat.get("name").asText() : null);
        if (chat.has("owner")) {
            dto.setOwnerUserId(chat.get("owner").asText());
        }
        if (chat.has("member_list")) {
            List<WeworkGroupChatDto.GroupMember> members = new ArrayList<>();
            for (JsonNode item : chat.get("member_list")) {
                WeworkGroupChatDto.GroupMember member = WeworkGroupChatDto.GroupMember.builder()
                        .userId(item.has("userid") ? item.get("userid").asText() : null)
                        .type(item.has("type") ? item.get("type").asInt() : null)
                        .joinTime(item.has("join_time") ? LocalDateTime.now() : null)
                        .build();
                members.add(member);
            }
            dto.setMembers(members);
            dto.setMemberCount(members.size());
        }
        return dto;
    }

    /**
     * 解析会话存档列表响应
     *
     * @param resp 响应 JSON
     * @return 会话数据列表
     */
    private List<WeworkChatDataDto> parseChatDataList(JsonNode resp) {
        List<WeworkChatDataDto> result = new ArrayList<>();
        if (resp == null || !resp.has("chatdata")) {
            return result;
        }
        for (JsonNode item : resp.get("chatdata")) {
            WeworkChatDataDto dto = WeworkChatDataDto.success(
                    item.has("msgid") ? item.get("msgid").asText() : null,
                    item.has("seq") ? item.get("seq").asLong() : null);
            if (item.has("publickey_ver")) {
                dto.setPublicAccountId(String.valueOf(item.get("publickey_ver").asLong()));
            }
            if (item.has("from")) {
                dto.setFromUserId(item.get("from").asText());
            }
            if (item.has("tolist")) {
                List<String> toList = new ArrayList<>();
                for (JsonNode to : item.get("tolist")) {
                    toList.add(to.asText());
                }
                dto.setToUserIds(toList);
            }
            if (item.has("roomid")) {
                dto.setRoomId(item.get("roomid").asText());
            }
            if (item.has("action")) {
                dto.setAction(item.get("action").asText());
            }
            if (item.has("msgtime")) {
                dto.setSendTime(LocalDateTime.now());
            }
            if (item.has("msgtype")) {
                dto.setMsgType(item.get("msgtype").asText());
            }
            if (item.has("encrypt_random_key")) {
                dto.setEncryptRandomKey(item.get("encrypt_random_key").asText());
            }
            if (item.has("encrypt_chat_msg")) {
                dto.setEncryptChatMessage(item.get("encrypt_chat_msg").asText());
            }
            result.add(dto);
        }
        return result;
    }

    /**
     * 模拟部门列表(mock 模式使用)
     *
     * @return 部门列表
     */
    private List<Map<String, Object>> mockDepartmentList() {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", 1L);
        root.put("name", "模拟企业");
        root.put("parentid", 0L);
        root.put("order", 1);
        result.add(root);
        return result;
    }

    /**
     * 模拟部门成员列表(mock 模式使用)
     *
     * @return 部门成员列表
     */
    private List<Map<String, Object>> mockDepartmentUserList() {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("userid", "mock_user_1");
        user.put("department", 1L);
        result.add(user);
        return result;
    }
}
