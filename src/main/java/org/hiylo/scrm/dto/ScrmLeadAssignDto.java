/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadAssignDto.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 销售线索分配 DTO。
 * <p>
 * 用于将线索评分记录分配给销售负责人, scoreId 标识被分配的评分记录, assigneeId 为负责人用户标识。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmLeadAssignDto {

    /** 评分记录 ID */
    @NotNull(message = "评分 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long scoreId;

    /** 负责人用户标识 */
    @NotBlank(message = "负责人不能为空")
    @Size(max = 100, message = "负责人长度不能超过 100")
    private String assigneeId;
}
