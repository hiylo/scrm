/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractChangeDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 合同变更 DTO。
 * <p>
 * 用于合同变更的创建/更新入参与查询返回。变更编号全局唯一, 变更类型与状态使用枚举字符串,
 * 变更原因必填。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmContractChangeDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 合同 ID */
    @NotNull(message = "合同 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 合同编号 (可空) */
    @Size(max = 100, message = "合同编号长度不能超过 100")
    private String contractNo;

    /** 变更编号 (唯一) */
    @NotBlank(message = "变更编号不能为空")
    @Size(max = 100, message = "变更编号长度不能超过 100")
    private String changeNo;

/** 变更类型: AMENDMENT / SUPPLEMENT / RENEWAL / TERMINATION / TRANSFER / PRICE_CHANGE / SCOPE_CHANGE / TERM_CHANGE /
         * OTHER */
    @NotBlank(message = "变更类型不能为空")
    @Size(max = 30, message = "变更类型长度不能超过 30")
    private String changeType;

    /** 变更原因 */
    @NotBlank(message = "变更原因不能为空")
    @Size(max = 500, message = "变更原因长度不能超过 500")
    private String changeReason;

    /** 变更描述 (可空) */
    @Size(max = 2000, message = "变更描述长度不能超过 2000")
    private String changeDescription;

    /** 变更状态: PENDING / IN_REVIEW / APPROVED / REJECTED / EXECUTED / CANCELLED */
    @Size(max = 20, message = "变更状态长度不能超过 20")
    private String changeStatus;

    /** 变更日期 (可空) */
    private LocalDate changeDate;

    /** 生效日期 (可空) */
    private LocalDate effectiveDate;

    /** 原值 JSON (可空) */
    @Size(max = 2000, message = "原值长度不能超过 2000")
    private String oldValue;

    /** 新值 JSON (可空) */
    @Size(max = 2000, message = "新值长度不能超过 2000")
    private String newValue;

    /** 影响字段 (可空) */
    @Size(max = 500, message = "影响字段长度不能超过 500")
    private String affectedFields;

    /** 金额变化 */
    private Double valueChange;

    /** 变更前金额 */
    @PositiveOrZero(message = "变更前金额不能为负数")
    private Double valueBefore;

    /** 变更后金额 */
    @PositiveOrZero(message = "变更后金额不能为负数")
    private Double valueAfter;

    /** 影响评估 (可空) */
    @Size(max = 1000, message = "影响评估长度不能超过 1000")
    private String impactAssessment;

    /** 风险评估 (可空) */
    @Size(max = 1000, message = "风险评估长度不能超过 1000")
    private String riskAssessment;

    /** 审批人 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approverId;

    /** 审批人名称 (可空) */
    @Size(max = 100, message = "审批人名称长度不能超过 100")
    private String approverName;

    /** 审批时间 (可空) */
    private LocalDateTime approvedAt;

    /** 审批意见 (可空) */
    @Size(max = 1000, message = "审批意见长度不能超过 1000")
    private String approvalNotes;

    /** 附件 JSON (可空) */
    @Size(max = 1000, message = "附件长度不能超过 1000")
    private String attachments;

    /** 新合同 ID (可空, 续约/转让时指向新合同) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long newContractId;

    /** 备注 (可空) */
    @Size(max = 1000, message = "备注长度不能超过 1000")
    private String notes;

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
