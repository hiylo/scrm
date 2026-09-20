/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAccountDto.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 平台账号 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAccountDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 平台类型：wework / douyin / kuaishou / xiaohongshu / bilibili / wechat_personal */
    @NotBlank(message = "平台类型不能为空")
    @Size(max = 30, message = "平台类型长度不能超过 30")
    @Pattern(regexp = "wework|douyin|kuaishou|xiaohongshu|bilibili|wechat_personal",
            message = "平台类型仅支持 wework/douyin/kuaishou/xiaohongshu/bilibili/wechat_personal")
    private String platformType;

    /** 平台内部账号唯一标识 */
    @NotBlank(message = "平台账号 UID 不能为空")
    @Size(max = 200, message = "平台账号 UID 长度不能超过 200")
    private String platformAccountUid;

    /** 账号名称 (如微信号 / 手机号 / 登录名, 区别于展示昵称) */
    @Size(max = 200, message = "账号名称长度不能超过 200")
    private String accountName;

    /** 账号展示名称 */
    @Size(max = 200, message = "展示名称长度不能超过 200")
    private String displayName;

    /** 账号头像 URL */
    @Size(max = 500, message = "头像 URL 长度不能超过 500")
    private String avatarUrl;

    /** 关联 scrm-server 设备 ID */
    @Size(max = 100, message = "设备 ID 长度不能超过 100")
    private String deviceId;

    /** 关联人设 ID（可空） */
    @Size(max = 100, message = "人设 ID 长度不能超过 100")
    private String personaId;

    /** 登录态：LOGIN / LOGOUT / FROZEN / UNKNOWN */
    @Size(max = 20, message = "登录态长度不能超过 20")
    @Pattern(regexp = "LOGIN|LOGOUT|FROZEN|UNKNOWN",
            message = "登录态仅支持 LOGIN/LOGOUT/FROZEN/UNKNOWN")
    private String loginState;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
