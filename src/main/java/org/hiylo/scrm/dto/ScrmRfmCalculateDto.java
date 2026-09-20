/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRfmCalculateDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * SCRM RFM 批量计算请求 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmRfmCalculateDto {

    /** RFM 配置 ID */
    @NotNull(message = "配置 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long configId;

    /** 待计算的客户 ID 列表 */
    @NotEmpty(message = "客户 ID 列表不能为空")
    private List<Long> customerIds;
}
