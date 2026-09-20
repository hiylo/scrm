/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTicketSatisfactionDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 工单满意度评价请求 DTO。
 * <p>
 * 客户对已解决/已关闭的工单提交满意度评价, 评分 1-5 分。仅 RESOLVED / CLOSED / REOPENED
 * 状态的工单可提交评价, 重复提交将覆盖原评价。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmTicketSatisfactionDto {

    /** 工单 ID */
    @NotNull(message = "工单 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ticketId;

    /** 满意度评分 (1-5) */
    @NotNull(message = "满意度评分不能为空")
    @Min(value = 1, message = "满意度评分最小为 1")
    @Max(value = 5, message = "满意度评分最大为 5")
    private Integer score;

    /** 满意度评价内容 (可空) */
    @Size(max = 500, message = "满意度评价长度不能超过 500")
    private String comment;
}
