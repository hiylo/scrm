/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWeworkController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.integration.wework.service.WeworkService;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.integration.wework.dto.WeworkExternalContactDto;
import org.hiylo.scrm.integration.wework.dto.WeworkGroupChatDto;
import org.hiylo.scrm.integration.wework.dto.WeworkMessageSendDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 企业微信业务控制器
 * <p>
 * 暴露企业微信开放 API 能力, 包括:
 * <ul>
 *   <li>发送私聊消息 / 客户群消息</li>
 *   <li>查询外部联系人 (客户) 列表</li>
 *   <li>查询客户群列表</li>
 *   <li>查询部门列表 / 部门成员</li>
 * </ul>
 * 通过 {@link WeworkService}
 * 路由到企微开放 API, 支持按账号配置切换。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/wework")
@RequiredArgsConstructor
public class ScrmWeworkController {

    /** 企微业务适配层 (按账号感知) */
    /** 企业微信开放 API 客户端 */
    private final WeworkService weworkPlatform;

    /**
     * 发送私聊消息
     *
     * @param userId  接收消息的企业成员 userid
     * @param content 消息内容
     * @return 发送结果
     */
    @RequirePermission(resource = "scrm_wework", action = "send")
    @PostMapping("/messages/send")
    public OperationResponse<WeworkMessageSendDto> sendChatMessage(
            @RequestParam String userId,
            @RequestParam String content) {
        log.info("企微发送私聊消息: userId={}", userId);
        return OperationResponse.build(weworkPlatform.sendMessageToExternal(userId, content));
    }

    /**
     * 发送客户群消息
     *
     * @param chatId  客户群 ID
     * @param content 消息内容
     * @return 发送结果
     */
    @RequirePermission(resource = "scrm_wework", action = "send")
    @PostMapping("/group-messages/send")
    public OperationResponse<WeworkMessageSendDto> sendGroupMessage(
            @RequestParam String chatId,
            @RequestParam String content) {
        log.info("企微发送客户群消息: chatId={}", chatId);
        return OperationResponse.build(weworkPlatform.sendGroupMessage(chatId, content));
    }

    /**
     * 获取外部联系人 (客户) 列表
     *
     * @param userId 企业成员 userid
     * @return 外部联系人列表
     */
    @RequirePermission(resource = "scrm_wework", action = "read")
    @GetMapping("/external-contacts")
    public OperationResponse<List<WeworkExternalContactDto>> getExternalContacts(
            @RequestParam String userId) {
        log.info("企微获取外部联系人: userId={}", userId);
        return OperationResponse.build(weworkPlatform.getExternalContactList(userId));
    }

    /**
     * 获取客户群列表
     *
     * @param pageIndex 分页索引 (从 0 开始)
     * @param pageSize  每页数量 (最大 1000)
     * @return 客户群列表
     */
    @RequirePermission(resource = "scrm_wework", action = "read")
    @GetMapping("/group-chats")
    public OperationResponse<List<WeworkGroupChatDto>> getGroupChats(
            @RequestParam(defaultValue = "0") Integer pageIndex,
            @RequestParam(defaultValue = "100") Integer pageSize) {
        log.info("企微获取客户群列表: pageIndex={}, pageSize={}",
                pageIndex, pageSize);
        return OperationResponse.build(weworkPlatform.getGroupChatList(pageIndex, pageSize));
    }

    /**
     * 获取部门列表
     *
     * @return 部门列表 (包含 id / name / parentid)
     */
    @RequirePermission(resource = "scrm_wework", action = "read")
    @GetMapping("/departments")
    public OperationResponse<List<Map<String, Object>>> getDepartmentList() {
        log.info("企微获取部门列表:");
        return OperationResponse.build(weworkPlatform.getDepartmentList());
    }

    /**
     * 获取部门成员列表
     *
     * @param departmentId 部门 ID
     * @return 部门成员列表
     */
    @RequirePermission(resource = "scrm_wework", action = "read")
    @GetMapping("/departments/{departmentId}/users")
    public OperationResponse<List<Map<String, Object>>> getDepartmentUsers(
            @PathVariable Long departmentId) {
        log.info("企微获取部门成员: departmentId={}", departmentId);
        return OperationResponse.build(weworkPlatform.getDepartmentUserList(departmentId));
    }
}
