/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeReviewDto.java
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
 * SCRM 知识文章审核 DTO。
 * <p>
 * 审核接口入参, {@code action} 为 APPROVE (通过) / REJECT (驳回),
 * {@code comment} 为审核意见 (驳回时必填)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmKnowledgeReviewDto {

    /** 文章 ID */
    @NotNull(message = "文章 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long articleId;

    /** 审核动作: APPROVE / REJECT */
    @NotBlank(message = "审核动作不能为空")
    @Pattern(regexp = "APPROVE|REJECT", message = "审核动作仅支持 APPROVE/REJECT")
    private String action;

    /** 审核意见 (驳回时必填) */
    @Size(max = 500, message = "审核意见长度不能超过 500")
    private String comment;
}
