/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetUsageDto.java
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
 * SCRM 营销素材使用记录 DTO。
 * <p>
 * 用于记录素材使用接口入参, 必填素材 ID / 使用类型 / 用户 ID, 其余字段可选。
 * 使用时间缺省由服务端填充为当前时间。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAssetUsageDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 素材 ID */
    @NotNull(message = "素材 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long assetId;

    /** 使用类型: VIEW/DOWNLOAD/USE/SHARE/FAVORITE/LIKE/EMBED/EXPORT */
    @NotBlank(message = "使用类型不能为空")
    @Size(max = 20, message = "使用类型长度不能超过 20")
    private String usageType;

    /** 使用模块 (可空) */
    @Size(max = 100, message = "使用模块长度不能超过 100")
    private String usageModule;

    /** 使用实体 (可空) */
    @Size(max = 200, message = "使用实体长度不能超过 200")
    private String usageEntity;

    /** 使用实体名称 (可空) */
    @Size(max = 200, message = "使用实体名称长度不能超过 200")
    private String usageEntityName;

    /** 使用场景 (可空) */
    @Size(max = 200, message = "使用场景长度不能超过 200")
    private String usageScenario;

    /** 用户 ID */
    @NotBlank(message = "用户 ID 不能为空")
    @Size(max = 100, message = "用户 ID 长度不能超过 100")
    private String userId;

    /** 用户名称 (可空) */
    @Size(max = 100, message = "用户名称长度不能超过 100")
    private String userName;

    /** 用户角色 (可空) */
    @Size(max = 50, message = "用户角色长度不能超过 50")
    private String userRole;

    /** 使用次数 (默认 1) */
    private Integer usageCount;

    /** JSON 附加数据 (TEXT, 可空) */
    private String metadata;

    /** 使用时间 (由服务端维护) */
    private LocalDateTime usedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
