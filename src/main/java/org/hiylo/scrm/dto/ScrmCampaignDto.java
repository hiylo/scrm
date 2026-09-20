/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignDto.java
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
 * SCRM 营销任务 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCampaignDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 200, message = "任务名称长度不能超过 200")
    private String campaignName;

    /** 任务类型：AUTO_ADD_FRIEND / AUTO_POST / AUTO_CHAT / AUTO_NURTURE / AUTO_REPLY */
    @NotBlank(message = "任务类型不能为空")
    @Size(max = 30, message = "任务类型长度不能超过 30")
    @Pattern(regexp = "AUTO_ADD_FRIEND|AUTO_POST|AUTO_CHAT|AUTO_NURTURE|AUTO_REPLY",
            message = "任务类型仅支持 AUTO_ADD_FRIEND/AUTO_POST/AUTO_CHAT/AUTO_NURTURE/AUTO_REPLY")
    private String campaignType;

    /** 平台类型 */
    @NotBlank(message = "平台类型不能为空")
    @Size(max = 30, message = "平台类型长度不能超过 30")
    @Pattern(regexp = "wework|douyin|kuaishou|xiaohongshu|bilibili|wechat_personal",
            message = "平台类型仅支持 wework/douyin/kuaishou/xiaohongshu/bilibili/wechat_personal")
    private String platformType;

    /** 关联 scrm Fleet ID（可空） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fleetId;

    /** 任务状态：DRAFT / RUNNING / PAUSED / COMPLETED / FAILED */
    @Size(max = 20, message = "任务状态长度不能超过 20")
    @Pattern(regexp = "DRAFT|RUNNING|PAUSED|COMPLETED|FAILED",
            message = "任务状态仅支持 DRAFT/RUNNING/PAUSED/COMPLETED/FAILED")
    private String status;

    /** cron 表达式（可空） */
    @Size(max = 100, message = "cron 表达式长度不能超过 100")
    private String cronExpression;

    /** 任务开始时间 */
    private LocalDateTime startTime;

    /** 任务结束时间 */
    private LocalDateTime endTime;

    /** 执行任务 ID（可空，历史列名为 behavior_flow_id, 语义已变更为执行任务登记 ID） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long behaviorFlowId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
