/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkGroupChatDto.java
 * Date : 2026/09/19 21:20:11
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.integration.wework.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 企业微信客户群 DTO
 * <p>
 * 对应企业微信「客户群管理」接口返回的客户群信息，包含群ID、群名称、
 * 群主、群成员列表等字段。同时承载客户群列表查询与详情查询结果。
 * </p>
 *
 * @author Hsi Chu
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class WeworkGroupChatDto {

    /** 是否成功 */
    private Boolean success;

    /** 错误码 */
    private String errorCode;

    /** 错误信息 */
    private String errorMessage;

    /** 客户群ID */
    private String chatId;

    /** 群名称 */
    private String name;

    /** 群主userid */
    private String ownerUserId;

    /** 群创建时间 */
    private LocalDateTime createTime;

    /** 群成员列表 */
    private List<GroupMember> members;

    /** 群成员数量 */
    private Integer memberCount;

    /**
     * 获取客户群的群成员列表
     *
     * @return 群成员列表
     */
    public List<GroupMember> getMembers() {
        return members == null ? null : new ArrayList<>(members);
    }

    /**
     * 设置客户群的群成员列表
     *
     * @param members 群成员列表
     */
    public void setMembers(List<GroupMember> members) {
        this.members = members == null ? null : new ArrayList<>(members);
    }

    /**
     * 客户群成员
     *
     * @author Hsi Chu
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupMember {

        /** 成员userid */
        private String userId;

        /** 成员类型：1=企业成员，2=外部联系人 */
        private Integer type;

        /** 加入群时间 */
        private LocalDateTime joinTime;

        /** 成员所在企业名称(仅外部联系人有) */
        private String corpName;
    }

    /**
     * 构造成功响应
     *
     * @param chatId 群ID
     * @param name   群名称
     * @return 成功响应
     */
    public static WeworkGroupChatDto success(String chatId, String name) {
        WeworkGroupChatDto dto = new WeworkGroupChatDto();
        dto.setSuccess(true);
        dto.setChatId(chatId);
        dto.setName(name);
        return dto;
    }

    /**
     * 构造失败响应
     *
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     * @return 失败响应
     */
    public static WeworkGroupChatDto failure(String errorCode, String errorMessage) {
        WeworkGroupChatDto dto = new WeworkGroupChatDto();
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
