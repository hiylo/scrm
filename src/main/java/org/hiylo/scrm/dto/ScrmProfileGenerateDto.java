/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProfileGenerateDto.java
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
 * SCRM 客户画像生成请求 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmProfileGenerateDto {

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 模板 ID (可空, 为空时使用默认模板) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    /** 是否强制重新生成 (即使已有 GENERATED 画像也重新生成) */
    private Boolean forceRegenerate;
}
