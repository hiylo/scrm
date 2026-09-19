/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContentChannelDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 内容分发渠道 DTO。
 * <p>
 * 对应 {@code ScrmContentChannelEntity} 的业务字段, 渠道发布状态与互动指标。
 * channels 字段使用枚举字符串而非 List, 便于透传与校验。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmContentChannelDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 内容 ID */
    @NotNull(message = "内容 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contentId;

/** 渠道: WECHAT_OFFICIAL / WECHAT_MOMENTS / DOUYIN / KUAISHOU / XIAOHONGSHU / BILIBILI / WEIBO / WEBSITE / EMAIL /
         * SMS */
    @Pattern(regexp = "WECHAT_OFFICIAL|WECHAT_MOMENTS|DOUYIN|KUAISHOU|XIAOHONGSHU|BILIBILI|WEIBO|WEBSITE|EMAIL|SMS",
            message =
                    "渠道仅支持 WECHAT_OFFICIAL/WECHAT_MOMENTS/DOUYIN/KUAISHOU/XIAOHONGSHU/BILIBILI/WEIBO/WEBSITE/EMAIL/SMS")
    private String channel;

    /** 渠道帖子 ID (可空) */
    @Size(max = 200, message = "渠道帖子 ID 长度不能超过 200")
    private String channelPostId;

    /** 渠道帖子 URL (可空) */
    @Size(max = 500, message = "渠道帖子 URL 长度不能超过 500")
    private String channelPostUrl;

    /** 发布状态: PENDING / PUBLISHING / PUBLISHED / FAILED / REMOVED (查询返回) */
    @Pattern(regexp = "PENDING|PUBLISHING|PUBLISHED|FAILED|REMOVED",
            message = "发布状态仅支持 PENDING/PUBLISHING/PUBLISHED/FAILED/REMOVED")
    private String status;

    /** 发布时间 (查询返回) */
    private LocalDateTime publishedAt;

    /** 浏览数 (查询返回) */
    private Integer viewCount;

    /** 点赞数 (查询返回) */
    private Integer likeCount;

    /** 分享数 (查询返回) */
    private Integer shareCount;

    /** 评论数 (查询返回) */
    private Integer commentCount;

    /** 转化数 (查询返回) */
    private Integer conversionCount;

    /** 错误信息 (查询返回) */
    private String errorMessage;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
