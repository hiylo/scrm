/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOpportunityStageChangeDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 商机阶段推进请求 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmOpportunityStageChangeDto {

    /** 商机 ID */
    @NotNull(message = "商机 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long opportunityId;

    /** 目标阶段 ID */
    @NotNull(message = "目标阶段 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toStageId;

    /** 变更备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String note;
}
