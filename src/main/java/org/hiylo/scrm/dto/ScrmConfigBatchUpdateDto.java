/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConfigBatchUpdateDto.java
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
 * SCRM 配置批量更新 DTO。
 * <p>
 * 用于 {@code batchUpdate} 接口的入参, 携带多条配置键值对与统一变更原因。
 * 服务端将逐条校验配置值合法性, 失败条目不影响其它条目。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmConfigBatchUpdateDto {

    /** 待更新配置项列表 */
    @NotEmpty(message = "更新项列表不能为空")
    @Valid
    private List<ConfigUpdateItem> updates;

    /** 变更原因 (可空) */
    @Size(max = 500, message = "变更原因长度不能超过 500")
    private String changeReason;

    /** 变更人 (可空, 缺省由上下文填充) */
    @Size(max = 100, message = "变更人长度不能超过 100")
    private String changedBy;

    /** IP 地址 (可空) */
    @Size(max = 50, message = "IP 地址长度不能超过 50")
    private String ipAddress;

    /**
     * 单条配置更新项
     * @author Hsi Chu
     */
    @Data
    public static class ConfigUpdateItem {

        /** 配置键 */
        @Size(max = 200, message = "配置键长度不能超过 200")
        private String configKey;

        /** 配置值 (可空, 用于清空场景) */
        private String configValue;
    }
}
