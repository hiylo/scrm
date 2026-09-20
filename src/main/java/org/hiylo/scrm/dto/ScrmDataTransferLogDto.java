/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataTransferLogDto.java
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
 * SCRM 数据导入导出日志 DTO。
 * <p>
 * 对应 {@code ScrmDataTransferLogEntity} 的业务字段, 不含公共字段。
 * 一般由系统在导入/导出过程中自动写入, 此 DTO 用于内部传递与查询返回。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmDataTransferLogDto {

    /** 关联任务 ID */
    @NotNull(message = "任务 ID 不能为空")
    private Long taskId;

    /** 任务类型: IMPORT/EXPORT */
    @NotBlank(message = "任务类型不能为空")
    @Size(max = 10, message = "任务类型长度不能超过 10")
    @Pattern(regexp = "IMPORT|EXPORT", message = "任务类型仅支持 IMPORT/EXPORT")
    private String taskType;

    /** 行号（可空） */
    private Integer rowIndex;

    /** 记录键（可空, 去重键或主业务键） */
    @Size(max = 200, message = "记录键长度不能超过 200")
    private String recordKey;

    /** 操作: CREATE/UPDATE/SKIP/FAIL */
    @NotBlank(message = "操作不能为空")
    @Size(max = 20, message = "操作长度不能超过 20")
    @Pattern(regexp = "CREATE|UPDATE|SKIP|FAIL", message = "操作仅支持 CREATE/UPDATE/SKIP/FAIL")
    private String operation;

    /** 字段错误 JSON（可空） */
    private String fieldErrors;

    /** 消息（可空） */
    @Size(max = 500, message = "消息长度不能超过 500")
    private String message;

    /** 状态: SUCCESS/WARNING/ERROR */
    @NotBlank(message = "状态不能为空")
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "SUCCESS|WARNING|ERROR", message = "状态仅支持 SUCCESS/WARNING/ERROR")
    private String status;

    /** 处理时间 */
    @NotNull(message = "处理时间不能为空")
    private LocalDateTime processedAt;
}
