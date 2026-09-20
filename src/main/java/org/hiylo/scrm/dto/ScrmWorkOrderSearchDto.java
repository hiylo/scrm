/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderSearchDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 工单搜索 DTO。
 * <p>
 * 用于工单高级搜索, 支持按编号、标题、类型、优先级、状态、客户、处理人、来源、SLA 违规、
 * 时间范围与关键词组合过滤, 并支持自定义排序字段。所有字段均可空, 为空时不参与过滤。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmWorkOrderSearchDto {

    /** 工单编号 (可空, 模糊匹配) */
    private String orderNo;

    /** 标题 (可空, 模糊匹配) */
    private String title;

    /** 工单类型 (可空, 精确匹配) */
    private String orderType;

    /** 优先级 (可空, 精确匹配) */
    private String priority;

    /** 工单状态 (可空, 精确匹配) */
    private String orderStatus;

    /** 客户 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 处理人 (可空, 模糊匹配) */
    private String assignedTo;

    /** 来源 (可空, 精确匹配) */
    private String source;

    /** SLA 违规过滤 (可空: true 仅违规 / false 仅达标) */
    private Boolean slaBreached;

    /** 起始时间 (按创建时间, 可空) */
    private LocalDateTime startDate;

    /** 截止时间 (按创建时间, 可空) */
    private LocalDateTime endDate;

    /** 关键词 (可空, 模糊匹配工单编号/标题/描述) */
    private String keyword;

    /** 排序字段 (可空, 默认 createTime 倒序): createTime / updateTime / priority / orderStatus */
    private String sortBy;
}
