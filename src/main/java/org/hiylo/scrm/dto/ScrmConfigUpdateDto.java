/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConfigUpdateDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 配置值更新 DTO。
 * <p>
 * 用于 {@code setConfigValue} 接口的入参, 携带配置键、新值与变更原因。
* 服务端将校验配置值合法性并写入变更历史。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmConfigUpdateDto {

    /** 配置键 */
    @NotBlank(message = "配置键不能为空")
    @Size(max = 200, message = "配置键长度不能超过 200")
    private String configKey;

    /** 配置值 (可空, 用于清空场景) */
    private String configValue;

    /** 变更原因 (可空) */
    @Size(max = 500, message = "变更原因长度不能超过 500")
    private String changeReason;

    /** 变更人 (可空, 缺省由上下文填充) */
    @Size(max = 100, message = "变更人长度不能超过 100")
    private String changedBy;

    /** IP 地址 (可空) */
    @Size(max = 50, message = "IP 地址长度不能超过 50")
    private String ipAddress;

    /** User-Agent (可空) */
    @Size(max = 500, message = "User-Agent 长度不能超过 500")
    private String userAgent;

    /** 会话 ID (可空) */
    @Size(max = 200, message = "会话 ID 长度不能超过 200")
    private String sessionId;
}
