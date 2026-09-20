/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBatchTagDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * SCRM 批量打标/去标 DTO。
 * <p>
 * 批量打标接口入参, 携带客户 ID 列表、标签 ID 列表与动作 (ADD 添加 / REMOVE 移除)。
 * Service 层对每个客户 × 每个标签执行对应动作, 返回成功操作数。打标人信息可选, 缺省
 * 由请求头透传。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmBatchTagDto {

    /** 客户 ID 列表 */
    @NotEmpty(message = "客户 ID 列表不能为空")
    private List<@NotNull(message = "客户 ID 不能为空") Long> customerIds;

    /** 标签 ID 列表 */
    @NotEmpty(message = "标签 ID 列表不能为空")
    private List<@NotNull(message = "标签 ID 不能为空") Long> tagIds;

    /** 动作: ADD 添加 / REMOVE 移除 */
    @NotBlank(message = "动作不能为空")
    @Pattern(regexp = "ADD|REMOVE", message = "动作仅支持 ADD/REMOVE")
    private String action;

    /** 标签值 (用于有值标签, 仅 ADD 时生效, 可空) */
    @Size(max = 500, message = "标签值长度不能超过 500")
    private String tagValue;

    /** 标签来源 (仅 ADD 时生效, 默认 MANUAL) */
    @Pattern(regexp = "MANUAL|AUTO|IMPORT|COMPUTED|",
            message = "标签来源仅支持 MANUAL/AUTO/IMPORT/COMPUTED")
    private String tagSource;

    /** 打标人 ID (可空) */
    @Size(max = 100, message = "打标人 ID 长度不能超过 100")
    private String assignedBy;

    /** 打标人名称 (可空, 冗余字段便于展示) */
    @Size(max = 100, message = "打标人名称长度不能超过 100")
    private String assignedByName;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String note;
}
