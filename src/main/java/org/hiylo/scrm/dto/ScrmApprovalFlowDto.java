/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalFlowDto.java
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
 * SCRM 审批流程定义 DTO。
 * <p>
 * 对应 {@code ScrmApprovalFlowEntity} 的业务字段, 创建/更新接口入参。
 * flowType / status 以枚举字符串校验合法性; versionNumber / allowDelegation /
 * allowCountersign / allowUrgent / maxDurationDays 缺省时由服务端填充默认值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmApprovalFlowDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 流程名称 */
    @NotBlank(message = "流程名称不能为空")
    @Size(max = 200, message = "流程名称长度不能超过 200")
    private String flowName;

    /** 流程编码 (全局唯一) */
    @NotBlank(message = "流程编码不能为空")
    @Size(max = 50, message = "流程编码长度不能超过 50")
    private String flowCode;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 流程类型: CONTRACT/EXPENSE/LEAVE/REFUND/DISCOUNT/PRICE_CHANGE/CUSTOMER_MERGE/CONTENT/PURCHASE/OTHER/CUSTOM */
    @NotBlank(message = "流程类型不能为空")
    @Pattern(
            regexp = "CONTRACT|EXPENSE|LEAVE|REFUND|DISCOUNT|PRICE_CHANGE|CUSTOMER_MERGE|CONTENT|PURCHASE|OTHER|CUSTOM",
            message = "流程类型仅支持 CONTRACT/EXPENSE/LEAVE/REFUND/DISCOUNT/PRICE_CHANGE/CUSTOMER_MERGE/CONTENT/PURCHASE/OTHER/CUSTOM")
    private String flowType;

    /** 适用模块 (可空) */
    @Size(max = 100, message = "适用模块长度不能超过 100")
    private String applicableModule;

/** 节点定义 JSON:
         * [{nodeId,nodeName,nodeType,approverType,approverIds,ccUserIds,condition,actions,autoApprove
             ,timeoutHours,order}]
         * */
    @NotBlank(message = "节点定义不能为空")
    private String nodes;

    /** 条件路由规则 JSON: [{nodeId,conditions,routes:[{toNode,condition}]}] (可空) */
    private String conditionRules;

    /** 起始节点 ID (可空) */
    @Size(max = 100, message = "起始节点 ID 长度不能超过 100")
    private String startNode;

    /** 结束节点 ID 列表 (可空, 逗号分隔) */
    @Size(max = 500, message = "结束节点列表长度不能超过 500")
    private String endNodes;

    /** 流程版本号 (默认 1) */
    private Integer versionNumber;

    /** 状态: ACTIVE/INACTIVE/DRAFT (查询返回) */
    @Pattern(regexp = "ACTIVE|INACTIVE|DRAFT",
            message = "状态仅支持 ACTIVE/INACTIVE/DRAFT")
    private String status;

    /** 是否为默认流程 (默认 FALSE) */
    private Boolean isDefault;

    /** 使用次数 (查询返回) */
    private Integer usageCount;

    /** 最近使用时间 (查询返回) */
    private LocalDateTime lastUsedAt;

    /** 审批人缺失时的备选 (可空, 逗号分隔用户 ID) */
    @Size(max = 200, message = "审批人备选长度不能超过 200")
    private String approverFallback;

    /** 允许转交 (默认 TRUE) */
    private Boolean allowDelegation;

    /** 允许加签 (默认 FALSE) */
    private Boolean allowCountersign;

    /** 允许加急 (默认 TRUE) */
    private Boolean allowUrgent;

    /** 最大审批时长 (天, 默认 30) */
    private Integer maxDurationDays;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
