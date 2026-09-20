/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProfileCompareDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * SCRM 客户画像对比请求 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmProfileCompareDto {

    /** 客户 ID 1 */
    @NotNull(message = "客户 ID 1 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId1;

    /** 客户 ID 2 */
    @NotNull(message = "客户 ID 2 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId2;

    /** 参与对比的维度 (可空, 为空时对比全部维度) */
    private List<String> dimensions;
}
