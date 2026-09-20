/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataDictionaryUsageDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 数据字典使用记录 DTO。
 * <p>
 * 用于记录字典 / 字典项在系统中的引用情况, 写入时必填字典 ID / 编码 / 使用模块,
 * 字典项级使用记录需带 itemId。{@link #increment} 控制是累加次数还是覆盖次数。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmDataDictionaryUsageDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 字典 ID */
    @NotNull(message = "字典 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dictId;

    /** 字典编码 */
    @NotBlank(message = "字典编码不能为空")
    @Size(max = 50, message = "字典编码长度不能超过 50")
    private String dictCode;

    /** 字典项 ID (可空, 字典级使用统计时为空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemId;

    /** 字典项值 (可空) */
    @Size(max = 500, message = "字典项值长度不能超过 500")
    private String itemValue;

    /** 使用模块 (如 customer / order / ticket) */
    @NotBlank(message = "使用模块不能为空")
    @Size(max = 100, message = "使用模块长度不能超过 100")
    private String usageModule;

    /** 使用实体 (可空, 如 ScrmCustomerEntity) */
    @Size(max = 200, message = "使用实体长度不能超过 200")
    private String usageEntity;

    /** 使用字段 (可空, 如 customer_level) */
    @Size(max = 200, message = "使用字段长度不能超过 200")
    private String usageField;

    /** 使用场景 (可空, 如 FORM / REPORT / FILTER) */
    @Size(max = 200, message = "使用场景长度不能超过 200")
    private String usageScenario;

    /** 使用次数 (默认 1, 写入时若 increment=true 则按此值累加) */
    private Integer usageCount;

    /** 是否累加模式 (默认 true, false 时按 usageCount 覆盖) */
    private Boolean increment;

    /** 最近使用时间 (由服务端维护) */
    private LocalDateTime lastUsedAt;

    /** 首次使用时间 (由服务端维护) */
    private LocalDateTime firstUsedAt;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
