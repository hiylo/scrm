/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConfigImportDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * SCRM 配置导入 DTO。
 * <p>
 * 用于 {@code importConfigs} 接口的入参, 携带多条配置键值对与是否覆盖已存在配置。
 * 服务端将逐条处理: 已存在配置按 overwrite 决定是否覆盖, 不存在则创建。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmConfigImportDto {

    /** 待导入配置项列表 */
    @NotEmpty(message = "导入配置列表不能为空")
    @Valid
    private List<ConfigImportItem> configs;

    /** 是否覆盖已存在配置 (默认 FALSE) */
    private Boolean overwrite;

    /** 导入人 (可空, 缺省由上下文填充) */
    @Size(max = 100, message = "导入人长度不能超过 100")
    private String importedBy;

    /** 变更原因 (可空) */
    @Size(max = 500, message = "变更原因长度不能超过 500")
    private String changeReason;

    /**
     * 单条导入配置项
     * @author Hsi Chu
     */
    @Data
    public static class ConfigImportItem {

        /** 配置键 */
        @Size(max = 200, message = "配置键长度不能超过 200")
        private String key;

        /** 配置值 (可空) */
        private String value;
    }
}
