/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChurnRecoveryDto.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 客户流失挽留记录 DTO。
 * <p>
 * 对应 {@code ScrmChurnRecoveryEntity} 的业务字段, 创建挽留记录接口入参。
 * recoveryAction 标注挽留动作类型, result 记录动作执行结果。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmChurnRecoveryDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 关联预警 ID */
    @NotNull(message = "预警 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long warningId;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 挽留动作: FOLLOW_UP / MASS_SEND / COUPON / CALL / VISIT / OTHER */
    @NotBlank(message = "挽留动作不能为空")
    @Pattern(regexp = "FOLLOW_UP|MASS_SEND|COUPON|CALL|VISIT|OTHER",
            message = "挽留动作仅支持 FOLLOW_UP/MASS_SEND/COUPON/CALL/VISIT/OTHER")
    private String recoveryAction;

    /** 动作详情 (可空) */
    @Size(max = 500, message = "动作详情长度不能超过 500")
    private String actionDetail;

    /** 动作执行时间 (可空, 未填则取当前时间) */
    private LocalDateTime actionExecutedAt;

    /** 执行人 */
    @NotBlank(message = "执行人不能为空")
    @Size(max = 100, message = "执行人长度不能超过 100")
    private String executedBy;

    /** 动作结果: SUCCESS / FAILED / PENDING */
    @NotBlank(message = "动作结果不能为空")
    @Pattern(regexp = "SUCCESS|FAILED|PENDING", message = "动作结果仅支持 SUCCESS/FAILED/PENDING")
    private String result;

    /** 客户是否回应 (默认 false) */
    private Boolean customerResponded;

    /** 客户回应时间 (可空) */
    private LocalDateTime responseAt;

    /** 是否成功激活 (默认 false) */
    private Boolean reactivated;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
