/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkChatDataDto.java
 * Date : 2026/09/19 21:20:11
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.integration.wework.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 企业微信会话存档 DTO
 * <p>
 * 对应企业微信「会话内容存档」接口返回的会话数据。会话存档需企业开通会话存档功能，
 * 并使用企业提供的 SDK 进行消息加解密。该 DTO 同时承载会话数据列表与解密结果。
 * </p>
 *
 * @author Hsi Chu
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class WeworkChatDataDto {

    /** 是否成功 */
    private Boolean success;

    /** 错误码 */
    private String errorCode;

    /** 错误信息 */
    private String errorMessage;

    /** 会话数据seq(用于下次拉取) */
    private Long seq;

    /** 消息ID */
    private String msgId;

    /** 公开账号ID */
    private String publicAccountId;

    /** 消息发送方userid */
    private String fromUserId;

    /** 消息接收方userid列表 */
    private List<String> toUserIds;

    /** 房间ID(群聊时存在) */
    private String roomId;

    /** 消息动作：send=发送，recall=撤回，switch=切换企业日志 */
    private String action;

    /** 消息发送时间 */
    private LocalDateTime sendTime;

    /** 消息类型：text/image/voice/video/file 等 */
    private String msgType;

    /** 加密的消息内容(原始) */
    private String encryptRandomKey;

    /** 加密的消息内容 */
    private String encryptChatMessage;

    /** 解密后的消息内容 */
    private String plainChatMessage;

    /**
     * 获取消息接收方userid列表
     *
     * @return 接收方userid列表
     */
    public List<String> getToUserIds() {
        return toUserIds == null ? null : new ArrayList<>(toUserIds);
    }

    /**
     * 设置消息接收方userid列表
     *
     * @param toUserIds 接收方userid列表
     */
    public void setToUserIds(List<String> toUserIds) {
        this.toUserIds = toUserIds == null ? null : new ArrayList<>(toUserIds);
    }

    /**
     * 构造成功响应
     *
     * @param msgId 消息ID
     * @param seq   会话seq
     * @return 成功响应
     */
    public static WeworkChatDataDto success(String msgId, Long seq) {
        WeworkChatDataDto dto = new WeworkChatDataDto();
        dto.setSuccess(true);
        dto.setMsgId(msgId);
        dto.setSeq(seq);
        return dto;
    }

    /**
     * 构造失败响应
     *
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     * @return 失败响应
     */
    public static WeworkChatDataDto failure(String errorCode, String errorMessage) {
        WeworkChatDataDto dto = new WeworkChatDataDto();
        dto.setSuccess(false);
        dto.setErrorCode(errorCode);
        dto.setErrorMessage(errorMessage);
        return dto;
    }

    /**
     * 检查是否成功
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }
}
