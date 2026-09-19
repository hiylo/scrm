/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderLogDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 工单日志 DTO。
 * <p>
 * 用于工单日志创建与查询返回。创建时必填工单 ID 与日志类型; 其他字段可选。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmWorkOrderLogDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 关联工单 ID */
    @NotNull(message = "工单 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 工单编号 (冗余) */
    @Size(max = 100, message = "工单编号长度不能超过 100")
    private String orderNo;

    /* 日志类型: /*
    /* STATUS_CHANGE/ASSIGNMENT/COMMENT/ESCALATION/RESOLUTION/SLA_BREACH/NOTE/ATTACHMENT/CUSTOMER_RESPONSE/INTERNAL */
    /* */ @NotBlank(message = "日志类型不能为空")    @Size(max = 30, message = "日志类型长度不能超过 30")
    private String logType;

    /** 日志标题 (可空) */
    @Size(max = 200, message = "日志标题长度不能超过 200")
    private String logTitle;

    /** 日志内容 (可空) */
    private String logContent;

    /** 变更前状态 (可空) */
    @Size(max = 20, message = "变更前状态长度不能超过 20")
    private String fromStatus;

    /** 变更后状态 (可空) */
    @Size(max = 20, message = "变更后状态长度不能超过 20")
    private String toStatus;

    /** 变更前处理人 (可空) */
    @Size(max = 100, message = "变更前处理人长度不能超过 100")
    private String fromAssignee;

    /** 变更后处理人 (可空) */
    @Size(max = 100, message = "变更后处理人长度不能超过 100")
    private String toAssignee;

    /** 变更前部门 (可空) */
    @Size(max = 100, message = "变更前部门长度不能超过 100")
    private String fromDepartment;

    /** 变更后部门 (可空) */
    @Size(max = 100, message = "变更后部门长度不能超过 100")
    private String toDepartment;

    /** 字段变更 JSON (可空) */
    @Size(max = 2000, message = "字段变更长度不能超过 2000")
    private String fieldChanges;

    /** 内部日志 */
    private Boolean isInternal;

    /** 客户可见 */
    private Boolean isCustomerVisible;

    /** 操作人 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    /** 操作人名称 (可空) */
    @Size(max = 100, message = "操作人名称长度不能超过 100")
    private String operatorName;

    /** 操作者类型: AGENT/CUSTOMER/SYSTEM/MANAGER */
    @Size(max = 30, message = "操作者类型长度不能超过 30")
    private String operatorType;

    /** 附件 (可空, JSON 数组) */
    @Size(max = 1000, message = "附件长度不能超过 1000")
    private String attachments;

    /** 提醒用户 (可空, 逗号分隔) */
    @Size(max = 500, message = "提醒用户长度不能超过 500")
    private String mentionedUsers;

    /** 提醒部门 (可空, 逗号分隔) */
    @Size(max = 500, message = "提醒部门长度不能超过 500")
    private String mentionedDepartments;

    /** 花费时间分钟 */
    private Integer timeSpentMinutes;

    /** 计费时间 */
    private Integer billableTime;

    /** IP 地址 (可空) */
    @Size(max = 50, message = "IP 地址长度不能超过 50")
    private String ipAddress;

    /** User-Agent (可空) */
    @Size(max = 500, message = "User-Agent 长度不能超过 500")
    private String userAgent;

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
