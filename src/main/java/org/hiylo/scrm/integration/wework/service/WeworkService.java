/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkService.java
 * Date : 2026/09/19 21:20:11
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.integration.wework.service;

import org.hiylo.scrm.integration.wework.dto.WeworkChatDataDto;
import org.hiylo.scrm.integration.wework.dto.WeworkExternalContactDto;
import org.hiylo.scrm.integration.wework.dto.WeworkGroupChatDto;
import org.hiylo.scrm.integration.wework.dto.WeworkMessageSendDto;
import org.hiylo.scrm.integration.wework.dto.WeworkUserDto;

import java.util.List;
import java.util.Map;

/**
 * 企业微信平台服务接口
 * <p>
 * 定义企业微信平台的核心能力，包括：
 * </p>
 * <ul>
 *   <li>通讯录管理 - 部门列表、部门成员、成员详情</li>
 *   <li>外部联系人 - 客户列表、客户详情</li>
 *   <li>客户群 - 群列表、群详情</li>
 *   <li>消息推送 - 单聊外部联系人消息、客户群消息</li>
 *   <li>会话存档 - 会话数据拉取、消息解密</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since 1.0.0
 */
public interface WeworkService {

    /**
     * 通讯录管理 - 获取部门列表
     * <p>对应企业微信 /department/list 接口。</p>
     *
     * @return 部门列表(以 Map 形式返回,包含 id/name/parentid 等字段)
     */
    List<Map<String, Object>> getDepartmentList();

    /**
     * 通讯录管理 - 获取指定部门成员列表
     * <p>对应企业微信 /user/list_id 接口,返回部门下成员 userid 简要信息。</p>
     *
     * @param departmentId 部门ID
     * @return 部门成员列表(以 Map 形式返回)
     */
    List<Map<String, Object>> getDepartmentUserList(Long departmentId);

    /**
     * 通讯录管理 - 获取成员详情
     * <p>对应企业微信 /user/get 接口。</p>
     *
     * @param userId 成员userid
     * @return 用户信息响应
     */
    WeworkUserDto getUserDetail(String userId);

    /**
     * 外部联系人 - 获取外部联系人列表
     * <p>对应企业微信 /externalcontact/list 接口,获取指定成员添加的外部联系人。</p>
     *
     * @param userId 企业成员userid
     * @return 外部联系人列表
     */
    List<WeworkExternalContactDto> getExternalContactList(String userId);

    /**
     * 外部联系人 - 获取外部联系人详情
     * <p>对应企业微信 /externalcontact/get 接口。</p>
     *
     * @param externalUserId 外部联系人userid
     * @return 外部联系人详情
     */
    WeworkExternalContactDto getExternalContactDetail(String externalUserId);

    /**
     * 客户群 - 获取客户群列表
     * <p>对应企业微信 /externalcontact/groupchat/list 接口。</p>
     *
     * @param pageIndex 分页索引(从0开始)
     * @param pageSize  每页数量(最大1000)
     * @return 客户群列表
     */
    List<WeworkGroupChatDto> getGroupChatList(Integer pageIndex, Integer pageSize);

    /**
     * 客户群 - 获取客户群详情
     * <p>对应企业微信 /externalcontact/groupchat/get 接口。</p>
     *
     * @param chatId 客户群ID
     * @return 客户群详情
     */
    WeworkGroupChatDto getGroupChatDetail(String chatId);

    /**
     * 消息推送 - 向外部联系人发送消息
     * <p>对应企业微信「发送应用消息」接口,通过应用消息触达客户。</p>
     *
     * @param userId  接收消息的成员userid
     * @param content 文本内容
     * @return 发送结果
     */
    WeworkMessageSendDto sendMessageToExternal(String userId, String content);

    /**
     * 消息推送 - 发送客户群消息
     * <p>对应企业微信「群机器人」/「应用消息」群消息推送接口。</p>
     *
     * @param chatId  客户群ID
     * @param content 文本内容
     * @return 发送结果
     */
    WeworkMessageSendDto sendGroupMessage(String chatId, String content);

    /**
     * 会话存档 - 拉取会话数据
     * <p>对应企业微信 /msgaudit/check_conversation 接口,获取会话内容存档。</p>
     *
     * @param seq   起始seq(从该seq之后拉取)
     * @param limit 拉取数量(最大1000)
     * @return 会话数据列表
     */
    List<WeworkChatDataDto> getChatDataList(Long seq, Integer limit);

    /**
     * 会话存档 - 解密会话数据
     * <p>使用企业提供的会话存档私钥,对加密消息内容进行解密。</p>
     *
     * @param encryptChatMessage 加密的消息内容
     * @param encryptRandomKey   加密的随机密钥
     * @return 解密后的会话数据
     */
    WeworkChatDataDto decryptChatData(String encryptChatMessage, String encryptRandomKey);

    /**
     * 使 access_token 缓存失效
     * <p>
     * 当企微配置(corpId/secret)发生变更时调用，使当前缓存的 access_token 失效，
     * 下次调用时将使用新配置重新获取 token。
     * </p>
     */
    void invalidateTokenCache();

    /**
     * 判断企微平台是否可用
     * <p>
     * corpId 与 secret 均已配置时视为可用。
     * </p>
     *
     * @return true 表示企微平台可用
     */
    boolean isAvailable();
}
