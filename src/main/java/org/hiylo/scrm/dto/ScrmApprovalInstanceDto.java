/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalInstanceDto.java
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

import java.time.LocalDateTime;

/**
 * SCRM 审批实例 DTO。
 * <p>
 * 对应 {@code ScrmApprovalInstanceEntity} 的业务字段, 主要用于查询返回与
 * 部分更新场景。提交审批使用 {@link ScrmApprovalSubmitDto}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmApprovalInstanceDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 实例编号 (全局唯一) */
    @Size(max = 100, message = "实例编号长度不能超过 100")
    private String instanceNo;

    /** 流程 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long flowId;

    /** 流程名称 (查询返回) */
    private String flowName;

    /** 流程类型 (查询返回) */
    private String flowType;

    /** 业务类型: CONTRACT/EXPENSE/LEAVE/REFUND/DISCOUNT/OTHER */
    @Pattern(regexp = "CONTRACT|EXPENSE|LEAVE|REFUND|DISCOUNT|OTHER",
            message = "业务类型仅支持 CONTRACT/EXPENSE/LEAVE/REFUND/DISCOUNT/OTHER")
    private String businessType;

    /** 业务 ID (可空) */
    @Size(max = 200, message = "业务 ID 长度不能超过 200")
    private String businessId;

    /** 业务标题 (可空) */
    @Size(max = 500, message = "业务标题长度不能超过 500")
    private String businessTitle;

    /** 业务数据 JSON 快照 (可空) */
    private String businessData;

    /** 申请人 ID */
    @Size(max = 100, message = "申请人 ID 长度不能超过 100")
    private String applicantId;

    /** 申请人名称 (可空) */
    private String applicantName;

    /** 申请人部门 (可空) */
    private String applicantDept;

    /** 申请人角色 (可空) */
    private String applicantRole;

    /** 当前节点 ID (查询返回) */
    private String currentNodeId;

    /** 当前节点名称 (查询返回) */
    private String currentNodeName;

    /** 当前节点类型 (查询返回) */
    private String currentNodeType;

    /** 当前审批人 ID 列表 (查询返回, 逗号分隔) */
    private String currentApproverIds;

    /** 状态: PENDING/APPROVING/APPROVED/REJECTED/CANCELLED/TRANSFERRED/TIMEOUT/WITHDRAWN (查询返回) */
    private String status;

    /** 优先级 */
    private Integer priority;

    /** 是否加急 */
    private Boolean isUrgent;

    /** 加急原因 (可空) */
    @Size(max = 500, message = "加急原因长度不能超过 500")
    private String urgentReason;

    /** 开始时间 (查询返回) */
    private LocalDateTime startedAt;

    /** 完成时间 (查询返回) */
    private LocalDateTime completedAt;

    /** 审批时长 (小时, 查询返回) */
    private Integer durationHours;

    /** 最终审批时间 (查询返回) */
    private LocalDateTime approvedAt;

    /** 最终审批人 (查询返回) */
    private String approvedBy;

    /** 驳回人 (查询返回) */
    private String rejectedBy;

    /** 驳回原因 (查询返回) */
    private String rejectedReason;

    /** 撤回时间 (查询返回) */
    private LocalDateTime withdrawnAt;

    /** 节点历史 JSON (查询返回) */
    private String nodeHistory;

    /** 流程变量 JSON (可空) */
    private String variables;

    /** 附件 URL (可空) */
    @Size(max = 1000, message = "附件 URL 长度不能超过 1000")
    private String attachmentUrls;

    /** 抄送人 (可空, 逗号分隔) */
    @Size(max = 500, message = "抄送人长度不能超过 500")
    private String ccUsers;

    /** 创建人 (可空) */
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
