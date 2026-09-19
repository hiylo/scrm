/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderDto.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 工单 DTO。
 * <p>
 * 用于工单创建、更新、查询返回。创建时必填标题、工单类型、客户 ID、来源; 优先级、状态、
 * 其他字段缺省由服务端补全 (NORMAL / OPEN / false)。更新时字段非空才覆盖。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmWorkOrderDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 工单编号 (创建时由服务端生成, 入参忽略) */
    private String orderNo;

    /** 标题 */
    @NotBlank(message = "工单标题不能为空")
    @Size(max = 200, message = "标题长度不能超过 200")
    private String title;

    /** 描述 (可空) */
    private String description;

    /** 工单类型: COMPLAINT/CONSULTATION/MAINTENANCE/INSTALLATION/REPAIR/SERVICE_REQUEST/TECH_SUPPORT/BILLING/RETURN/EXCHANGE/FEEDBACK/OTHER */
    @NotBlank(message = "工单类型不能为空")
    @Size(max = 50, message = "工单类型长度不能超过 50")
    private String orderType;

    /** 工单分类 (可空) */
    @Size(max = 100, message = "工单分类长度不能超过 100")
    private String orderCategory;

    /** 优先级: URGENT/HIGH/NORMAL/LOW */
    @Size(max = 20, message = "优先级长度不能超过 20")
    private String priority;

    /** 工单状态: OPEN/ASSIGNED/IN_PROGRESS/PENDING_CUSTOMER/RESOLVED/CLOSED/CANCELLED/REOPENED */
    @Size(max = 20, message = "状态长度不能超过 20")
    private String orderStatus;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 客户电话 (可空) */
    @Size(max = 50, message = "客户电话长度不能超过 50")
    private String customerPhone;

    /** 客户邮箱 (可空) */
    @Size(max = 100, message = "客户邮箱长度不能超过 100")
    private String customerEmail;

    /** 联系人 (可空) */
    @Size(max = 100, message = "联系人长度不能超过 100")
    private String contactPerson;

    /** 联系人电话 (可空) */
    @Size(max = 50, message = "联系人电话长度不能超过 50")
    private String contactPhone;

    /** 联系人邮箱 (可空) */
    @Size(max = 100, message = "联系人邮箱长度不能超过 100")
    private String contactEmail;

    /** 产品 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long productId;

    /** 产品名称 (可空) */
    @Size(max = 200, message = "产品名称长度不能超过 200")
    private String productName;

    /** 产品分类 (可空) */
    @Size(max = 100, message = "产品分类长度不能超过 100")
    private String productCategory;

    /** 序列号 (可空) */
    @Size(max = 200, message = "序列号长度不能超过 200")
    private String serialNumber;

    /** 关联合同 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 关联合同编号 (可空) */
    @Size(max = 100, message = "关联合同编号长度不能超过 100")
    private String contractNo;

    /** 关联活动 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long campaignId;

    /** 来源: PHONE/EMAIL/WEB/APP/WECHAT/WALK_IN/REFERRAL/SYSTEM/OTHER */
    @NotBlank(message = "来源不能为空")
    @Size(max = 50, message = "来源长度不能超过 50")
    private String source;

    /** 渠道 (可空) */
    @Size(max = 50, message = "渠道长度不能超过 50")
    private String channel;

    /** 处理人 (可空) */
    @Size(max = 100, message = "处理人长度不能超过 100")
    private String assignedTo;

    /** 处理人 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long assignedToId;

    /** 处理部门 (可空) */
    @Size(max = 100, message = "处理部门长度不能超过 100")
    private String assignedDepartment;

    /** 分配时间 (可空) */
    private LocalDateTime assignedAt;

    /** 接受时间 (可空) */
    private LocalDateTime acceptedAt;

    /** 开始处理时间 (可空) */
    private LocalDateTime startedAt;

    /** 解决时间 (可空) */
    private LocalDateTime resolvedAt;

    /** 关闭时间 (可空) */
    private LocalDateTime closedAt;

    /** 最后响应时间 (可空) */
    private LocalDateTime lastResponseAt;

    /** 响应时长分钟 (可空) */
    private Integer responseTimeMinutes;

    /** 解决时长分钟 (可空) */
    private Integer resolutionTimeMinutes;

    /** SLA 策略 (可空) */
    @Size(max = 100, message = "SLA 策略长度不能超过 100")
    private String slaPolicy;

    /** SLA 响应截止 (可空) */
    private LocalDateTime slaResponseDue;

    /** SLA 解决截止 (可空) */
    private LocalDateTime slaResolutionDue;

    /** SLA 响应达标 (可空) */
    private Boolean slaResponseMet;

    /** SLA 解决达标 (可空) */
    private Boolean slaResolutionMet;

    /** SLA 违规 */
    private Boolean slaBreached;

    /** 满意度评分 1-5 (可空) */
    private Integer satisfactionScore;

    /** 满意度评价 (可空) */
    @Size(max = 500, message = "满意度评价长度不能超过 500")
    private String satisfactionComment;

    /** 解决方案 (可空) */
    @Size(max = 2000, message = "解决方案长度不能超过 2000")
    private String resolution;

    /** 解决编码 (可空) */
    @Size(max = 50, message = "解决编码长度不能超过 50")
    private String resolutionCode;

    /** 根本原因 (可空) */
    @Size(max = 500, message = "根本原因长度不能超过 500")
    private String rootCause;

    /** 重复工单 */
    private Boolean isRepeated;

    /** 关联工单 (可空, 逗号分隔) */
    @Size(max = 500, message = "关联工单长度不能超过 500")
    private String relatedOrderIds;

    /** 是否升级 */
    private Boolean escalated;

    /** 升级到 (可空) */
    @Size(max = 100, message = "升级到长度不能超过 100")
    private String escalatedTo;

    /** 升级时间 (可空) */
    private LocalDateTime escalatedAt;

    /** 升级原因 (可空) */
    @Size(max = 500, message = "升级原因长度不能超过 500")
    private String escalationReason;

    /** 标签 (可空, 逗号分隔) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 附件 (可空, JSON 数组) */
    @Size(max = 1000, message = "附件长度不能超过 1000")
    private String attachments;

    /** 内部备注 (可空) */
    @Size(max = 1000, message = "内部备注长度不能超过 1000")
    private String internalNotes;

    /** 期望解决日期 (可空) */
    private LocalDate expectedResolutionDate;

    /** 实际解决日期 (可空) */
    private LocalDate actualResolutionDate;

    /** 解决截止日期 (可空) */
    private LocalDate resolutionDeadline;

    /** 是否紧急 */
    private Boolean isUrgent;

    /** 是否 VIP 客户 */
    private Boolean isVipCustomer;

    /** 是否需要跟进 */
    private Boolean followUpRequired;

    /** 跟进日期 (可空) */
    private LocalDate followUpDate;

    /** 跟进人 (可空) */
    @Size(max = 100, message = "跟进人长度不能超过 100")
    private String followUpBy;

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
