/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChurnScanDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

/**
 * SCRM 客户流失批量扫描请求 DTO。
 * <p>
 * {@link #daysBack} 限定扫描客户范围 (扫描最近 N 天内有互动的客户), {@link #customerIds}
 * 可显式指定待扫描客户列表, 优先于 daysBack 生效。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmChurnScanDto {

    /** 扫描最近多少天内有互动的客户 (默认 30) */
    @Min(value = 1, message = "扫描天数必须大于 0")
    private Integer daysBack;

    /** 待扫描客户 ID 列表 (可空, 非空时优先于 daysBack) */
    private List<Long> customerIds;
}
