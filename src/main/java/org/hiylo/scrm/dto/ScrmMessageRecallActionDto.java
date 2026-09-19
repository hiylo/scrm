/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageRecallActionDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 消息撤回动作 DTO。
 * <p>
 * {@code recallMessage} 接口入参, 仅需指定要撤回的消息 ID 与撤回原因。服务端按 messageId
 * 定位跟踪记录后, 校验撤回窗口、更新跟踪状态、创建撤回记录并处理已阅读者 (模拟实现)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmMessageRecallActionDto {

    /** 消息 ID */
    @NotBlank(message = "消息 ID 不能为空")
    @Size(max = 200, message = "消息 ID 长度不能超过 200")
    private String messageId;

    /** 撤回原因 (可空) */
    @Size(max = 500, message = "撤回原因长度不能超过 500")
    private String reason;
}
