/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignLaunchDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 营销活动渠道启动 DTO。
 * <p>
 * 启动活动渠道发送的入参, 携带活动 ID 与各渠道启动配置。服务端遍历 channelConfigs,
 * 逐渠道记录发送 (状态 PENDING → SENDING → SENT) 并更新发送计数与活动实际花费。
 * channelConfigs 为空时启动活动下全部 PENDING 渠道。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCampaignLaunchDto {

    /** 活动 ID */
    @NotNull(message = "活动 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long campaignId;

    /** 渠道启动配置列表 (为空时启动活动下全部 PENDING 渠道) */
    @Valid
    private List<ChannelLaunchConfig> channelConfigs;

    /**
     * 渠道启动配置。
     *
     * @author Hsi Chu
     * @since V1.0
     */
    @Data
    public static class ChannelLaunchConfig {

        /** 渠道: WECHAT / WORK_WECHAT / SMS / EMAIL / DOUYIN / KUAISHOU / XIAOHONGSHU / BILIBILI */
        @NotBlank(message = "渠道不能为空")
        @Pattern(regexp = "WECHAT|WORK_WECHAT|SMS|EMAIL|DOUYIN|KUAISHOU|XIAOHONGSHU|BILIBILI",
                message = "渠道仅支持 WECHAT/WORK_WECHAT/SMS/EMAIL/DOUYIN/KUAISHOU/XIAOHONGSHU/BILIBILI")
        private String channel;

        /** 目标人数 (缺省取现有渠道记录) */
        private Integer targetCount;

        /** 内容标题 (可空, 覆盖现有渠道配置) */
        @Size(max = 200, message = "内容标题长度不能超过 200")
        private String contentTitle;

        /** 内容正文 (可空, 覆盖现有渠道配置) */
        private String contentBody;

        /** 内容图片 URL (可空) */
        @Size(max = 500, message = "内容图片 URL 长度不能超过 500")
        private String contentImage;

        /** 跳转链接 (可空) */
        @Size(max = 500, message = "跳转链接长度不能超过 500")
        private String linkUrl;

        /** 计划发送时间 (可空) */
        private LocalDateTime scheduledAt;

        /** 渠道花费 (可空, 缺省 0) */
        private Double cost;
    }
}
