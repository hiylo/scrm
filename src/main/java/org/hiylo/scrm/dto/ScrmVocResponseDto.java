/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocResponseDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM VoC 声音回复 DTO。
 * <p>
 * 用于向指定声音提交回复: voiceId 必填, response 必填, responderId 必填, isPublic 缺省 FALSE。
 * 服务端追加回复后递增声音的 responseCount。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmVocResponseDto {

    /** 声音 ID */
    @NotNull(message = "声音 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long voiceId;

    /** 回复内容 */
    @NotBlank(message = "回复内容不能为空")
    private String response;

    /** 回复人 ID */
    @NotBlank(message = "回复人 ID 不能为空")
    @Size(max = 100, message = "回复人 ID 长度不能超过 100")
    private String responderId;

    /** 是否公开 (默认 FALSE) */
    private Boolean isPublic;
}
