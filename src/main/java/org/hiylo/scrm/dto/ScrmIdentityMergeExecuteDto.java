/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIdentityMergeExecuteDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * SCRM 合并任务执行参数 DTO。
 * <p>
 * 用于触发合并执行, 可携带字段覆盖 (fieldOverrides) 以人工干预字段合并结果。
 * fieldOverrides 形如 {"nickname":"张三", "remark":"已合并客户"}。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmIdentityMergeExecuteDto {

    /** 任务 ID */
    @NotNull(message = "任务 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 字段覆盖 (可空): field → 覆盖值, 优先级高于合并策略 */
    private Map<String, Object> fieldOverrides;
}
