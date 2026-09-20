/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignComparisonDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * SCRM 营销活动对比 DTO。
 * <p>
 * 用于多活动效果对比接口入参, 传入需要对比的分析/活动 ID 列表。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCampaignComparisonDto {

    /** 需要对比的分析 ID 列表 */
    @NotEmpty(message = "对比分析 ID 列表不能为空")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> campaignIds;
}
