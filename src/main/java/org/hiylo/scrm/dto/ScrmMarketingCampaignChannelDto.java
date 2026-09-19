/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCampaignChannelDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 营销活动渠道 DTO。
 * <p>
 * 对应 {@code ScrmMarketingCampaignChannelEntity} 的业务字段, 渠道内容配置与发送指标。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmMarketingCampaignChannelDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 活动 ID (创建时必填) */
    @NotNull(message = "活动 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long campaignId;

    /** 渠道: WECHAT / WORK_WECHAT / SMS / EMAIL / DOUYIN / KUAISHOU / XIAOHONGSHU / BILIBILI */
    @NotBlank(message = "渠道不能为空")
    @Pattern(regexp = "WECHAT|WORK_WECHAT|SMS|EMAIL|DOUYIN|KUAISHOU|XIAOHONGSHU|BILIBILI",
            message = "渠道仅支持 WECHAT/WORK_WECHAT/SMS/EMAIL/DOUYIN/KUAISHOU/XIAOHONGSHU/BILIBILI")
    private String channel;

    /** 渠道配置 JSON (可空) */
    private String channelConfig;

    /** 内容标题 (可空) */
    @Size(max = 200, message = "内容标题长度不能超过 200")
    private String contentTitle;

    /** 内容正文 (可空) */
    private String contentBody;

    /** 内容图片 URL (可空) */
    @Size(max = 500, message = "内容图片 URL 长度不能超过 500")
    private String contentImage;

    /** 跳转链接 (可空) */
    @Size(max = 500, message = "跳转链接长度不能超过 500")
    private String linkUrl;

    /** 计划发送时间 (可空) */
    private LocalDateTime scheduledAt;

    /** 实际发送时间 (查询返回) */
    private LocalDateTime sentAt;

    /** 目标人数 (默认 0) */
    private Integer targetCount;

    /** 已发送数 (查询返回) */
    private Integer sentCount;

    /** 已送达数 (查询返回) */
    private Integer deliveredCount;

    /** 已读数 (查询返回) */
    private Integer readCount;

    /** 点击数 (查询返回) */
    private Integer clickCount;

    /** 转化数 (查询返回) */
    private Integer convertCount;

    /** 渠道花费 (默认 0) */
    private Double cost;

    /** 状态: PENDING / SENDING / SENT / FAILED (查询返回) */
    @Pattern(regexp = "PENDING|SENDING|SENT|FAILED",
            message = "渠道状态仅支持 PENDING/SENDING/SENT/FAILED")
    private String status;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
