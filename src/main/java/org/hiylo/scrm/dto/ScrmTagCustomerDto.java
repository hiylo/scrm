/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagCustomerDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 客户标签关联 DTO。
 * <p>
 * 对应 {@code ScrmTagCustomerEntity} 的客户-标签赋值关系字段, 用于打标/去标接口入参。
 * 与 {@link ScrmCustomerTagDto} (标签定义 DTO) 区分: 本 DTO 仅承载赋值关系 (customerId +
 * tagId + tagValue + 来源 + 打标人), 不涉及标签定义本身。
 * </p>
 * <p>
 * tagSource: MANUAL 手动 / AUTO 自动 / IMPORT 导入 / COMPUTED 计算 (默认 MANUAL)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmTagCustomerDto {

    /** 客户 ID (引用 scrm_customer.id) */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 标签 ID (引用 scrm_tag.id) */
    @NotNull(message = "标签 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tagId;

    /** 标签值 (用于有值标签, 可空) */
    @Size(max = 500, message = "标签值长度不能超过 500")
    private String tagValue;

    /** 标签来源: MANUAL/AUTO/IMPORT/COMPUTED (默认 MANUAL) */
    @Pattern(regexp = "MANUAL|AUTO|IMPORT|COMPUTED",
            message = "标签来源仅支持 MANUAL/AUTO/IMPORT/COMPUTED")
    private String tagSource;

    /** 打标人 ID (可空, 默认取当前请求用户) */
    @Size(max = 100, message = "打标人长度不能超过 100")
    private String assignedBy;

    /** 打标人名称 (可空, 冗余字段便于展示) */
    @Size(max = 100, message = "打标人名称长度不能超过 100")
    private String assignedByName;

    /** 过期时间 (可空, 到期后视为失效) */
    private java.time.LocalDateTime expiresAt;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String note;
}
