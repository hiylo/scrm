/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSegmentCompareDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SCRM 客户分群对比 DTO。
 * <p>
 * 对比两个分群的成员交集 / 差集与各维度指标, 入参为两个分群 ID。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmSegmentCompareDto {

    /** 分群 ID 1 */
    @NotNull(message = "分群 ID 1 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long segmentId1;

    /** 分群 ID 2 */
    @NotNull(message = "分群 ID 2 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long segmentId2;
}
