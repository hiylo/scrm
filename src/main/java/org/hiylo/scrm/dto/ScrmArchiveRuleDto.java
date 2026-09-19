/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmArchiveRuleDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 归档规则 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmArchiveRuleDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 100, message = "规则名称长度不能超过 100")
    private String ruleName;

    /** 平台类型过滤 (null 表示全部) */
    @Size(max = 30, message = "平台类型长度不能超过 30")
    private String platformType;

    /** 归属账号 ID 过滤 (null 表示全部) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 消息方向过滤: INBOUND / OUTBOUND (null 表示全部) */
    @Size(max = 10, message = "消息方向长度不能超过 10")
    private String direction;

    /** 消息类型过滤 (逗号分隔): TEXT,IMAGE,VIDEO */
    @Size(max = 200, message = "消息类型长度不能超过 200")
    private String messageTypes;

    /** 关键词过滤 (逗号分隔) */
    private String keywords;

    /** 风险等级过滤: LOW / MEDIUM / HIGH (null 表示全部) */
    @Size(max = 20, message = "风险等级过滤长度不能超过 20")
    private String riskLevelFilter;

    /** 是否启用 */
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    /** 优先级 (数字越大越先匹配) */
    private Integer priority;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
