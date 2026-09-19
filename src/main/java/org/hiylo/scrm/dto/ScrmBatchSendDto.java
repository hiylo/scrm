/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBatchSendDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * SCRM 通知批量发送 DTO。
 * <p>
 * 用于 {@code ScrmNotificationCenterService.sendBatch} 接口入参: 创建一个批次并向多个接收者
 * 批量发送同一条通知。batchName 标识批次名称, templateCode 指定来源模板, channel 覆盖模板渠道
 * (用于跨渠道批量发送), recipientIds 为接收者 ID 集合, variables 为模板变量映射。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmBatchSendDto {

    /** 批次名称 */
    @NotBlank(message = "批次名称不能为空")
    @Size(max = 200, message = "批次名称长度不能超过 200")
    private String batchName;

    /** 模板编码 */
    @NotBlank(message = "模板编码不能为空")
    @Size(max = 100, message = "模板编码长度不能超过 100")
    private String templateCode;

    /** 通知渠道: IN_APP/EMAIL/SMS/PUSH/WEBHOOK (覆盖模板渠道) */
    @NotBlank(message = "通知渠道不能为空")
    @Pattern(regexp = "IN_APP|EMAIL|SMS|PUSH|WEBHOOK",
            message = "通知渠道仅支持 IN_APP/EMAIL/SMS/PUSH/WEBHOOK")
    private String channel;

    /** 接收者类型: USER/CUSTOMER/EXTERNAL/ROLE/DEPARTMENT (可空, 默认 USER) */
    @Pattern(regexp = "USER|CUSTOMER|EXTERNAL|ROLE|DEPARTMENT",
            message = "接收者类型仅支持 USER/CUSTOMER/EXTERNAL/ROLE/DEPARTMENT")
    private String recipientType;

    /** 接收者 ID 列表 */
    @NotEmpty(message = "接收者 ID 列表不能为空")
    private List<String> recipientIds;

    /** 模板变量 (key=变量名, value=变量值) */
    private Map<String, String> variables;

    /** 优先级 (可空, 默认 0) */
    private Integer priority;

    /** 发送者 ID (可空) */
    @Size(max = 100, message = "发送者 ID 长度不能超过 100")
    private String senderId;

    /** 发送者名称 (可空) */
    @Size(max = 100, message = "发送者名称长度不能超过 100")
    private String senderName;

    /** 触发人 */
    @NotBlank(message = "触发人不能为空")
    @Size(max = 100, message = "触发人长度不能超过 100")
    private String triggeredBy;
}
