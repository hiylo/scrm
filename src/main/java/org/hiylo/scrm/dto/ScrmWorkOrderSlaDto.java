/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderSlaDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 工单 SLA 策略 DTO。
 * <p>
 * 用于 SLA 策略创建、更新与查询返回。创建时必填策略名称、策略编码、响应与解决时间分钟;
 * 其他字段缺省由服务端补全。统计字段 (totalOrders / breachedOrders 等) 由系统自动维护。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmWorkOrderSlaDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 策略名称 (唯一) */
    @NotBlank(message = "策略名称不能为空")
    @Size(max = 100, message = "策略名称长度不能超过 100")
    private String policyName;

    /** 策略编码 (唯一) */
    @NotBlank(message = "策略编码不能为空")
    @Size(max = 50, message = "策略编码长度不能超过 50")
    private String policyCode;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 适用工单类型 (可空) */
    @Size(max = 50, message = "适用工单类型长度不能超过 50")
    private String orderType;

    /** 适用优先级 (可空) */
    @Size(max = 20, message = "适用优先级长度不能超过 20")
    private String priority;

    /** 适用客群 (可空) */
    @Size(max = 100, message = "适用客群长度不能超过 100")
    private String customerSegment;

    /** 响应时间分钟 */
    @NotNull(message = "响应时间分钟不能为空")
    @Min(value = 0, message = "响应时间分钟不能为负数")
    private Integer responseTimeMinutes;

    /** 解决时间分钟 */
    @NotNull(message = "解决时间分钟不能为空")
    @Min(value = 0, message = "解决时间分钟不能为负数")
    private Integer resolutionTimeMinutes;

    /** 响应时间小时 (可空) */
    private Integer responseTimeHours;

    /** 解决时间小时 (可空) */
    private Integer resolutionTimeHours;

    /** 仅工作时间 */
    private Boolean businessHoursOnly;

    /** 工作时间开始 (默认 09:00) */
    @Size(max = 10, message = "工作时间开始长度不能超过 10")
    private String businessHoursStart;

    /** 工作时间结束 (默认 18:00) */
    @Size(max = 10, message = "工作时间结束长度不能超过 10")
    private String businessHoursEnd;

    /** 工作日 (默认 MON-FRI) */
    @Size(max = 50, message = "工作日长度不能超过 50")
    private String businessDays;

    /** 时区 (默认 Asia/Shanghai) */
    @Size(max = 50, message = "时区长度不能超过 50")
    private String timezone;

    /** 启用升级 */
    private Boolean escalationEnabled;

    /** 升级级别 JSON (可空) */
    @Size(max = 2000, message = "升级级别长度不能超过 2000")
    private String escalationLevels;

    /** 首次响应违规动作 (可空) */
    @Size(max = 200, message = "首次响应违规动作长度不能超过 200")
    private String firstResponseBreachAction;

    /** 解决违规动作 (可空) */
    @Size(max = 200, message = "解决违规动作长度不能超过 200")
    private String resolutionBreachAction;

    /** 违规前提醒分钟 (默认 30) */
    private Integer warningBeforeBreach;

    /** 解决后自动关闭小时 (默认 72) */
    private Integer autoCloseAfterResolution;

    /** 允许重开 */
    private Boolean reopenAllowed;

    /** 允许重开小时 (默认 168) */
    private Integer reopenWithinHours;

    /** 每次违规罚金 (默认 0) */
    private Double penaltyPerBreach;

    /** 每次达标奖励 (默认 0) */
    private Double creditPerMet;

    /** 目标达标率 % (默认 95) */
    private Double targetComplianceRate;

    /** 当前达标率 % (默认 0) */
    private Double currentComplianceRate;

    /** 总订单数 */
    private Integer totalOrders;

    /** 违规订单数 */
    private Integer breachedOrders;

    /** 达标订单数 */
    private Integer metOrders;

    /** 平均响应时间分钟 */
    private Double avgResponseTime;

    /** 平均解决时间分钟 */
    private Double avgResolutionTime;

    /** 是否启用 */
    private Boolean enabled;

    /** 是否默认策略 */
    private Boolean isDefault;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
