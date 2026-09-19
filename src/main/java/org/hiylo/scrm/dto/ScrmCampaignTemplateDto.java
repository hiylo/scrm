/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignTemplateDto.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM SOP 模板 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCampaignTemplateDto {

    /** 主键 ID */
    private Long id;

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名称长度不能超过 200")
    private String templateName;

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

    /** 模板内容（JSON，行为流模板） */
    @NotBlank(message = "模板内容不能为空")
    private String templateContent;

    /** 模板描述 */
    @Size(max = 500, message = "模板描述长度不能超过 500")
    private String description;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
