/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCampaignParticipantDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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
 * SCRM 营销活动参与者 DTO。
 * <p>
 * 对应 {@code ScrmMarketingCampaignParticipantEntity} 的业务字段, 记录客户参与轨迹。
 * actions 为 JSON 数组字符串: {@code ["VIEW", "CLICK", "PURCHASE"]}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmMarketingCampaignParticipantDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 活动 ID */
    @NotNull(message = "活动 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long campaignId;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 参与渠道: WECHAT / WORK_WECHAT / SMS / EMAIL / DOUYIN / KUAISHOU / XIAOHONGSHU / BILIBILI */
    @NotBlank(message = "参与渠道不能为空")
    @Pattern(regexp = "WECHAT|WORK_WECHAT|SMS|EMAIL|DOUYIN|KUAISHOU|XIAOHONGSHU|BILIBILI",
            message = "渠道仅支持 WECHAT/WORK_WECHAT/SMS/EMAIL/DOUYIN/KUAISHOU/XIAOHONGSHU/BILIBILI")
    private String channel;

    /** 参与时间 (缺省取当前时间) */
    private LocalDateTime participatedAt;

    /** 参与动作 JSON 数组: [VIEW / CLICK / SHARE / PURCHASE / COMMENT] */
    @Size(max = 500, message = "参与动作长度不能超过 500")
    private String actions;

    /** 是否转化 (更新转化时使用) */
    private Boolean converted;

    /** 转化时间 (可空) */
    private LocalDateTime convertedAt;

    /** 转化金额 (默认 0) */
    private Double conversionValue;

    /** 回复内容 (可空) */
    @Size(max = 500, message = "回复内容长度不能超过 500")
    private String responseContent;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
