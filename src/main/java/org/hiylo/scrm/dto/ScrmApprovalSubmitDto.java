/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalSubmitDto.java
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
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 审批提交 DTO。
 * <p>
 * 提交审批接口入参, 创建审批实例并初始化流程。flowId 与 businessType / businessId /
 * businessTitle / applicantId 必填; businessData 为业务数据 JSON 快照;
 * urgent 标记是否加急。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmApprovalSubmitDto {

    /** 流程 ID */
    @NotNull(message = "流程 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long flowId;

    /** 业务类型: CONTRACT/EXPENSE/LEAVE/REFUND/DISCOUNT/OTHER */
    @NotBlank(message = "业务类型不能为空")
    @Pattern(regexp = "CONTRACT|EXPENSE|LEAVE|REFUND|DISCOUNT|OTHER",
            message = "业务类型仅支持 CONTRACT/EXPENSE/LEAVE/REFUND/DISCOUNT/OTHER")
    private String businessType;

    /** 业务 ID (可空) */
    @Size(max = 200, message = "业务 ID 长度不能超过 200")
    private String businessId;

    /** 业务标题 */
    @NotBlank(message = "业务标题不能为空")
    @Size(max = 500, message = "业务标题长度不能超过 500")
    private String businessTitle;

    /** 业务数据 JSON 快照 (可空) */
    private String businessData;

    /** 申请人 ID */
    @NotBlank(message = "申请人 ID 不能为空")
    @Size(max = 100, message = "申请人 ID 长度不能超过 100")
    private String applicantId;

    /** 申请人名称 (可空) */
    @Size(max = 100, message = "申请人名称长度不能超过 100")
    private String applicantName;

    /** 申请人部门 (可空) */
    @Size(max = 200, message = "申请人部门长度不能超过 200")
    private String applicantDept;

    /** 申请人角色 (可空) */
    @Size(max = 100, message = "申请人角色长度不能超过 100")
    private String applicantRole;

    /** 附件 URL (可空) */
    @Size(max = 1000, message = "附件 URL 长度不能超过 1000")
    private String attachmentUrls;

    /** 抄送人 (可空, 逗号分隔) */
    @Size(max = 500, message = "抄送人长度不能超过 500")
    private String ccUsers;

    /** 流程变量 JSON (可空) */
    private String variables;

    /** 是否加急 (默认 FALSE) */
    private Boolean urgent;

    /** 加急原因 (可空) */
    @Size(max = 500, message = "加急原因长度不能超过 500")
    private String urgentReason;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
