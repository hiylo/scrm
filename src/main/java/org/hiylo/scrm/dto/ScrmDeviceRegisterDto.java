/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDeviceRegisterDto.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 设备注册 DTO。
 * <p>
 * 业务员 APP 启动并获取到个推 client_id 后, 调用 {@code POST /scrm/push/register}
 * 上报本设备信息, 服务端 upsert 到 {@code scrm_user_device} 表, 用于后续推送。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmDeviceRegisterDto {

    /** 个推 client_id, 推送目标标识 */
    @NotBlank(message = "clientId 不能为空")
    @Size(max = 128, message = "clientId 长度不能超过 128")
    private String clientId;

    /** 设备唯一标识（可空） */
    @Size(max = 128, message = "deviceId 长度不能超过 128")
    private String deviceId;

    /** 平台: android / ios（默认 android） */
    @NotBlank(message = "platform 不能为空")
    @Size(max = 16, message = "platform 长度不能超过 16")
    private String platform = "android";
}
