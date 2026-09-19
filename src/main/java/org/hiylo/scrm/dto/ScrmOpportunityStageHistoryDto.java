/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOpportunityStageHistoryDto.java
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
 * SCRM 商机阶段变更历史 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmOpportunityStageHistoryDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 商机 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long opportunityId;

    /** 变更前阶段 ID (首次为空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromStageId;

    /** 变更前阶段名称 (联表查询填充, 便于前端展示) */
    private String fromStageName;

    /** 变更后阶段 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toStageId;

    /** 变更后阶段名称 (联表查询填充, 便于前端展示) */
    private String toStageName;

    /** 变更操作人用户 ID */
    private String changedBy;

    /** 变更时间 */
    private LocalDateTime changedAt;

    /** 变更备注 */
    private String note;

    /** 在上一阶段停留天数 */
    private Integer durationDays;
}
