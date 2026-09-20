/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageRecallDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 消息撤回 DTO。
 * <p>
 * 用于直接创建撤回记录 (不通过撤回动作流程), 承载撤回类型、撤回原因、撤回状态、接收者统计、
 * 撤回窗口等字段。messageTrackingId / messageId / senderId / recalledAt 由服务端在撤回
 * 流程中填充, 此 DTO 主要用于查询响应与外部回填场景。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmMessageRecallDto {

    /** 关联消息跟踪 ID */
    private Long messageTrackingId;

    /** 消息 ID */
    @Size(max = 200, message = "消息 ID 长度不能超过 200")
    private String messageId;

    /** 撤回发起者 ID */
    @Size(max = 100, message = "撤回发起者 ID 长度不能超过 100")
    private String senderId;

    /** 撤回发起者名称 (可空) */
    @Size(max = 100, message = "撤回发起者名称长度不能超过 100")
    private String senderName;

    /** 撤回类型: MANUAL/AUTO/SYSTEM (可空, 缺省 MANUAL) */
    @Size(max = 20, message = "撤回类型长度不能超过 20")
    private String recallType;

    /** 撤回原因 (可空) */
    @Size(max = 500, message = "撤回原因长度不能超过 500")
    private String recallReason;

    /** 撤回状态: SUCCESS/PARTIAL/FAILED/PENDING (可空, 缺省 SUCCESS) */
    @Size(max = 20, message = "撤回状态长度不能超过 20")
    private String recallStatus;

    /** 总接收者数 (可空, 缺省 0) */
    private Integer totalRecipients;

    /** 成功撤回数 (可空, 缺省 0) */
    private Integer successfulRecalls;

    /** 失败撤回数 (可空, 缺省 0) */
    private Integer failedRecalls;

    /** 撤回时间 (可空) */
    private LocalDateTime recalledAt;

    /** 撤回完成时间 (可空) */
    private LocalDateTime completedAt;

    /** 撤回窗口分钟 (可空, 缺省 2) */
    private Integer recallWindowMinutes;

    /** 是否在撤回窗口内 (可空, 缺省 true) */
    private Boolean isWithinWindow;

    /** 失败原因 (可空) */
    @Size(max = 500, message = "失败原因长度不能超过 500")
    private String failureReason;

    /** 已阅读者列表 JSON (可空) */
    @Size(max = 1000, message = "已阅读者列表长度不能超过 1000")
    private String affectedReaders;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;
}
