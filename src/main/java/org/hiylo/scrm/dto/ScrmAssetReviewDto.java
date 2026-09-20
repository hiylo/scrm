/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetReviewDto.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 营销素材审核 DTO。
 * <p>
 * 用于素材审核接口入参, {@code action} 为 APPROVE / REJECT, {@code comment} 为审核意见
 * (拒绝时建议必填)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAssetReviewDto {

    /** 素材 ID */
    @NotNull(message = "素材 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long assetId;

    /** 审核动作: APPROVE / REJECT */
    @NotBlank(message = "审核动作不能为空")
    @Pattern(regexp = "APPROVE|REJECT", message = "审核动作仅支持 APPROVE / REJECT")
    private String action;

    /** 审核意见 (可空, 拒绝时建议必填) */
    @Size(max = 500, message = "审核意见长度不能超过 500")
    private String comment;
}
