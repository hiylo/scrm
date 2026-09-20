/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContentReviewDto.java
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
 * SCRM 内容审核 DTO。
 * <p>
 * 审核接口入参, 携带待审核内容 ID 与审核结论 (approved=true 通过, false 驳回)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmContentReviewDto {

    /** 内容 ID */
    @NotNull(message = "内容 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contentId;

    /** 是否通过 (true 通过 / false 驳回) */
    @NotNull(message = "审核结论不能为空")
    private Boolean approved;

    /** 审核意见 (可空) */
    @Size(max = 500, message = "审核意见长度不能超过 500")
    private String comment;
}
