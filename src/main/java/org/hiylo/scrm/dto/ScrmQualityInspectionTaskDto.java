/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQualityInspectionTaskDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 质检任务 DTO。
 * <p>
 * 对应 {@code ScrmQualityInspectionTaskEntity} 的业务字段, 不含公共字段与汇总统计字段。
 * 创建任务接口入参, 由系统执行后回填统计字段。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmQualityInspectionTaskDto {

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 200, message = "任务名称长度不能超过 200")
    private String taskName;

    /** 质检范围: ALL/ASSIGNEE/ACCOUNT/CUSTOMER */
    @NotBlank(message = "质检范围不能为空")
    @Size(max = 30, message = "质检范围长度不能超过 30")
    @Pattern(regexp = "ALL|ASSIGNEE|ACCOUNT|CUSTOMER",
            message = "质检范围仅支持 ALL/ASSIGNEE/ACCOUNT/CUSTOMER")
    private String inspectionScope;

    /** 范围值 (assigneeId/accountId/customerId, ALL 时可空) */
    @Size(max = 500, message = "范围值长度不能超过 500")
    private String scopeValue;

    /** 质检会话起始时间 */
    @NotNull(message = "起始时间不能为空")
    private LocalDateTime startTime;

    /** 质检会话截止时间 */
    @NotNull(message = "截止时间不能为空")
    private LocalDateTime endTime;

    /** 质检规则 ID 列表 JSON 数组 (如 [123, 456]) */
    @NotBlank(message = "质检规则 ID 列表不能为空")
    private String ruleIds;

    /** 创建人（可空） */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
