/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationSendDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 通知发送 DTO。
 * <p>
 * 用于 {@code ScrmNotificationCenterService.sendNotification} 接口入参: 指定模板编码, 接收者列表
 * (接收者 ID 集合), 变量映射 (渲染模板时替换占位符)。可选指定接收者类型, 优先级, 计划发送时间,
 * 发送者信息与关联业务对象。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmNotificationSendDto {

    /** 模板编码 */
    @NotBlank(message = "模板编码不能为空")
    @Size(max = 100, message = "模板编码长度不能超过 100")
    private String templateCode;

    /** 接收者类型: USER/CUSTOMER/EXTERNAL/ROLE/DEPARTMENT (可空, 默认 USER) */
    @Pattern(regexp = "USER|CUSTOMER|EXTERNAL|ROLE|DEPARTMENT",
            message = "接收者类型仅支持 USER/CUSTOMER/EXTERNAL/ROLE/DEPARTMENT")
    private String recipientType;

    /** 接收者 ID 列表 */
    @NotEmpty(message = "接收者列表不能为空")
    private List<String> recipients;

    /** 模板变量 (key=变量名, value=变量值) */
    private Map<String, String> variables;

    /** 优先级 (可空, 默认 0) */
    private Integer priority;

    /** 计划发送时间 (可空, 为空则立即发送) */
    private LocalDateTime scheduledAt;

    /** 发送者 ID (可空) */
    @Size(max = 100, message = "发送者 ID 长度不能超过 100")
    private String senderId;

    /** 发送者名称 (可空) */
    @Size(max = 100, message = "发送者名称长度不能超过 100")
    private String senderName;

    /** 接收者联系方式: 手机/邮箱 (可空, 用于邮件/短信渠道) */
    @Size(max = 200, message = "接收者联系方式长度不能超过 200")
    private String recipientContact;

    /** 关联类型 (可空) */
    @Size(max = 50, message = "关联类型长度不能超过 50")
    private String relatedType;

    /** 关联 ID (可空) */
    @Size(max = 100, message = "关联 ID 长度不能超过 100")
    private String relatedId;
}
