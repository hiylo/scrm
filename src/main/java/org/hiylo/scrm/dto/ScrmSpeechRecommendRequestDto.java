/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechRecommendRequestDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * SCRM 销售话术推荐请求 DTO。
 * <p>
 * 推荐接口入参: {@link #customerId} 标识目标客户 (可空, 表示匿名推荐),
 * {@link #scenarioCode} 指定场景编码, {@link #context} 携带匹配上下文
 * (channel / productCategory / sentiment / customerStage)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmSpeechRecommendRequestDto {

    /** 客户 ID (可空, 表示匿名推荐) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    private String customerName;

    /** 场景编码 */
    @NotBlank(message = "场景编码不能为空")
    private String scenarioCode;

    /** 匹配上下文 (可空, 缺省字段由服务端补全) */
    private RecommendContext context;

    /** 推荐人 ID (可空) */
    private String recommendedBy;

    /** 推荐人名称 (可空) */
    private String recommendedByName;

    /**
     * 推荐匹配上下文。
     * <p>所有字段可空, 缺省字段不参与匹配。</p>
     * @author Hsi Chu
     */
    @Data
    public static class RecommendContext {
        /** 渠道 (如 wechat / douyin / xhs) */
        private String channel;

        /** 产品类别 */
        private String productCategory;

        /** 客户情感: POSITIVE / NEUTRAL / NEGATIVE */
        private String sentiment;

        /** 客户阶段: NEW / ACTIVE / AT_RISK / CHURNED / VIP / PROSPECT */
        private String customerStage;

        /** 时段 (如 MORNING / AFTERNOON / EVENING) */
        private String timeOfDay;

        /** 上次互动内容 (用于关键词匹配) */
        private String previousInteraction;
    }
}
