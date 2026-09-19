/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBehaviorPathDto.java
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
 * SCRM 客户行为路径 DTO。
 * <p>
 * 用于路径详情查询返回。{@code behaviorSequence} 为 JSON 行为序列字符串:
 * {@code [{type, time, touchpoint, page}]}, {@code touchpoints} 为触点逗号分隔串。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmBehaviorPathDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 路径名称 (可空) */
    @Size(max = 200, message = "路径名称长度不能超过 200")
    private String pathName;

    /** 客户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 会话开始时间 */
    private LocalDateTime sessionStartTime;

    /** 会话结束时间 (可空) */
    private LocalDateTime sessionEndTime;

    /** 经过的触点 (逗号分隔, 可空) */
    @Size(max = 1000, message = "触点列表长度不能超过 1000")
    private String touchpoints;

    /** JSON 行为序列: [{type, time, touchpoint, page}] */
    private String behaviorSequence;

    /** 总行为数 */
    private Integer totalBehaviors;

    /** 总停留时长 (秒) */
    private Integer totalDurationSeconds;

    /** 触点数 */
    private Integer touchpointCount;

    /** 是否含转化行为 */
    private Boolean hasConversion;

    /** 转化点 (可空) */
    @Size(max = 500, message = "转化点长度不能超过 500")
    private String conversionPoint;

    /** 入口触点 (可空) */
    @Size(max = 50, message = "入口触点长度不能超过 50")
    private String entryTouchpoint;

    /** 出口触点 (可空) */
    @Size(max = 50, message = "出口触点长度不能超过 50")
    private String exitTouchpoint;

    /** 设备类型 (可空) */
    @Size(max = 30, message = "设备类型长度不能超过 30")
    private String deviceType;

    /** 会话 ID (可空) */
    @Size(max = 200, message = "会话 ID 长度不能超过 200")
    private String sessionId;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
