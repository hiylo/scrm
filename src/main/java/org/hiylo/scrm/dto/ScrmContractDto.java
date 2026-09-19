/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 合同实例 DTO。
 * <p>
 * 用于合同实例的创建/更新入参与查询返回。创建时若指定 templateId 则基于模板渲染合同内容,
 * 否则需直接提供 content 字段。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmContractDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 合同编号 (唯一, 创建时为空则自动生成) */
    @Size(max = 100, message = "合同编号长度不能超过 100")
    private String contractNo;

    /** 合同名称 */
    @NotBlank(message = "合同名称不能为空")
    @Size(max = 500, message = "合同名称长度不能超过 500")
    private String contractName;

    /** 合同类型: SALES / SERVICE / PARTNERSHIP / NDA / RESELLER / AGENCY / MAINTENANCE / RENTAL / PURCHASE / CUSTOM */
    @NotBlank(message = "合同类型不能为空")
    @Size(max = 30, message = "合同类型长度不能超过 30")
    @Pattern(regexp = "SALES|SERVICE|PARTNERSHIP|NDA|RESELLER|AGENCY|MAINTENANCE|RENTAL|PURCHASE|CUSTOM",
            message = "合同类型仅支持 SALES/SERVICE/PARTNERSHIP/NDA/RESELLER/AGENCY/MAINTENANCE/RENTAL/PURCHASE/CUSTOM")
    private String contractType;

    /** 关联模板 ID (可空, 手动创建时为 null) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 客户联系方式 (可空) */
    @Size(max = 200, message = "客户联系方式长度不能超过 200")
    private String customerContact;

    /** 客户地址 (可空) */
    @Size(max = 500, message = "客户地址长度不能超过 500")
    private String customerAddress;

    /** 合同标题 (可空) */
    @Size(max = 500, message = "合同标题长度不能超过 500")
    private String title;

    /** 合同描述 (可空) */
    @Size(max = 1000, message = "合同描述长度不能超过 1000")
    private String description;

    /** 合同内容 (渲染后的完整内容, 手动创建时必填) */
    private String content;

    /** JSON 变量值 (创建合同时传入的变量键值对, 可空) */
    private String variables;

    /** 合同金额 */
    @PositiveOrZero(message = "合同金额不能为负数")
    private Double contractAmount;

    /** 币种: CNY / USD / EUR 等, 默认 CNY */
    @Size(max = 10, message = "币种长度不能超过 10")
    private String currency;

    /** 付款条款 (可空) */
    @Size(max = 500, message = "付款条款长度不能超过 500")
    private String paymentTerms;

    /** 合同开始日期 */
    @NotNull(message = "合同开始日期不能为空")
    private LocalDate startDate;

    /** 合同结束日期 */
    @NotNull(message = "合同结束日期不能为空")
    private LocalDate endDate;

    /** 合同期限 (月, 可空) */
    @PositiveOrZero(message = "合同期限不能为负数")
    private Integer durationMonths;

    /** 是否自动续约 */
    private Boolean autoRenew;

    /** 自动续约月数 */
    @PositiveOrZero(message = "自动续约月数不能为负数")
    private Integer autoRenewMonths;

    /** 签署日期 (可空) */
    private LocalDate signedDate;

    /** 生效日期 (可空) */
    private LocalDate effectiveDate;

    /** 失效日期 (可空) */
    private LocalDate expiredDate;

/** 状态: DRAFT / PENDING_REVIEW / PENDING_SIGNATURE / SIGNED / ACTIVE / EXPIRED / TERMINATED / CANCELLED / ARCHIVED
         * */
    @Pattern(regexp = "DRAFT|PENDING_REVIEW|PENDING_SIGNATURE|SIGNED|ACTIVE|EXPIRED|TERMINATED|CANCELLED|ARCHIVED",
            message = "状态仅支持 DRAFT/PENDING_REVIEW/PENDING_SIGNATURE/SIGNED/ACTIVE/EXPIRED/TERMINATED/CANCELLED/ARCHIVED")
    private String status;

    /** 优先级 (数值越大优先级越高) */
    @PositiveOrZero(message = "优先级不能为负数")
    private Integer priority;

    /** 销售人员 ID (可空) */
    @Size(max = 100, message = "销售人员 ID 长度不能超过 100")
    private String salesPersonId;

    /** 销售人员名称 (可空) */
    @Size(max = 100, message = "销售人员名称长度不能超过 100")
    private String salesPersonName;

    /** 部门 ID (可空) */
    @Size(max = 100, message = "部门 ID 长度不能超过 100")
    private String departmentId;

    /** 部门名称 (可空) */
    @Size(max = 200, message = "部门名称长度不能超过 200")
    private String departmentName;

    /** 审批人 ID (可空) */
    @Size(max = 100, message = "审批人 ID 长度不能超过 100")
    private String approverId;

    /** 审批人名称 (可空) */
    @Size(max = 100, message = "审批人名称长度不能超过 100")
    private String approverName;

    /** 审批时间 (可空) */
    private LocalDateTime approvedAt;

    /** 审批意见 (可空) */
    @Size(max = 500, message = "审批意见长度不能超过 500")
    private String approvalComment;

    /** 签署人 ID (可空) */
    @Size(max = 100, message = "签署人 ID 长度不能超过 100")
    private String signerId;

    /** 签署人名称 (可空) */
    @Size(max = 100, message = "签署人名称长度不能超过 100")
    private String signerName;

    /** 签署方式: ELECTRONIC / PAPER / STAMP (可空) */
    @Size(max = 30, message = "签署方式长度不能超过 30")
    @Pattern(regexp = "ELECTRONIC|PAPER|STAMP|",
            message = "签署方式仅支持 ELECTRONIC/PAPER/STAMP")
    private String signatureMethod;

    /** 签署文件 URL (可空) */
    @Size(max = 500, message = "签署文件 URL 长度不能超过 500")
    private String signatureUrl;

    /** JSON 附件列表 (可空) */
    @Size(max = 1000, message = "附件长度不能超过 1000")
    private String attachments;

    /** 标签逗号分隔 (可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 关联合同 ID 逗号分隔 (可空) */
    @Size(max = 500, message = "关联合同长度不能超过 500")
    private String relatedContracts;

    /** 续约自合同 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long renewalOfId;

    /** 续约到合同 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long renewedToId;

    /** 是否启用提醒 */
    private Boolean remindersEnabled;

    /** 提前提醒天数 */
    @PositiveOrZero(message = "提前提醒天数不能为负数")
    private Integer reminderDaysBefore;

    /** 最后提醒发送时间 (可空) */
    private LocalDateTime lastReminderSentAt;

    /** JSON 合同条款详情: [{clauseName, content, isCustom}] (可空) */
    private String terms;

    /** JSON 自定义字段 (可空) */
    private String customFields;

    /** 备注 (可空) */
    @Size(max = 1000, message = "备注长度不能超过 1000")
    private String notes;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
