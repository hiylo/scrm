/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmJourneyEnrollDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 客户入旅程请求 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmJourneyEnrollDto {

    /** 旅程 ID */
    @NotNull(message = "旅程 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long journeyId;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 入营来源: EVENT / MANUAL / API */
    @Size(max = 50, message = "入营来源长度不能超过 50")
    @Pattern(regexp = "EVENT|MANUAL|API", message = "入营来源仅支持 EVENT/MANUAL/API")
    @NotBlank(message = "入营来源不能为空")
    private String source;
}
