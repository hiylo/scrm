/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConversationDto.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 会话 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmConversationDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 平台类型 */
    @NotBlank(message = "平台类型不能为空")
    @Size(max = 30, message = "平台类型长度不能超过 30")
    @Pattern(regexp = "wework|douyin|kuaishou|xiaohongshu|bilibili|wechat_personal",
            message = "平台类型仅支持 wework/douyin/kuaishou/xiaohongshu/bilibili/wechat_personal")
    private String platformType;

    /** 账号 ID */
    @NotNull(message = "账号 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户昵称（来自 scrm_customer.nickname，冗余存储避免前端二次查询） */
    private String customerNickname;

    /** 客户头像 URL（来自 scrm_customer.avatar_url） */
    private String customerAvatarUrl;

    /** 账号名称（来自 scrm_account.display_name） */
    private String accountName;

    /** 会话类型：SINGLE / GROUP */
    @NotBlank(message = "会话类型不能为空")
    @Size(max = 20, message = "会话类型长度不能超过 20")
    @Pattern(regexp = "SINGLE|GROUP", message = "会话类型仅支持 SINGLE/GROUP")
    private String conversationType;

    /** 平台会话 ID */
    @Size(max = 200, message = "平台会话 ID 长度不能超过 200")
    private String platformConversationId;

    /** 最后消息时间 */
    private LocalDateTime lastMessageAt;

    /** 最后消息摘要 */
    @Size(max = 500, message = "最后消息摘要长度不能超过 500")
    private String lastMessageSummary;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;

    /** 会话状态：ACTIVE / CLOSED / PENDING */
    private String status;

    /** 消息总数 */
    private Long messageCount;

    /** 未读消息数 */
    private Long unreadCount;
}
