/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionRecordDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 销售佣金记录 DTO。
 * <p>
 * 对应 {@code ScrmCommissionRecordEntity} 的业务字段, 用于查询返回与状态更新。
 * calculationDetails 为 JSON 数组字符串: {@code [{rule,condition,rate,amount}]}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCommissionRecordDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 佣金编号 (唯一, 格式 COMM+年月+序号) */
    private String recordNo;

    /** 方案 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long planId;

    /** 方案名称 (冗余便于展示) */
    private String planName;

    /** 规则 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;

    /** 规则名称 (可空) */
    private String ruleName;

    /** 销售人员 ID */
    private String salesPersonId;

    /** 销售人员名称 */
    private String salesPersonName;

    /** 团队 ID (可空) */
    private String teamId;

    /** 团队名称 (可空) */
    private String teamName;

    /** 关联订单 ID (可空) */
    private String orderId;

    /** 订单金额 */
    private Double orderAmount;

    /** 订单利润 */
    private Double orderProfit;

    /** 订单日期 (可空) */
    private LocalDate orderDate;

    /** 产品分类 (可空) */
    private String productCategory;

    /** 客户类型 (可空) */
    private String customerType;

    /** 佣金计算基础 */
    private Double commissionBasis;

    /** 佣金比例 */
    private Double commissionRate;

    /** 佣金金额 */
    private Double commissionAmount;

    /** 奖金 */
    private Double bonusAmount;

    /** 扣减 */
    private Double deductionAmount;

    /** 最终佣金 */
    private Double finalCommission;

    /** 计算详情 JSON (可空): [{rule,condition,rate,amount}] */
    private String calculationDetails;

    /** 状态: CALCULATED/PENDING_APPROVAL/APPROVED/REJECTED/PAID/CLAWBACK/ADJUSTED */
    @Pattern(regexp = "CALCULATED|PENDING_APPROVAL|APPROVED|REJECTED|PAID|CLAWBACK|ADJUSTED",
            message = "状态仅支持 CALCULATED/PENDING_APPROVAL/APPROVED/REJECTED/PAID/CLAWBACK/ADJUSTED")
    private String status;

    /** 所属周期 (可空, 格式 yyyy-MM) */
    private String period;

    /** 发放日期 (可空) */
    private LocalDate payoutDate;

    /** 审批人 (可空) */
    private String approvedBy;

    /** 审批时间 (可空) */
    private LocalDateTime approvedAt;

    /** 审批备注 (可空) */
    @Size(max = 500, message = "审批备注长度不能超过 500")
    private String approvalNote;

    /** 发放时间 (可空) */
    private LocalDateTime paidAt;

    /** 实发金额 */
    private Double paidAmount;

    /** 税额 */
    private Double taxAmount;

    /** 扣减说明 (可空) */
    @Size(max = 500, message = "扣减说明长度不能超过 500")
    private String deductionNote;

    /** 追回金额 */
    private Double clawbackAmount;

    /** 追回原因 (可空) */
    @Size(max = 500, message = "追回原因长度不能超过 500")
    private String clawbackReason;

    /** 追回时间 (可空) */
    private LocalDateTime clawbackAt;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

    /** 计算时间 */
    private LocalDateTime calculatedAt;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
