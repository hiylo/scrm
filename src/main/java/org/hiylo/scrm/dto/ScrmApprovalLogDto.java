/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalLogDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 审批操作日志 DTO。
 * <p>
 * 对应 {@code ScrmApprovalLogEntity} 的业务字段, 主要用于查询返回。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmApprovalLogDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 审批实例 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long instanceId;

    /** 节点 ID */
    @Size(max = 100, message = "节点 ID 长度不能超过 100")
    private String nodeId;

    /** 节点名称 (可空) */
    private String nodeName;

    /** 节点类型 (可空): START/APPROVE/CC/CONDITION/END */
    private String nodeType;

    /** 操作类型: SUBMIT/APPROVE/REJECT/TRANSFER/COUNTERSIGN/CC/WITHDRAW/RESUBMIT/TIMEOUT/URGE/COMMENT/AUTO_APPROVE */
    @NotBlank(message = "操作类型不能为空")
    @Pattern(regexp =
            "SUBMIT|APPROVE|REJECT|TRANSFER|COUNTERSIGN|CC|WITHDRAW|RESUBMIT|TIMEOUT|URGE|COMMENT|AUTO_APPROVE",
            message = "操作类型仅支持 SUBMIT/APPROVE/REJECT/TRANSFER/COUNTERSIGN/CC/WITHDRAW/RESUBMIT/TIMEOUT/URGE/COMMENT/AUTO_APPROVE")
    private String actionType;

    /** 操作人 ID */
    @NotBlank(message = "操作人 ID 不能为空")
    @Size(max = 100, message = "操作人 ID 长度不能超过 100")
    private String operatorId;

    /** 操作人名称 (可空) */
    private String operatorName;

    /** 操作人角色 (可空) */
    private String operatorRole;

    /** 操作人类型: APPLICANT/APPROVER/CC/SYSTEM */
    @Pattern(regexp = "APPLICANT|APPROVER|CC|SYSTEM",
            message = "操作人类型仅支持 APPLICANT/APPROVER/CC/SYSTEM")
    private String operatorType;

    /** 审批意见 (可空) */
    @Size(max = 2000, message = "审批意见长度不能超过 2000")
    private String comment;

    /** 操作数据 JSON: {transferredTo,countersignUsers,ccUsers} (可空) */
    private String actionData;

    /** 上一节点 ID (可空) */
    private String previousNodeId;

    /** 下一节点 ID (可空) */
    private String nextNodeId;

    /** 附件 (可空, 逗号分隔 URL) */
    private String attachments;

    /** 操作时间 */
    private LocalDateTime actedAt;

    /** 是否自动操作 */
    private Boolean isAutoAction;

    /** 操作顺序 */
    private Integer sequence;
}
