/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWhatIfDto.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 销售预测 What-If 分析入参 DTO。
 * <p>
 * 用于 {@code /scenarios/what-if} 接口, 基于指定模型与基线场景, 应用一组因子变更
 * (如提高增长率 / 调整市场条件) 重新生成预测, 并与基线对比输出差异。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmWhatIfDto {

    /** 模型 ID */
    @NotNull(message = "模型 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    /** 基线场景 ID */
    @NotNull(message = "基线场景 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long baseScenarioId;

    /** 因子变更列表 (至少一项) */
    @NotEmpty(message = "因子变更列表不能为空")
    private List<Change> changes;

    /**
     * What-If 变更: factor 名称与 value 数值 (如 {factor:"growthRate", value:0.2})。
     * @author Hsi Chu
     */
    @Data
    public static class Change {
        /** 变更因子名称 (如 growthRate / seasonalityFactor / marketCondition) */
        private String factor;
        /** 变更后的数值 */
        private Double value;
    }
}
