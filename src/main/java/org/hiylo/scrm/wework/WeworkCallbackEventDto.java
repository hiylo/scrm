/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkCallbackEventDto.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.wework;

import lombok.Data;

/**
 * 企业微信回调事件 DTO
 * <p>
 * 持有从企业微信回调 XML 解析后的事件数据, 用于在 Controller 与 Service 间传递。
 * 字段覆盖外部联系人变更、内部通讯录变更、会话内容审计等主要事件类型。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class WeworkCallbackEventDto {

    /** 事件类型: ChangeType 或 Event (如 change_external_contact / change_contact / enter_chat / msg_audit) */
    private String eventType;

    /** 变更类型: 子事件类型 (如 add_external_contact / del_external_contact / edit_external_contact) */
    private String changeType;

    /** 企业成员 UserID (内部员工) */
    private String userId;

    /** 外部联系人 UserID */
    private String externalUserId;

    /** 部门 ID (通讯录变更时) */
    private String departmentId;

    /** 会话 ID (会话内容审计时) */
    private String chatId;

    /** 事件时间戳 */
    private String timestamp;

    /** 原始解密后的 XML */
    private String rawXml;
}
