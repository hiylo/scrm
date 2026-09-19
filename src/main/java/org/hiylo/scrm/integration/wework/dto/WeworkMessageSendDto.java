/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkMessageSendDto.java
 * Date : 2026-09-19 00:00:00
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
 * 企业微信消息发送 DTO
 * <p>
 * 对应企业微信「发送应用消息」与「发送群欢迎语」等消息推送接口的请求/响应。
 * 同时承载单聊外部联系人消息、客户群消息发送结果。
 * </p>
 *
 * @author Hsi Chu
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class WeworkMessageSendDto {

    /** 是否成功 */
    private Boolean success;

    /** 错误码 */
    private String errorCode;

    /** 错误信息 */
    private String errorMessage;

    /** 接收消息的成员userid */
    private String toUser;

    /** 接收消息的部门ID列表 */
    private List<Long> toParties;

    /** 接收消息的标签ID列表 */
    private List<Long> toTags;

    /** 客户群ID(发送群消息时使用) */
    private String chatId;

    /** 消息类型：text/image/news/textcard/markdown 等 */
    private String msgType;

    /** 消息内容(文本类消息为文本内容,其他类型为JSON字符串) */
    private String content;

    /** 应用agentId */
    private Integer agentId;

    /** 企微返回的msgid */
    private String msgId;

    /** 发送时间 */
    private LocalDateTime sendTime;

    /**
     * 获取接收消息的部门ID列表
     *
     * @return 部门ID列表
     */
    public List<Long> getToParties() {
        return toParties == null ? null : new ArrayList<>(toParties);
    }

    /**
     * 获取接收消息的标签ID列表
     *
     * @return 标签ID列表
     */
    public List<Long> getToTags() {
        return toTags == null ? null : new ArrayList<>(toTags);
    }

    /**
     * 设置接收消息的部门ID列表
     *
     * @param toParties 部门ID列表
     */
    public void setToParties(List<Long> toParties) {
        this.toParties = toParties == null ? null : new ArrayList<>(toParties);
    }

    /**
     * 设置接收消息的标签ID列表
     *
     * @param toTags 标签ID列表
     */
    public void setToTags(List<Long> toTags) {
        this.toTags = toTags == null ? null : new ArrayList<>(toTags);
    }

    /**
     * 构造成功响应
     *
     * @param msgId 企微返回的消息ID
     * @return 成功响应
     */
    public static WeworkMessageSendDto success(String msgId) {
        WeworkMessageSendDto dto = new WeworkMessageSendDto();
        dto.setSuccess(true);
        dto.setMsgId(msgId);
        dto.setSendTime(LocalDateTime.now());
        return dto;
    }

    /**
     * 构造失败响应
     *
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     * @return 失败响应
     */
    public static WeworkMessageSendDto failure(String errorCode, String errorMessage) {
        WeworkMessageSendDto dto = new WeworkMessageSendDto();
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
