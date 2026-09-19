/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageReadReportDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 消息阅读上报 DTO。
 * <p>
 * {@code recordRead} / {@code batchRecordReads} 接口入参, 承载阅读上报的核心字段:
 * 消息 ID、阅读者 ID、本次阅读时长与阅读来源。服务端按 messageId 定位跟踪记录后创建阅读
 * 日志、更新跟踪记录的阅读状态并计算互动评分。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmMessageReadReportDto {

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

    /** 本次阅读时长 (秒, 可空, 缺省 0) */
    private Integer readDuration;

    /** 阅读来源: APP/WEB/EMAIL_CLIENT/WECHAT (可空) */
    @Size(max = 50, message = "阅读来源长度不能超过 50")
    private String source;

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
