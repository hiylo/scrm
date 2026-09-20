/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLifecycleDto.java
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
 * SCRM 客户当前生命周期阶段 DTO。
 * <p>
 * 对应 {@code ScrmCustomerLifecycleEntity} 的业务字段, 主要用于查询返回。
 * 客户阶段分配 / 转换由 {@code ScrmLifecycleTransitionActionDto} /
 * {@code ScrmLifecycleBulkTransitionDto} 触发。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCustomerLifecycleDto {

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

    /** 当前阶段 ID */
    @NotNull(message = "当前阶段 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentStageId;

    /** 当前阶段编码 */
    @NotBlank(message = "当前阶段编码不能为空")
    @Size(max = 50, message = "当前阶段编码长度不能超过 50")
    private String currentStageCode;

    /** 当前阶段名称 (可空) */
    @Size(max = 100, message = "当前阶段名称长度不能超过 100")
    private String currentStageName;

    /** 进入当前阶段时间 */
    private LocalDateTime enteredCurrentStageAt;

    /** 在当前阶段天数 */
    private Integer durationInStageDays;

    /** 上一阶段 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long previousStageId;

    /** 上一阶段编码 (可空) */
    private String previousStageCode;

    /** 阶段变更次数 */
    private Integer stageHistoryCount;

    /** 是否超期 */
    private Boolean isOverdue;

    /** 超期天数 */
    private Integer overdueDays;

    /** 预期下一阶段 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long nextStageId;

    /** 预期转换时间 (可空) */
    private LocalDateTime expectedTransitionAt;

    /** 负责人 (可空) */
    @Size(max = 100, message = "负责人长度不能超过 100")
    private String assignedTo;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

    /** 最近更新时间 */
    private LocalDateTime lastUpdatedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
