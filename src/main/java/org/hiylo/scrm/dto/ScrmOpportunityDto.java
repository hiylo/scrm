/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOpportunityDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 商机 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmOpportunityDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 商机名称 */
    @NotBlank(message = "商机名称不能为空")
    @Size(max = 200, message = "商机名称长度不能超过 200")
    private String opportunityName;

    /** 关联客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 所属漏斗 ID */
    @NotNull(message = "漏斗 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long funnelId;

    /** 当前阶段 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentStageId;

    /** 商机金额 */
    private Double amount;

    /** 预计成交日期 */
    private LocalDateTime expectedCloseDate;

    /** 成交概率 (0-100) */
    @Min(value = 0, message = "成交概率不能小于 0")
    @Max(value = 100, message = "成交概率不能大于 100")
    private Integer probability;

    /** 负责人用户 ID */
    @NotBlank(message = "负责人不能为空")
    @Size(max = 100, message = "负责人长度不能超过 100")
    private String ownerUserId;

    /** 商机状态：OPEN / WON / LOST / STALLED */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "OPEN|WON|LOST|STALLED", message = "状态仅支持 OPEN/WON/LOST/STALLED")
    private String status;

    /** 商机来源 */
    @Size(max = 100, message = "商机来源长度不能超过 100")
    private String source;

    /** 竞争对手 */
    @Size(max = 200, message = "竞争对手长度不能超过 200")
    private String competitor;

    /** 备注 */
    private String note;

    /** 成交时间 */
    private LocalDateTime wonAt;

    /** 输单时间 */
    private LocalDateTime lostAt;

    /** 输单原因 */
    @Size(max = 500, message = "输单原因长度不能超过 500")
    private String lostReason;

    /** 加权金额 (amount * probability / 100, 仅查询返回, 用于销售预测) */
    private Double weightedAmount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
