/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageReadLogDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 消息阅读日志 DTO。
 * <p>
 * 用于直接创建阅读日志 (不通过阅读上报流程), 承载阅读者、阅读时间、阅读时长、阅读来源
 * 与设备环境等字段。messageTrackingId / messageId / readerId / readAt 为必填, 其余可选。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmMessageReadLogDto {

    /** 关联消息跟踪 ID */
    private Long messageTrackingId;

    /** 消息 ID */
    @NotBlank(message = "消息 ID 不能为空")
    @Size(max = 200, message = "消息 ID 长度不能超过 200")
    private String messageId;

    /** 阅读者 ID */
    @NotBlank(message = "阅读者 ID 不能为空")
    @Size(max = 200, message = "阅读者 ID 长度不能超过 200")
    private String readerId;

    /** 阅读者名称 (可空) */
    @Size(max = 200, message = "阅读者名称长度不能超过 200")
    private String readerName;

    /** 阅读者类型: CUSTOMER/AGENT/SYSTEM (可空, 缺省 CUSTOMER) */
    @Size(max = 20, message = "阅读者类型长度不能超过 20")
    private String readerType;

    /** 阅读时间 (可空, 缺省由服务端填充当前时间) */
    private LocalDateTime readAt;

    /** 本次阅读时长 (秒, 可空, 缺省 0) */
    private Integer readDurationSeconds;

    /** 阅读来源: APP/WEB/EMAIL_CLIENT/WECHAT (可空) */
    @Size(max = 50, message = "阅读来源长度不能超过 50")
    private String readSource;

    /** 设备类型 (可空) */
    @Size(max = 30, message = "设备类型长度不能超过 30")
    private String deviceType;

    /** 操作系统 (可空) */
    @Size(max = 50, message = "操作系统长度不能超过 50")
    private String os;

    /** 浏览器 (可空) */
    @Size(max = 100, message = "浏览器长度不能超过 100")
    private String browser;

    /** 客户端 IP (可空) */
    @Size(max = 100, message = "客户端 IP 长度不能超过 100")
    private String clientIp;

    /** 地理位置 (可空) */
    @Size(max = 200, message = "地理位置长度不能超过 200")
    private String location;
}
