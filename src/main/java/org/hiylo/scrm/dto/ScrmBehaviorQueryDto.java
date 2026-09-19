/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBehaviorQueryDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户行为轨迹查询 DTO。
 * <p>
 * {@code listTracks} 接口入参, 支持按客户 / 行为类型 / 触点 / 时间范围 / 漏斗阶段组合过滤。
 * 所有字段可空, 未填表示不过滤该维度。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmBehaviorQueryDto {

    /** 客户 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 行为类型 (可空): PAGE_VIEW/CLICK/SEARCH/PURCHASE/... */
    @Size(max = 50, message = "行为类型长度不能超过 50")
    private String behaviorType;

    /** 触点 (可空): WEBSITE/APP/WECHAT_OFFICIAL/... */
    @Size(max = 50, message = "触点长度不能超过 50")
    private String touchpoint;

    /** 行为时间起始 (含, 可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss) */
    private LocalDateTime startTime;

    /** 行为时间截止 (含, 可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss) */
    private LocalDateTime endTime;

    /** 漏斗阶段 (可空): AWARENESS/INTEREST/DESIRE/ACTION/RETENTION */
    @Size(max = 30, message = "漏斗阶段长度不能超过 30")
    private String funnelStage;
}
