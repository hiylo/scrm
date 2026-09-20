/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPlatformConfigDto.java
 * Date : 2026/07/29 21:19:51
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
 * SCRM 平台配置 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmPlatformConfigDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 平台类型：wework / douyin / kuaishou / xiaohongshu / bilibili / wechat_personal */
    @NotBlank(message = "平台类型不能为空")
    @Size(max = 30, message = "平台类型长度不能超过 30")
    @Pattern(regexp = "wework|douyin|kuaishou|xiaohongshu|bilibili|wechat_personal",
            message = "平台类型仅支持 wework/douyin/kuaishou/xiaohongshu/bilibili/wechat_personal")
    private String platformType;

    /** 企业ID（企微 CorpID） */
    @Size(max = 200, message = "CorpID 长度不能超过 200")
    private String corpId;

    /** 应用ID（企微 AgentID） */
    private Integer agentId;

    /** 应用Secret */
    @Size(max = 500, message = "Secret 长度不能超过 500")
    private String secret;

    /** 消息加解密密钥（EncodingAESKey） */
    @Size(max = 100, message = "AES Key 长度不能超过 100")
    private String aesKey;

    /** 回调 Token */
    @Size(max = 200, message = "Token 长度不能超过 200")
    private String token;

    /** 平台 API 基础地址 */
    @Size(max = 500, message = "基础地址长度不能超过 500")
    private String baseUrl;

    /** 回调 URL */
    @Size(max = 500, message = "回调 URL 长度不能超过 500")
    private String callbackUrl;

    /** 是否启用模拟模式 */
    private boolean mockMode = true;

    /** 是否启用真实 API 调用 */
    private boolean realApiEnabled = false;

    /** 请求超时时间（毫秒） */
    private int timeout = 30000;

    /** 重试次数 */
    private int retryCount = 3;

    /** 连接状态：CONNECTED / DISCONNECTED / UNKNOWN */
    @Size(max = 20, message = "连接状态长度不能超过 20")
    @Pattern(regexp = "CONNECTED|DISCONNECTED|UNKNOWN|",
            message = "连接状态仅支持 CONNECTED/DISCONNECTED/UNKNOWN")
    private String connectionStatus;

    /** 最后连接测试时间 */
    private LocalDateTime lastTestedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
