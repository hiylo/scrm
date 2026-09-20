/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLifecycleHistoryDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户生命周期阶段转换历史 DTO。
 * <p>
 * 对应 {@code ScrmLifecycleHistoryEntity} 的业务字段, 主要用于查询返回。
 * transitionType 以枚举字符串校验合法性。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmLifecycleHistoryDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 源阶段 ID (可空, null 表示新客户进入) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromStageId;

    /** 源阶段编码 (可空) */
    private String fromStageCode;

    /** 源阶段名称 (可空) */
    private String fromStageName;

    /** 目标阶段 ID */
    @NotNull(message = "目标阶段 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toStageId;

    /** 目标阶段编码 */
    @NotBlank(message = "目标阶段编码不能为空")
    @Size(max = 50, message = "目标阶段编码长度不能超过 50")
    private String toStageCode;

    /** 目标阶段名称 (可空) */
    private String toStageName;

    /** 关联转换规则 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long transitionId;

    /** 转换类型: AUTO/MANUAL/SYSTEM */
    @NotBlank(message = "转换类型不能为空")
    @Pattern(regexp = "AUTO|MANUAL|SYSTEM", message = "转换类型仅支持 AUTO/MANUAL/SYSTEM")
    private String transitionType;

    /** 触发事件 (可空): PURCHASE/LOGIN/INACTIVE_DAYS/FIRST_CONTACT/REFUND/CUSTOM */
    @Size(max = 100, message = "触发事件长度不能超过 100")
    private String triggerEvent;

    /** 触发描述 (可空) */
    @Size(max = 500, message = "触发描述长度不能超过 500")
    private String triggerDescription;

    /** 上一阶段停留天数 (可空) */
    private Integer durationInPreviousStage;

    /** 操作人 ID (可空) */
    @Size(max = 100, message = "操作人 ID 长度不能超过 100")
    private String operatorId;

    /** 操作人名称 (可空) */
    @Size(max = 100, message = "操作人名称长度不能超过 100")
    private String operatorName;

    /** 转换时间 */
    private LocalDateTime transitionTime;

    /** JSON 附加数据 (可空) */
    private String metadata;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
