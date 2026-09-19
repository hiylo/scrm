/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmImportTriggerDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 数据导入触发 DTO。
 * <p>
 * 一键触发导入的入参, 携带模板 ID、文件名称与导入选项。服务层据此创建导入任务并
 * 立即执行 (验证 → 导入 → 记录日志), 模拟实现返回任务实体供前端轮询进度。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmImportTriggerDto {

    /** 导入模板 ID */
    @NotNull(message = "模板 ID 不能为空")
    private Long templateId;

    /** 文件名称 (含路径, 服务层从模板/选项推导文件类型) */
    @NotBlank(message = "文件名称不能为空")
    @Size(max = 200, message = "文件名称长度不能超过 200")
    private String fileName;

    /** 导入选项 JSON（可空, 覆盖模板默认选项） */
    private String options;
}
