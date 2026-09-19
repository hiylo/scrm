/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionPayoutDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * SCRM 销售佣金发放 DTO。
 * <p>
 * 发放接口入参: 指定所属周期与销售人员 ID 列表 (可空, 为空表示该周期所有已审批记录)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCommissionPayoutDto {

    /** 所属周期 (格式 yyyy-MM) */
    @NotBlank(message = "所属周期不能为空")
    private String period;

    /** 销售人员 ID 列表 (可空, 为空表示该周期所有已审批记录) */
    private List<String> salesPersonIds;
}
