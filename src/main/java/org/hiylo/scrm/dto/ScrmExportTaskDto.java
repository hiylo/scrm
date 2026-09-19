/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExportTaskDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 数据导出任务 DTO。
 * <p>
 * 对应 {@code ScrmExportTaskEntity} 的业务字段, 不含公共字段、统计字段与执行轨迹字段
 * (status/filePath/records/start/end/duration 等由系统执行后回填)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmExportTaskDto {

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 200, message = "任务名称长度不能超过 200")
    private String taskName;

    /** 数据类型: CUSTOMER/CONTACT/FOLLOW_RECORD/TAG/PRODUCT/ORDER/OTHER */
    @NotBlank(message = "数据类型不能为空")
    @Size(max = 30, message = "数据类型长度不能超过 30")
    @Pattern(regexp = "CUSTOMER|CONTACT|FOLLOW_RECORD|TAG|PRODUCT|ORDER|OTHER",
            message = "数据类型仅支持 CUSTOMER/CONTACT/FOLLOW_RECORD/TAG/PRODUCT/ORDER/OTHER")
    private String dataType;

    /** 查询条件 JSON（可空） */
    private String queryCondition;

    /** 逗号分隔选中字段（可空） */
    @Size(max = 1000, message = "选中字段长度不能超过 1000")
    private String selectedFields;

    /** 过滤条件 JSON（可空） */
    private String filters;

    /** 文件类型: CSV/EXCEL/JSON（创建时可选, 默认 EXCEL） */
    @Size(max = 20, message = "文件类型长度不能超过 20")
    @Pattern(regexp = "CSV|EXCEL|JSON", message = "文件类型仅支持 CSV/EXCEL/JSON")
    private String fileType;

    /** 触发人 */
    @NotBlank(message = "触发人不能为空")
    @Size(max = 100, message = "触发人长度不能超过 100")
    private String triggeredBy;
}
