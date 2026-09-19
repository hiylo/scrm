/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSegmentCalculationResultDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * SCRM 客户分群计算结果 DTO。
 * <p>
 * 计算分群成员 (calculateSegment) 或预览分群 (previewSegment) 的返回结果, 汇总匹配客户数、
 * 新增 / 流失成员数与样本成员列表。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmSegmentCalculationResultDto {

    /** 分群 ID (预览场景为 null) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long segmentId;

    /** 匹配客户总数 */
    private int totalMatched;

    /** 新增成员数 (本次计算新加入) */
    private int addedCount;

    /** 流失成员数 (本次计算移除) */
    private int removedCount;

    /** 样本成员列表 (最多返回前若干条, 便于前端预览) */
    private List<Map<String, Object>> sampleMembers;
}
