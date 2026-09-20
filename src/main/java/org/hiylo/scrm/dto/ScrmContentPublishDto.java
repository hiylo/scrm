/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContentPublishDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 内容多渠道发布 DTO。
 * <p>
 * 多渠道发布接口入参, 携带内容 ID 与目标渠道列表。{@code scheduledAt} 非空时表示
 * 定时发布 (创建排期任务并延后执行), 为空时立即发布 (模拟)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmContentPublishDto {

    /** 内容 ID */
    @NotNull(message = "内容 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contentId;

    /** 渠道列表 (枚举字符串: WECHAT_OFFICIAL/WECHAT_MOMENTS/DOUYIN/KUAISHOU/XIAOHONGSHU/BILIBILI/WEIBO/WEBSITE/EMAIL/SMS) */
    @NotEmpty(message = "渠道列表不能为空")
    private List<@Pattern(
            regexp = "WECHAT_OFFICIAL|WECHAT_MOMENTS|DOUYIN|KUAISHOU|XIAOHONGSHU|BILIBILI|WEIBO|WEBSITE|EMAIL|SMS",
            message = "渠道仅支持 WECHAT_OFFICIAL/WECHAT_MOMENTS/DOUYIN/KUAISHOU/XIAOHONGSHU/BILIBILI/WEIBO/WEBSITE/EMAIL/SMS")
            String> channels;

    /** 计划发布时间 (可空, 为空表示立即发布) */
    private LocalDateTime scheduledAt;
}
