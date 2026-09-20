/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTemplateReviewDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 模板审核 DTO。
 * <p>
 * 审核接口入参, 指定模板 ID、审核动作 (APPROVE 通过 / REJECT 驳回) 与审核意见。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmTemplateReviewDto {

    /** 模板 ID */
    @NotNull(message = "模板 ID 不能为空")
    private Long templateId;

    /** 审核动作: APPROVE 通过 / REJECT 驳回 */
    @NotBlank(message = "审核动作不能为空")
    @Size(max = 20, message = "审核动作长度不能超过 20")
    private String action;

    /** 审核意见（可空） */
    @Size(max = 500, message = "审核意见长度不能超过 500")
    private String comment;

    /** 审核人（可空） */
    @Size(max = 100, message = "审核人长度不能超过 100")
    private String reviewer;
}
