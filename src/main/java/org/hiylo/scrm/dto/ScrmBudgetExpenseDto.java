/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetExpenseDto.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 营销预算支出 DTO。
 * <p>
 * 对应 {@code ScrmBudgetExpenseEntity} 的业务字段, 创建/更新接口入参。
 * attachments 为 JSON 字符串, tags 为逗号分隔字符串。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmBudgetExpenseDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 支出编号 (创建时由服务生成, 查询返回) */
    private String expenseNo;

    /** 预算方案 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long planId;

    /** 预算方案名称 (查询返回, 可空) */
    private String planName;

    /** 预算分配 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long allocationId;

    /** 预算分配名称 (查询返回, 可空) */
    private String allocationName;

    /** 支出类型: AD_SPEND/CONTENT_PRODUCTION/EVENT/TOOL/SALARY/REIMBURSEMENT/PAYMENT/OTHER */
    @NotBlank(message = "支出类型不能为空")
    @Pattern(regexp = "AD_SPEND|CONTENT_PRODUCTION|EVENT|TOOL|SALARY|REIMBURSEMENT|PAYMENT|OTHER",
            message = "支出类型仅支持 AD_SPEND/CONTENT_PRODUCTION/EVENT/TOOL/SALARY/REIMBURSEMENT/PAYMENT/OTHER")
    private String expenseType;

    /** 关联营销活动 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long campaignId;

    /** 关联营销活动名称 (可空) */
    @Size(max = 200, message = "关联营销活动名称长度不能超过 200")
    private String campaignName;

    /** 支出日期 */
    @NotNull(message = "支出日期不能为空")
    private LocalDate expenseDate;

    /** 支出金额 */
    @NotNull(message = "支出金额不能为空")
    private Double amount;

    /** 币种 (默认 CNY) */
    @Pattern(regexp = "CNY|USD|EUR|GBP|JPY",
            message = "币种仅支持 CNY/USD/EUR/GBP/JPY")
    private String currency;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 供应商 (可空) */
    @Size(max = 200, message = "供应商长度不能超过 200")
    private String vendor;

    /** 发票号 (可空) */
    @Size(max = 100, message = "发票号长度不能超过 100")
    private String invoiceNo;

    /** 付款方式: BANK_TRANSFER/ALIPAY/WECHAT/CASH/CARD/OTHER (可空) */
    @Pattern(regexp = "BANK_TRANSFER|ALIPAY|WECHAT|CASH|CARD|OTHER",
            message = "付款方式仅支持 BANK_TRANSFER/ALIPAY/WECHAT/CASH/CARD/OTHER")
    private String paymentMethod;

    /** 付款状态: PENDING/PAID/CANCELLED (查询返回) */
    private String paymentStatus;

    /** 付款时间 (查询返回) */
    private LocalDateTime paidAt;

    /** 凭证 URL (可空) */
    @Size(max = 500, message = "凭证 URL 长度不能超过 500")
    private String receiptUrl;

    /** 附件 JSON (可空) */
    @Size(max = 1000, message = "附件长度不能超过 1000")
    private String attachments;

    /** 状态: PENDING/APPROVED/REJECTED/PAID (查询返回) */
    private String status;

    /** 审批人 (查询返回) */
    private String approvedBy;

    /** 审批时间 (查询返回) */
    private LocalDateTime approvedAt;

    /** 审批备注 (查询返回) */
    private String approverComment;

    /** 部门 ID (可空) */
    @Size(max = 100, message = "部门 ID 长度不能超过 100")
    private String departmentId;

    /** 部门名称 (可空) */
    @Size(max = 200, message = "部门名称长度不能超过 200")
    private String departmentName;

    /** 申请人 ID (可空) */
    @Size(max = 100, message = "申请人 ID 长度不能超过 100")
    private String requesterId;

    /** 申请人名称 (可空) */
    @Size(max = 100, message = "申请人名称长度不能超过 100")
    private String requesterName;

    /** 标签 (可空, 逗号分隔) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
