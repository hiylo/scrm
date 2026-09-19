/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementEventDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 互动行为事件 DTO。
 * <p>
 * 对应 {@code ScrmEngagementEventEntity} 的字段, 主要用于事件查询返回。事件创建请使用
 * {@link ScrmEngagementRecordDto} 作为 recordEvent 接口入参。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmEngagementEventDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 行为类型 */
    private String behaviorType;

    /** 发生渠道 */
    private String channel;

    /** 事件发生时间 */
    private LocalDateTime eventTime;

    /** 得分 */
    private Integer points;

    /** 命中规则 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;

    /** 会话 ID */
    private String sessionId;

    /** 页面 URL */
    private String pageUrl;

    /** 来源 */
    private String referrer;

    /** User-Agent */
    private String userAgent;

    /** 设备类型 */
    private String deviceType;

    /** 地理位置 */
    private String location;

    /** JSON 附加数据 */
    private String metadata;

    /** 客户端 IP */
    private String ip;

    /** 是否已计入评分 */
    private Boolean processed;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
