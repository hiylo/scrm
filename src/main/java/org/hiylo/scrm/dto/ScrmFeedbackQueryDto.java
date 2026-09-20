/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFeedbackQueryDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 反馈查询条件 DTO。
 * <p>
 * 用于反馈分页查询的过滤条件封装。所有字段均可空, 为空时不过滤对应维度。
 * keyword 模糊匹配反馈编号/标题/内容。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmFeedbackQueryDto {

    /* * 反馈类型过滤: SUGGESTION / COMPLAINT / COMPLIMENT / BUG_REPORT / FEATURE_REQUEST / SERVICE_ISSUE / PRODUCT_ISSUE /
    /* OTHER */
    private String feedbackType;

    /** 分类过滤 */
    private String category;

    /** 状态过滤: NEW / IN_REVIEW / IN_PROGRESS / RESOLVED / CLOSED / REJECTED / DUPLICATE */
    private String status;

    /** 优先级过滤: URGENT / HIGH / MEDIUM / LOW */
    private String priority;

    /** 情感过滤: POSITIVE / NEUTRAL / NEGATIVE */
    private String sentiment;

    /** 客户 ID 过滤 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 起始时间 (按创建时间) */
    private LocalDateTime startTime;

    /** 截止时间 (按创建时间) */
    private LocalDateTime endTime;

    /** 关键词过滤, 匹配反馈编号/标题/内容 */
    private String keyword;
}
